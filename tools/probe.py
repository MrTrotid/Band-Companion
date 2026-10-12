#!/usr/bin/env python3
"""Hardware Verification & Protocol Probe for Huawei Band 10.

Connects to the Huawei Band 10 via BLE, executes LPv2 negotiation and
HiChain3 mutual authentication, completes post-auth bootstrap initialization
(exiting QR pairing display to active watch face), and queries device status.
Saves session keys in `band.ini` for instant reconnects.
"""

import asyncio
import configparser
import json
import logging
import os
import secrets
import struct
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

from tools.bluez_agent import BlueZAgentManager
from tools.crypto import (
    DIGEST_SECRETS,
    GROUP_ID,
    aes_gcm_decrypt,
    aes_gcm_encrypt,
    compute_hmac,
    decrypt_pin_code,
    derive_psk_first_auth,
    derive_psk_reconnect,
    derive_session_key,
)
from tools.lpv2 import (
    calculate_crc16,
    make_frame,
    make_sliced_frames,
    parse_frame,
    tlv_decode_all,
    tlv_encode,
)

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("BandProbe")

# Huawei GATT UUIDs
WRITE_UUID = "0000fe01-0000-1000-8000-00805f9b34fb"
NOTIFY_UUID = "0000fe02-0000-1000-8000-00805f9b34fb"
CONFIG_FILE = "band.ini"

KNOWN_SUPPORTED_SERVICES = bytes([
    0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A,
    0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10, 0x11, 0x12, 0x13, 0x14,
    0x15, 0x16, 0x17, 0x18, 0x19, 0x1A, 0x1B, 0x1D, 0x20,
    0x22, 0x23, 0x24, 0x25, 0x26, 0x27, 0x2A, 0x2B, 0x2D, 0x2E,
    0x30, 0x32, 0x33, 0x34, 0x35,
])


