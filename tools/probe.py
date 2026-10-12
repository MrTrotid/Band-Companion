#!/usr/bin/env python3
"""Hardware Verification & Protocol Probe for Huawei Band 10.

Connects to the Huawei Band 10 via BLE, executes LPv2 negotiation and
HiChain3 mutual authentication, and queries device status & battery level.
Saves session keys in `band.ini` for instant reconnects.
"""

import asyncio
import configparser
import json
import logging
import os
import sys
import time
from pathlib import Path
from typing import Dict, Optional, Tuple

# Ensure project root is in sys.path regardless of execution directory
_project_root = Path(__file__).resolve().parent.parent
if str(_project_root) not in sys.path:
    sys.path.insert(0, str(_project_root))

from bleak import BleakClient, BleakScanner
from bleak.backends.characteristic import BleakGATTCharacteristic

from tools.crypto import (
    DIGEST_SECRETS,
    GROUP_ID,
    aes_gcm_decrypt,
    aes_gcm_encrypt,
    compute_hmac,
    decrypt_pin,
    derive_psk_first_auth,
    derive_psk_reconnect,
    derive_session_key,
)
from tools.lpv2 import make_frame, parse_frame, tlv_encode

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("BandProbe")

# Huawei GATT UUIDs
WRITE_UUID = "0000fe01-0000-1000-8000-00805f9b34fb"
NOTIFY_UUID = "0000fe02-0000-1000-8000-00805f9b34fb"
CONFIG_FILE = "band.ini"


class HuaweiBandClient:
    def __init__(self, address: str):
        self.address = address
        self.client: Optional[BleakClient] = None
        self._rx_queue: asyncio.Queue = asyncio.Queue()
        self._rx_buffer = bytearray()

        # Session attributes
        self.android_id = os.urandom(16).hex().upper()  # 32-char uppercase hex
        self.client_mac = "FF:FF:FF:A9:F0:23"
        self.auth_version = 1
        self.secret_key: Optional[bytes] = None
        self.load_config()

    def load_config(self):
        config = configparser.ConfigParser()
        if os.path.exists(CONFIG_FILE):
            config.read(CONFIG_FILE)
            if "band10" in config:
                sec = config["band10"].get("secret_key", "").strip()
                if sec:
                    self.secret_key = bytes.fromhex(sec)
                    logger.info("Loaded stored secret_key from %s (reconnect enabled)", CONFIG_FILE)
                aid = config["band10"].get("android_id", "").strip()
                if aid:
                    self.android_id = aid

    def save_config(self):
        config = configparser.ConfigParser()
        config["band10"] = {
            "device_mac": self.address,
            "client_mac": self.client_mac,
            "android_id": self.android_id,
            "secret_key": self.secret_key.hex() if self.secret_key else "",
        }
        with open(CONFIG_FILE, "w") as f:
            config.write(f)
        logger.info("Saved credentials to %s", CONFIG_FILE)

    def _on_notify(self, characteristic: BleakGATTCharacteristic, data: bytearray):
        self._rx_buffer.extend(data)
        while len(self._rx_buffer) >= 5:
            if self._rx_buffer[0] != 0x5A:
                # Seek to next magic byte
                idx = self._rx_buffer.find(b"\x5a")
                if idx == -1:
                    self._rx_buffer.clear()
                    break
                self._rx_buffer = self._rx_buffer[idx:]

            if len(self._rx_buffer) < 3:
                break
            length = int.from_bytes(self._rx_buffer[1:3], byteorder="big")
            total_frame_len = length + 5  # header + len + body + crc

            if len(self._rx_buffer) < total_frame_len:
                break

            complete_frame = bytes(self._rx_buffer[:total_frame_len])
            self._rx_buffer = self._rx_buffer[total_frame_len:]

            try:
                svc, cmd, tlvs = parse_frame(complete_frame)
                self._rx_queue.put_nowait((svc, cmd, tlvs))
            except Exception as e:
                logger.debug("Failed parsing frame: %s", e)

    async def connect(self, timeout: float = 15.0):
        logger.info("Scanning for Band 10 at %s (timeout: %.0fs)...", self.address, timeout)
        device = await BleakScanner.find_device_by_address(self.address, timeout=timeout)

        if not device:
            logger.error("Device %s was not detected in BLE scan.", self.address)
            print("\n" + "!" * 70)
            print("⚠️  BAND NOT DETECTED IN BLUETOOTH SCAN")
            print("!" * 70)
            print("1. Is the Band currently connected to your phone's Huawei Health app?")
            print("   -> BLE devices cannot connect to two hosts simultaneously.")
            print("   -> Temporarily turn OFF Bluetooth on your phone while testing here.")
            print("2. Is the Band's screen awake?")
            print("   -> Tap the Band screen so it lights up and starts advertising.")
            print("3. Is the MAC address correct?")
            print("   -> Check Settings -> About on the Band, or run `python tools/scan.py`.")
            print("!" * 70 + "\n")
            raise ConnectionError(f"Band {self.address} not advertising or unreachable.")

        logger.info("Found %s (RSSI: %s dBm). Connecting...", device.name or "Band", getattr(device, "rssi", "N/A"))
        self.client = BleakClient(device)
        await self.client.connect()
        logger.info("Connected! Starting notifications on %s", NOTIFY_UUID)
        await self.client.start_notify(NOTIFY_UUID, self._on_notify)

    async def send(self, svc: int, cmd: int, payload: bytes = b""):
        frame = make_frame(svc, cmd, payload)
        await self.client.write_gatt_char(WRITE_UUID, frame, response=False)

    async def recv(self, exp_svc: int, exp_cmd: int, timeout: float = 15.0) -> Dict[int, bytes]:
        deadline = asyncio.get_running_loop().time() + timeout
        while True:
            remaining = deadline - asyncio.get_running_loop().time()
            if remaining <= 0:
                raise asyncio.TimeoutError(f"Timeout waiting for svc={exp_svc:#x} cmd={exp_cmd:#x}")
            svc, cmd, tlvs = await asyncio.wait_for(self._rx_queue.get(), timeout=remaining)
            if (svc, cmd) == (exp_svc, exp_cmd):
                return tlvs
            logger.debug("Skipping unsolicited notification svc=%#x cmd=%#x", svc, cmd)

    async def step1_link_params(self):
        logger.info("-> Step 1: Link Parameters (0x01/0x01)...")
        payload = tlv_encode(1) + tlv_encode(2) + tlv_encode(3) + tlv_encode(4)
        await self.send(0x01, 0x01, payload)
        tlvs = await self.recv(0x01, 0x01)

        server_nonce_blob = tlvs.get(0x05, b"")
        if len(server_nonce_blob) > 2:
            self.auth_version = server_nonce_blob[1]
        logger.info("<- Link Params OK. Auth Version: %d, ServerNonce length: %d", self.auth_version, len(server_nonce_blob))

    async def step2_security_negotiation(self):
        logger.info("-> Step 2: Security Negotiation (0x01/0x33)...")
        payload = (
            tlv_encode(0x01, 4)  # authMode = 4 (Band 10)
            + tlv_encode(0x02, 1)
            + tlv_encode(0x03, 1)
            + tlv_encode(0x04, 0)
            + tlv_encode(0x05, self.android_id.encode("ascii"))
            + tlv_encode(0x06, b"")
            + tlv_encode(0x07, b"BandCompanion")
        )
        await self.send(0x01, 0x33, payload)
        tlvs = await self.recv(0x01, 0x33)

        tag1 = tlvs.get(0x01, b"\x00")[0]
        tag2 = tlvs.get(0x02, b"\x00")[0] if 0x02 in tlvs else 0
        pw = 4 if tag1 == 4 else 0
        auth_type = tag2 ^ pw
        logger.info("<- Security Negotiation OK. AuthType: %#x", auth_type)
        return auth_type

    async def step3_pin_retrieval(self) -> str:
        logger.info("-> Step 3: Requesting Pairing PIN (0x01/0x2C)...")
        await self.send(0x01, 0x2C, tlv_encode(0x01, b""))
        logger.info("Waiting for band response (PLEASE CONFIRM ON BAND SCREEN)...")
        tlvs = await self.recv(0x01, 0x2C, timeout=30.0)

        enc_pin = tlvs.get(0x01, b"")
        iv = tlvs.get(0x02, b"")
        pin = decrypt_pin(enc_pin, iv, self.auth_version)
        logger.info("<- Decrypted PIN: [%s]. Confirm on the Band!", pin)
        return pin

    async def step4_hichain3_auth(self, pin: Optional[str] = None):
        seed = os.urandom(32)
        rand_self = os.urandom(16)
        peer_auth_id = self.android_id.encode("utf-8").hex().upper()
        req_id = str(int(time.time() * 1000))

        if self.secret_key:
            logger.info("-> Step 4: Reconnect Auth (operationCode=0x02)...")
            op_code = 2
            psk = derive_psk_reconnect(self.secret_key, seed)
        else:
            logger.info("-> Step 4: First-Auth Mutual Handshake (operationCode=0x01)...")
            op_code = 1
            psk = derive_psk_first_auth(pin or "000000", seed)

        # Round 1
        msg1 = {
            "isoSalt": rand_self.hex().upper(),
            "peerAuthId": peer_auth_id,
            "operationCode": op_code,
            "seed": seed.hex().upper(),
            "peerUserType": 0,
            "version": {"minVersion": "1.0.0", "currentVersion": "2.0.16"},
            "authForm": 0,
            "message": 1,
            "requestId": req_id,
            "groupId": GROUP_ID,
            "groupName": "health_group_name",
            "groupOp": 2,
            "groupType": 256,
            "peerDeviceId": self.android_id,
            "connDeviceId": self.android_id,
            "appId": "com.huawei.health",
            "ownerName": "",
            "groupAndModuleVersion": "2.0.1",
        }
        p1 = tlv_encode(0x01, json.dumps(msg1)) + tlv_encode(0x02, op_code) + tlv_encode(0x03, req_id)
        await self.send(0x01, 0x28, p1)
        resp1 = await self.recv(0x01, 0x28)
        data1 = json.loads(resp1[0x01].decode("utf-8").rstrip("\x00"))

        rand_peer = bytes.fromhex(data1["isoSalt"])
        band_id = bytes.fromhex(data1["peerAuthId"])
        session_key = derive_session_key(psk, rand_self, rand_peer)

        # Round 2
        tok2 = compute_hmac(psk, rand_self + rand_peer + band_id + self.android_id.encode("utf-8"))
        msg2 = {"peerAuthId": peer_auth_id, "token": tok2}
        p2 = tlv_encode(0x01, json.dumps(msg2)) + tlv_encode(0x02, op_code) + tlv_encode(0x03, req_id)
        await self.send(0x01, 0x28, p2)
        await self.recv(0x01, 0x28)

        # Round 3 (first-auth only)
        if op_code == 1:
            challenge = os.urandom(16)
            nonce3 = os.urandom(12)
            enc3 = aes_gcm_encrypt(challenge, session_key, nonce3, b"hichain_iso_exchange")
            msg3 = {"nonce": nonce3.hex().upper(), "encData": enc3.hex().upper()}
            p3 = tlv_encode(0x01, json.dumps(msg3)) + tlv_encode(0x02, op_code) + tlv_encode(0x03, req_id)
            await self.send(0x01, 0x28, p3)
            resp3 = await self.recv(0x01, 0x28)
            data3 = json.loads(resp3[0x01].decode("utf-8").rstrip("\x00"))

            nonce_peer = bytes.fromhex(data3["nonce"])
            enc_auth_tok = bytes.fromhex(data3["encAuthToken"])
            self.secret_key = aes_gcm_decrypt(enc_auth_tok, session_key, nonce_peer, challenge)
            self.save_config()

        # Round 4
        nonce4 = os.urandom(12)
        enc4 = aes_gcm_encrypt(b"\x00\x00\x00\x00", session_key, nonce4, b"hichain_iso_result")
        msg4 = {"nonce": nonce4.hex().upper(), "encResult": enc4.hex().upper(), "operationCode": op_code}
        p4 = tlv_encode(0x01, json.dumps(msg4)) + tlv_encode(0x02, op_code) + tlv_encode(0x03, req_id)
        await self.send(0x01, 0x28, p4)
        await self.recv(0x01, 0x28)
        logger.info("<- HiChain3 Authentication Succeeded!")

    async def query_device_info(self):
        logger.info("Querying battery level (0x01/0x0E)...")
        await self.send(0x01, 0x0E, b"")
        tlvs = await self.recv(0x01, 0x0E)
        battery = tlvs.get(0x01, b"\x00")[0] if 0x01 in tlvs else "Unknown"
        logger.info("🔋 Battery Level: %s%%", battery)