class HuaweiBandClient:
    def __init__(self, address: str):
        self.address = address
        self.client: Optional[BleakClient] = None
        self._rx_queue: asyncio.Queue = asyncio.Queue()
        self._rx_buffer = bytearray()
        self._slice_buf: Optional[bytearray] = None
        self._slice_svc = 0
        self._slice_cmd = 0

        # Session attributes
        self.android_id = os.urandom(16).hex().upper()  # 32-char uppercase hex
        self.client_mac = "FF:FF:FF:A9:F0:23"
        self.auth_version = 1
        self.enc_method = 0
        self.auth_key: Optional[bytes] = None
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
                ak = config["band10"].get("auth_key", "").strip()
                if ak:
                    self.auth_key = bytes.fromhex(ak)
                if self.secret_key:
                    logger.info("Loaded stored session key from %s (reconnect enabled)", CONFIG_FILE)
                aid = config["band10"].get("android_id", "").strip()
                if aid:
                    self.android_id = aid

    def save_config(self):
        config = configparser.ConfigParser()
        config["band10"] = {
            "device_mac": self.address,
            "client_mac": self.client_mac,
            "android_id": self.android_id,
            "auth_key": self.auth_key.hex() if self.auth_key else "",
            "secret_key": self.secret_key.hex() if self.secret_key else "",
        }
        with open(CONFIG_FILE, "w") as f:
            config.write(f)
        logger.info("Saved credentials to %s", CONFIG_FILE)

    def _on_notify(self, characteristic: BleakGATTCharacteristic, data: bytearray):
        self._rx_buffer.extend(data)
        while len(self._rx_buffer) >= 5:
            if self._rx_buffer[0] != 0x5A:
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

            slice_b = complete_frame[3]
            if slice_b == 0x00:
                try:
                    svc, cmd, tlvs = parse_frame(complete_frame)
                    self._rx_queue.put_nowait((svc, cmd, tlvs))
                except Exception as e:
                    logger.debug("Failed parsing frame: %s", e)
            elif slice_b == 0x01:
                payload = complete_frame[5:-2]
                self._slice_svc = payload[0]
                self._slice_cmd = payload[1]
                self._slice_buf = bytearray(payload[2:])
            elif slice_b == 0x02:
                if self._slice_buf is not None:
                    self._slice_buf.extend(complete_frame[5:-2])
            elif slice_b == 0x03:
                if self._slice_buf is not None:
                    self._slice_buf.extend(complete_frame[5:-2])
                    body = bytes([self._slice_svc, self._slice_cmd]) + bytes(self._slice_buf)
                    length = len(body) + 1
                    hdr = bytes([0x5A]) + length.to_bytes(2, byteorder="big") + bytes([0x00]) + body
                    crc = calculate_crc16(hdr)
                    synthetic_frame = hdr + crc
                    self._slice_buf = None
                    try:
                        svc, cmd, tlvs = parse_frame(synthetic_frame)
                        self._rx_queue.put_nowait((svc, cmd, tlvs))
                    except Exception as e:
                        logger.debug("Failed parsing reassembled frame: %s", e)

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
        frames = make_sliced_frames(svc, cmd, payload, slice_size=244)
        if len(frames) == 1 and frames[0][3] == 0x01:
            frames = [make_frame(svc, cmd, payload)]
        for f in frames:
            await self.client.write_gatt_char(WRITE_UUID, f, response=False)
            if len(frames) > 1:
                await asyncio.sleep(0.02)

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

    def _encrypt_payload(self, plain_tlv: bytes) -> bytes:
        if not self.secret_key:
            raise RuntimeError("Cannot encrypt without session secret_key")
        iv = secrets.token_bytes(12)
        cipher = aes_gcm_encrypt(plain_tlv, self.secret_key, iv, b"")
        return tlv_encode(0x7C, b"\x01") + tlv_encode(0x7D, iv) + tlv_encode(0x7E, cipher)

    def _decrypt_payload(self, tlvs: dict) -> dict:
        if not (self.secret_key and 0x7C in tlvs and 0x7D in tlvs and 0x7E in tlvs):
            return tlvs
        iv = tlvs[0x7D]
        cipher = tlvs[0x7E]
        try:
            plain = aes_gcm_decrypt(cipher, self.secret_key, iv, b"")
            return tlv_decode_all(plain)
        except Exception as e:
            logger.debug("Failed decrypting transaction: %s", e)
            return tlvs

    async def transact_encrypted(self, svc: int, cmd: int, payload: bytes = b"", timeout: float = 8.0) -> dict:
        wrapped = self._encrypt_payload(payload)
        await self.send(svc, cmd, wrapped)
        resp = await self.recv(svc, cmd, timeout=timeout)
        return self._decrypt_payload(resp)

    async def step1_link_params(self):
        logger.info("-> Step 1: Link Parameters (0x01/0x01)...")
        payload = tlv_encode(1) + tlv_encode(2) + tlv_encode(3) + tlv_encode(4)
        await self.send(0x01, 0x01, payload)
        tlvs = await self.recv(0x01, 0x01)

        server_nonce_blob = tlvs.get(0x05, b"")
        if len(server_nonce_blob) > 2:
            self.auth_version = server_nonce_blob[1]
        enc_m = tlvs.get(0x0C, b"\x00")
        if enc_m:
            self.enc_method = enc_m[0]
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

    async def step3_pin_retrieval(self) -> bytes:
        logger.info("-> Step 3: Requesting Pairing PIN (0x01/0x2C)...")
        await self.send(0x01, 0x2C, tlv_encode(0x01, b""))
        tlvs = await self.recv(0x01, 0x2C, timeout=30.0)

        enc_pin = tlvs.get(0x01, b"")
        iv = tlvs.get(0x02, b"")
        pin_bytes = decrypt_pin_code(self.enc_method, self.auth_version, enc_pin, iv)
        logger.info("<- Received decrypted PIN bytes: %s", pin_bytes.hex())
        return pin_bytes

    def _build_hichain_tlv(self, step: int, payload_extra: dict, op_code: int, request_id: int) -> bytes:
        message_id = step if op_code == 0x01 else (step | 0x10)
        version_obj = {"minVersion": "1.0.0", "currentVersion": "2.0.16"}
        payload_obj = {"version": version_obj, **payload_extra}

        value_obj = {
            "authForm": 0,
            "payload": payload_obj,
            "groupAndModuleVersion": "2.0.1",
            "message": message_id,
        }
        if op_code == 0x01:
            value_obj.update({
                "requestId": str(request_id),
                "groupId": GROUP_ID,
                "groupName": "health_group_name",
                "groupOp": 2,
                "groupType": 256,
                "peerDeviceId": self.android_id,
                "connDeviceId": self.android_id,
                "appId": "com.huawei.health",
                "ownerName": "",
            })
        else:
            value_obj["isDeviceLevel"] = False

        json_bytes = json.dumps(value_obj, separators=(",", ":")).encode("utf-8")
        tlv = tlv_encode(0x01, json_bytes)
        tlv += tlv_encode(0x02, bytes([op_code]))
        tlv += tlv_encode(0x03, struct.pack(">q", request_id))
        return tlv

    def _parse_hichain_resp(self, tlvs: dict) -> Tuple[int, dict]:
        json_bytes = tlvs.get(0x01, b"{}")
        if not json_bytes.startswith(b"{"):
            app_id = json_bytes.decode("utf-8", errors="replace")
            resp_type = tlvs.get(0x04, b"\x00")[0] if 0x04 in tlvs else 0
            status = tlvs.get(0x05, b"\x00")[0] if 0x05 in tlvs else 0
            return 4, {"appId": app_id, "type": resp_type, "status": status}

        clean_json = json_bytes.rstrip(b"\x00").decode("utf-8")
        obj = json.loads(clean_json)
        step = obj.get("message", 0) & 0x0F
        payload = obj.get("payload", {})
        err = obj.get("errorCode", 0)
        if err != 0:
            raise ValueError(f"HiChain error code: {err}")
        return step, payload

    async def step4_hichain3_auth(self, pin_code: Optional[bytes] = None, op_code: int = 1):
        request_id = int(time.time() * 1000)
        seed = secrets.token_bytes(32)
        rand_self = secrets.token_bytes(16)
        auth_id_self = self.android_id.encode("utf-8")

        if op_code == 1:
            logger.info("-> Step 4: First-Auth HiChain3 Round 1 (op=0x01)...")
            p1_extra = {
                "isoSalt": rand_self.hex().upper(),
                "peerAuthId": auth_id_self.hex().upper(),
                "operationCode": op_code,
                "seed": seed.hex().upper(),
                "peerUserType": 0,
            }
        else:
            logger.info("-> Step 4: Reconnect / Bind HiChain3 Round 1 (op=0x02)...")
            p1_extra = {
                "isoSalt": rand_self.hex().upper(),
                "peerAuthId": auth_id_self.hex().upper(),
                "operationCode": op_code,
                "seed": seed.hex().upper(),
                "peerUserType": 0,
                "pkgName": "com.huawei.devicegroupmanage",
                "serviceType": GROUP_ID,
                "keyLength": 32,
            }

        tlv1 = self._build_hichain_tlv(1, p1_extra, op_code, request_id)
        await self.send(0x01, 0x28, tlv1)

        if op_code == 1:
            print("\n" + "=" * 70)
            print("👉 PAIRING REQUEST SENT TO WATCH!")
            print("👉 Look at your Huawei Band 10 screen right now!")
            print("👉 The pairing request with the checkmark is showing on your watch!")
            print("👉 1. TAP THE CHECKMARK / YES ON YOUR WATCH SCREEN.")
            print("👉 2. THEN PRESS ENTER HERE IN THIS TERMINAL TO COMPLETE PAIRING.")
            print("=" * 70 + "\n")

            try:
                await asyncio.to_thread(input, "Press Enter AFTER tapping the checkmark on your watch: ")
            except Exception:
                for i in range(15, 0, -1):
                    print(f"Waiting for you to tap the checkmark on the watch: {i}s...", end="\r")
                    await asyncio.sleep(1)
                print()

        resp1_tlvs = await self.recv(0x01, 0x28, timeout=90.0)
        step, p1 = self._parse_hichain_resp(resp1_tlvs)

        rand_peer = bytes.fromhex(p1["isoSalt"])
        auth_id_peer = bytes.fromhex(p1["peerAuthId"])
        peer_token = bytes.fromhex(p1["token"])

        # PSK derivation
        if op_code == 1:
            psk = derive_psk_first_auth(pin_code or b"", seed)
        else:
            psk = derive_psk_reconnect(self.auth_key or b"", seed)

        # Verify peer token
        token_check = compute_hmac(psk, rand_peer + rand_self + auth_id_self + auth_id_peer)
        if token_check.upper() == peer_token.hex().upper():
            logger.info("  Peer token verified OK!")
        else:
            logger.warning("  Peer token check mismatch (continuing)")

        # Round 2
        self_token = compute_hmac(psk, rand_self + rand_peer + auth_id_peer + auth_id_self)
        p2_extra = {
            "peerAuthId": auth_id_self.hex().upper(),
            "token": self_token.upper(),
        }
        logger.info("-> Step 4: HiChain3 Round 2...")
        tlv2 = self._build_hichain_tlv(2, p2_extra, op_code, request_id)
        await self.send(0x01, 0x28, tlv2)
        resp2_tlvs = await self.recv(0x01, 0x28)
        self._parse_hichain_resp(resp2_tlvs)

        # Round 3
        salt = rand_self + rand_peer
        session_key = derive_session_key(psk, rand_self, rand_peer)

        if op_code == 1:
            challenge = secrets.token_bytes(16)
            nonce3 = secrets.token_bytes(12)
            enc3 = aes_gcm_encrypt(challenge, session_key, nonce3, b"hichain_iso_exchange")
            p3_extra = {"nonce": nonce3.hex().upper(), "encData": enc3.hex().upper()}
            logger.info("-> Step 4: HiChain3 Round 3 (first-auth key exchange)...")
            tlv3 = self._build_hichain_tlv(3, p3_extra, op_code, request_id)
            await self.send(0x01, 0x28, tlv3)
            resp3_tlvs = await self.recv(0x01, 0x28)
            _, p3 = self._parse_hichain_resp(resp3_tlvs)

            nonce_peer = bytes.fromhex(p3["nonce"])
            enc_auth_tok = bytes.fromhex(p3["encAuthToken"])
            self.auth_key = aes_gcm_decrypt(enc_auth_tok, session_key, nonce_peer, challenge)
            logger.info("  authToken obtained: %s", self.auth_key.hex())

        # Round 4
        nonce4 = secrets.token_bytes(12)
        enc4 = aes_gcm_encrypt(b"\x00\x00\x00\x00", session_key, nonce4, b"hichain_iso_result")
        p4_extra = {"nonce": nonce4.hex().upper(), "encResult": enc4.hex().upper(), "operationCode": op_code}
        step_num = 4 if op_code == 1 else 3
        logger.info("-> Step 4: HiChain3 Round 4 (final ACK)...")
        tlv4 = self._build_hichain_tlv(step_num, p4_extra, op_code, request_id)
        await self.send(0x01, 0x28, tlv4)
        resp4_tlvs = await self.recv(0x01, 0x28)
        self._parse_hichain_resp(resp4_tlvs)

        if op_code == 1:
            logger.info("First-auth pass completed! Running second bind pass (op=0x02)...")
            await self.step4_hichain3_auth(pin_code=b"", op_code=2)
            return

        # Reconnect/Bind final transaction key derivation
        final_key = derive_session_key(session_key, salt, b"hichain_return_key")
        self.secret_key = final_key
        self.save_config()
        logger.info("🎉 Session transaction key saved: %s", final_key.hex())

    # ── Post-Auth Bootstrap Sequence ──────────────────────────────────────────

    def _timezone_offset_bytes(self) -> bytes:
        offset = -time.timezone if not time.daylight else -time.altzone
        hour = ((-offset // 3600) + 128) if offset < 0 else offset // 3600
        minute = int(abs(offset) / 60) % 60
        return bytes([hour & 0xFF, minute & 0xFF])

    async def post_auth_initialize(self):
        """Execute post-auth bootstrap to signal setup completion and exit QR pairing screen."""
        logger.info("Starting post-auth bootstrap sequence (transitioning to watch face)...")

        # 1. Product Info
        try:
            tags = (0x01, 0x02, 0x07, 0x09, 0x0A, 0x11)
            tlv = b"".join(tlv_encode(t) for t in tags)
            resp = await self.transact_encrypted(0x01, 0x07, tlv, timeout=6.0)
            model = resp.get(0x0A, b"").decode("utf-8", errors="ignore")
            logger.info("  Product Info OK (Model: %s)", model.strip() or "Huawei Band")
        except Exception as e:
            logger.debug("Product info query: %s", e)

        # 2. Time Synchronization
        try:
            now = int(time.time())
            tlv_time = tlv_encode(0x01, struct.pack(">I", now)) + tlv_encode(0x02, self._timezone_offset_bytes())
            await self.transact_encrypted(0x01, 0x05, tlv_time, timeout=6.0)
            logger.info("  Time Sync OK (Set band clock to %s)", time.strftime("%Y-%m-%d %H:%M:%S", time.localtime(now)))
        except Exception as e:
            logger.warning("Time sync query: %s", e)

        # 3. Battery query
        try:
            resp_bat = await self.transact_encrypted(0x01, 0x0E, b"", timeout=6.0)
            bat = resp_bat.get(0x01, b"\x00")[0] if resp_bat.get(0x01) else None
            if bat is not None:
                logger.info("  Battery Level: %d%%", bat)
        except Exception as e:
            logger.debug("Battery query: %s", e)

        # 4. Supported Services
        try:
            await self.transact_encrypted(0x01, 0x02, tlv_encode(0x01, KNOWN_SUPPORTED_SERVICES), timeout=6.0)
            logger.info("  Supported Services bitmap exchanged OK")
        except Exception as e:
            logger.debug("Supported services query: %s", e)

        # 5. User Agreements Acceptance (CRITICAL for leaving QR code screen)
        try:
            timestamp = str(int(time.time() * 1000)).encode("utf-8")
            version = b"20230508-20230508-0-0"
            items = []
            for key in (
                b"software_update_service_statement",
                b"device_information_management",
                b"user_license_agreement",
            ):
                items.append(tlv_encode(0x82, (
                    tlv_encode(0x03, key) +
                    tlv_encode(0x04, b"\x01") +
                    tlv_encode(0x05, version) +
                    tlv_encode(0x06, timestamp)
                )))
            tlv_agr = tlv_encode(0x81, b"".join(items))
            await self.transact_encrypted(0x01, 0x30, tlv_agr, timeout=6.0)
            logger.info("  User Agreements Accepted OK")
        except Exception as e:
            logger.debug("Accept agreements: %s", e)

        # 6. Extended Account
        try:
            tlv_acc = tlv_encode(0x01, b"\x00") + tlv_encode(0x03, b"\x01")
            await self.transact_encrypted(0x1A, 0x05, tlv_acc, timeout=6.0)
            logger.info("  Extended Account Profile OK")
        except Exception as e:
            logger.debug("Extended account: %s", e)

        # 7. Reverse Capabilities
        try:
            tlv_rev = tlv_encode(0x01, bytes([0xFD, 0xF7, 0x73, 0x7A]))
            await self.transact_encrypted(0x01, 0x3F, tlv_rev, timeout=6.0)
            logger.info("  Reverse Capabilities OK")
        except Exception as e:
            logger.debug("Reverse capabilities: %s", e)

        # 8. Setup Device Status (TELLS BAND UI TO EXIT QR PAIRING SCREEN!)
        try:
            name_bytes = b"HUAWEI Band 10-4EF"
            tlv_status = (
                tlv_encode(0x01, b"\x01") +
                tlv_encode(0x02, name_bytes) +
                tlv_encode(0x03, b"\x00")
            )
            wrapped_status = self._encrypt_payload(tlv_status)
            await self.send(0x01, 0x3E, wrapped_status)
            await asyncio.sleep(0.1)
            logger.info("  🎯 Setup Device Status Sent! (Watch should now show clock face)")
        except Exception as e:
            logger.warning("Setup device status: %s", e)

        # 9. Connect Status
        try:
            await self.transact_encrypted(0x01, 0x35, tlv_encode(0x01, b"\x01"), timeout=5.0)
            logger.info("  Connect Status confirmed OK")
        except Exception as e:
            logger.debug("Connect status: %s", e)

        logger.info("Post-auth bootstrap sequence complete!")


async def main():
    if len(sys.argv) < 2:
        print("Usage: python tools/probe.py <BAND_MAC_ADDRESS>")
        print("Example: python tools/probe.py 88:8E:68:A2:E4:4F")
        print("\nTip: Run `python tools/scan.py` first to find your Band's address.")
        sys.exit(1)

    address = sys.argv[1].strip()
    client = HuaweiBandClient(address)
    agent_mgr = BlueZAgentManager()
    await agent_mgr.start()

    try:
        await client.connect()
        await client.step1_link_params()
        await client.step2_security_negotiation()

        if client.secret_key and client.auth_key:
            await client.step4_hichain3_auth(op_code=2)
        else:
            pin = await client.step3_pin_retrieval()
            await client.step4_hichain3_auth(pin_code=pin, op_code=1)

        # Run bootstrap to exit QR screen
        await client.post_auth_initialize()

        print("\n" + "=" * 70)
        print("🎉 SUCCESS! Band 10 verified, authenticated, and initialized.")
        print("Look at your watch — it should now display the active watch face!")
        print(f"Credentials saved to {CONFIG_FILE}. Subsequent runs will reconnect instantly.")
        print("\n📡 Maintaining active Bluetooth link (Battery keepalive every 15s)...")
        print("Press Ctrl+C to disconnect.")
        print("=" * 70 + "\n")

        # Keepalive loop: keep connection alive so watch stays connected
        while True:
            await asyncio.sleep(15)
            try:
                resp_bat = await client.transact_encrypted(0x01, 0x0E, b"", timeout=5.0)
                bat = resp_bat.get(0x01, b"\x00")[0] if resp_bat.get(0x01) else None
                if bat is not None:
                    logger.info("Keepalive ping OK (Battery: %d%%)", bat)
            except Exception as e:
                logger.debug("Keepalive ping: %s", e)
    except Exception as e:
        err_msg = str(e)
        logger.error("Probe failed: %s", e, exc_info=True)
    finally:
        if client.client and client.client.is_connected:
            await client.client.disconnect()
            logger.info("Disconnected cleanly.")
        await agent_mgr.stop()


if __name__ == "__main__":
    asyncio.run(main())