async def main():
    if len(sys.argv) < 2:
        print("Usage: python tools/probe.py <BAND_MAC_ADDRESS>")
        print("Example: python tools/probe.py 88:8E:68:A2:E4:4F")
        print("\nTip: Run `python tools/scan.py` first to find your Band's address.")
        sys.exit(1)

    address = sys.argv[1].strip()
    client = HuaweiBandClient(address)

    try:
        await client.connect()
        await client.step1_link_params()
        await client.step2_security_negotiation()

        if client.secret_key:
            await client.step4_hichain3_auth()
        else:
            pin = await client.step3_pin_retrieval()
            await client.step4_hichain3_auth(pin)

        await client.query_device_info()
        print("\n" + "=" * 60)
        print("🎉 SUCCESS! Band 10 verified and authenticated.")
        print(f"Credentials saved to {CONFIG_FILE}. Subsequent runs will reconnect instantly.")
        print("=" * 60)
    except Exception as e:
        err_msg = str(e)
        logger.error("Probe failed: %s", e)
        if "UNLIKELY_ERROR" in err_msg or "14" in err_msg or "AuthenticationFailed" in err_msg or "NotConnected" in err_msg:
            print("\n" + "=" * 70)
            print("🔒 BAND REJECTED SUBSCRIPTION (BOND CONFLICT DETECTED)")
            print("=" * 70)
            print("Root Cause:")
            print("  The Huawei Band 10's security engine blocks new Bluetooth hosts")
            print("  from subscribing to notifications while it is still bonded to another device")
            print("  (such as the official Huawei Health app on your phone).")
            print("\nHow to Resolve:")
            print("  1. On your Huawei Band 10, open Settings -> System & updates (or System).")
            print("  2. Tap 'Disconnect' (this puts the band in pairing mode WITHOUT deleting data).")
            print("     [Or tap 'Reset' if you want a fresh factory reset].")
            print("  3. The band will show a pairing / waiting screen.")
            print("  4. Re-run this probe:")
            print(f"       .venv/bin/python3 tools/probe.py {address}")
            print("=" * 70 + "\n")
        else:
            logger.error("Traceback:", exc_info=True)
    finally:
        if client.client and client.client.is_connected:
            await client.client.disconnect()
            logger.info("Disconnected cleanly.")


if __name__ == "__main__":
    asyncio.run(main())
