"""
Huawei Band 10 BLE connector — Gadgetbridge-aligned handshake (HiChain3).

Handshake flow for Band 10 (deviceSupportType=4, authType=5):
  1. Link Params
  2. Security Negotiation  → discovers authType=HiChain3
  3. PIN Code              → band returns encrypted PIN (user confirms on device)
  4. HiChain3 auth (4 steps, JSON over sliced BLE, HMAC/HKDF/AES-GCM)
  5. Device is now authenticated; subsequent runs skip steps 3-4

HiChain3 step summary:
  Step 1  →  send {seed, randSelf, peerAuthId=ourId}
  Step 1  ←  get  {isoSalt=randPeer, peerAuthId=bandId, token=HMAC(psk,…)}
  Step 2  →  send {token=HMAC(psk,…)}
  Step 2  ←  get  {returnCodeMac=HMAC(psk, 0x00000000)}
  Step 3  →  send {nonce, encData=AES-GCM(challenge, sessionKey, nonce, aad)}   [first-auth only]
  Step 3  ←  get  {nonce, encAuthToken} → decrypt → secretKey for future use
  Step 4  →  send {nonce, encResult=AES-GCM(0x00000000, sessionKey, nonce, aad)}
  Step 4  ←  OK
"""
import asyncio
import binascii
from collections import deque
from datetime import datetime, timezone
import hashlib
import hmac as _hmac
import json
import logging
import os
import secrets
import struct
import sys
import time
from configparser import ConfigParser
from pathlib import Path
from urllib.parse import urlencode
from urllib.request import urlopen

# Ensure project root is in sys.path
_project_root = Path(__file__).resolve().parent.parent
if str(_project_root) not in sys.path:
    sys.path.insert(0, str(_project_root))
_tools_dir = Path(__file__).resolve().parent
if str(_tools_dir) not in sys.path:
    sys.path.insert(0, str(_tools_dir))

from tools.bluez_agent import BlueZAgentManager

from bleak import BleakClient, BleakScanner
from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes
from cryptography.hazmat.primitives import padding as _padding
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.primitives.kdf.hkdf import HKDF
from cryptography.hazmat.primitives import hashes

import analytics as band_analytics

LOG_LEVEL = getattr(logging, os.getenv("BAND10_LOG", "INFO").upper(), logging.INFO)
logging.basicConfig(level=LOG_LEVEL,
                    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s")
logger = logging.getLogger("band10")

# ── BLE UUIDs & constants ─────────────────────────────────────────────────────
DEVICE_MAC  = "88:8E:68:A2:E4:4F"
CLIENT_MAC  = "FF:FF:FF:A9:F0:23"   # random fake MAC (like Gadgetbridge)
CONFIG_FILE = Path("band.ini")
DATA_DIR    = Path("data")

GATT_WRITE  = "0000fe01-0000-1000-8000-00805f9b34fb"
GATT_READ   = "0000fe02-0000-1000-8000-00805f9b34fb"
MAGIC       = 0x5A

# Service / command IDs
SVC_DEV     = 0x01
SVC_FITNESS = 0x07
SVC_ACCOUNT = 0x1A
SVC_STRESS  = 0x20
SVC_WEATHER = 0x0F
SVC_GPS_TIME = 0x18
SVC_WATCHFACE = 0x27
SVC_FILE_UPLOAD = 0x28
SVC_FILE_DOWNLOAD = 0x2C
SVC_P2P     = 0x34
SVC_HR_RRI_TEST = 0x19
SVC_BREATH  = 0x2D
CMD_LINK    = 0x01
CMD_SUPPORTED_SERVICES = 0x02
CMD_SUPPORTED_COMMANDS = 0x03
CMD_TIME    = 0x05
CMD_PRODUCT = 0x07
CMD_AUTH    = 0x13
CMD_BOND    = 0x0E
CMD_BPARAMS = 0x0F
CMD_PHONE_INFO = 0x10
CMD_DEVICE_STATUS = 0x16
CMD_SECNEGO = 0x33
CMD_CONNECT_STATUS = 0x35
CMD_EXPAND_CAPABILITY = 0x37
CMD_PINCODE = 0x2C
CMD_HICHAIN = 0x28
CMD_SETTING_RELATED = 0x31
CMD_WEAR_STATUS = 0x3D
CMD_SETUP_DEVICE_STATUS = 0x3E
CMD_ACCEPT_AGREEMENT = 0x30
CMD_REVERSE_CAPABILITIES = 0x3F
CMD_COUNTRY_CODE = 0x0A
CMD_EXTENDED_ACCOUNT = 0x05
CMD_ACCOUNT_SWITCH = 0x06
CMD_TRUSLEEP = 0x16
CMD_AUTO_HR = 0x17
CMD_AUTO_SPO2 = 0x24
CMD_STRESS_AUTO = 0x09
CMD_HR_RRI_OPEN_CLOSE = 0x01
CMD_HR_REALTIME_DATA = 0x03
CMD_HR_RRI_DATA = 0x05
CMD_SLEEP_BREATH = 0x01

WEATHER_CURRENT = 0x01
WEATHER_SUPPORT = 0x02
WEATHER_DEVICE_REQUEST = 0x04
WEATHER_UNIT = 0x05
WEATHER_EXTENDED_SUPPORT = 0x06
WEATHER_FORECAST = 0x08
WEATHER_START = 0x09
WEATHER_SUN_MOON_SUPPORT = 0x0A
GPS_CURRENT = 0x07
WATCHFACE_PARAMS = 0x01
WATCHFACE_LIST = 0x02
WATCHFACE_OPERATION = 0x03
WATCHFACE_CONFIRM = 0x05
WATCHFACE_NAMES = 0x06
RESULT_SUCCESS = 0x000186A0
RESULT_WEATHER_ALREADY_STARTED = 0x000186A3
RESULT_WEATHER_DEVICE_REQUEST = 0x000186AA

FILE_CMD_INIT = 0x01
FILE_CMD_HASH = 0x02
FILE_CMD_INFO = 0x03
FILE_CMD_REQUEST_BLOCK = 0x04
FILE_CMD_BLOCK = 0x05
FILE_CMD_COMPLETE = 0x06
FILE_CMD_INCOMING_INIT = 0x07
FILE_TYPE_SLEEP_STATE = 0x0E
FILE_TYPE_SLEEP_DATA = 0x0F
FILE_TYPE_RRI = 0x10
FILE_TYPE_SEQUENCE_DATA = 0x16

UPLOAD_CMD_INFO = 0x02
UPLOAD_CMD_HASH = 0x03
UPLOAD_CMD_CONSULT = 0x04
UPLOAD_CMD_NEXT_CHUNK = 0x05
UPLOAD_CMD_CHUNK = 0x06
UPLOAD_CMD_RESULT = 0x07
UPLOAD_CMD_DEVICE_RESPONSE = 0x08

# Crypto constants (same across all Gadgetbridge / huawei-lpv2 builds)
DIGEST_SECRETS = {
    1: bytes([0x70,0xFB,0x6C,0x24,0x03,0x5F,0xDB,0x55,0x2F,0x38,0x89,0x8A,0xEE,0xDE,0x3F,0x69]),
    2: bytes([0x93,0xAC,0xDE,0xF7,0x6A,0xCB,0x09,0x85,0x7D,0xBF,0xE5,0x26,0x1A,0xAB,0xCD,0x78]),
    3: bytes([0x9C,0x27,0x63,0xA9,0xCC,0xE1,0x34,0x76,0x6D,0xE3,0xFF,0x61,0x18,0x20,0x05,0x53]),
}
SECRET_KEY_1 = {
    1: bytes([0x6F,0x75,0x6A,0x79,0x6D,0x77,0x71,0x34,0x63,0x6C,0x76,0x39,0x33,0x37,0x38,0x79]),
    2: bytes([0x55,0x53,0x86,0xFC,0x63,0x20,0x07,0xAA,0x86,0x49,0x35,0x22,0xB8,0x6A,0xE2,0x5C]),
    3: bytes([0x55,0x53,0x86,0xFC,0x63,0x20,0x07,0xAA,0x86,0x49,0x35,0x22,0xB8,0x6A,0xE2,0x5C]),
}
SECRET_KEY_2 = {
    1: bytes([0x62,0x31,0x30,0x6A,0x67,0x66,0x64,0x39,0x79,0x37,0x76,0x73,0x75,0x64,0x61,0x39]),
    2: bytes([0x33,0x07,0x9B,0xC5,0x7A,0x88,0x6D,0x3C,0xF5,0x61,0x37,0x09,0x6F,0x22,0x80,0x00]),
    3: bytes([0x33,0x07,0x9B,0xC5,0x7A,0x88,0x6D,0x3C,0xF5,0x61,0x37,0x09,0x6F,0x22,0x80,0x00]),
}
GROUP_ID = "7B0BC0CBCE474F6C238D9661C63400B797B166EA7849B3A370FC73A9A236E989"
DICT_SLEEP_DETAILS_CLASS = 700013
DICT_HRV_CLASS = 500044
DICT_HRV_RMSSD_VALUE = 500044831
DICT_PROBE_CLASSES = {
    "skin_temperature": 400012,
    "emotion": 500031,
    "sleep_apnea": 500002,
    "arterial_stiffness": 400017,
    "altitude": 200003,
    "blood_pressure": 10002,
    "lake_louise_ams": 500006,
    "hrv": DICT_HRV_CLASS,
}
P2P_DICT_MODULE = "hw.unitedevice.datadictionarysync"
P2P_DICT_PACKAGE = "hw.watch.health.filesync"
P2P_LOCAL_FINGERPRINT = "UniteDeviceManagement"
P2P_REMOTE_FINGERPRINT = "SystemApp"
SVC_DATASYNC = 0x37
DATASYNC_CONFIG_CMD = 0x01
DATASYNC_EVENT_CMD = 0x02
DATASYNC_DATA_CMD = 0x03
DATASYNC_DICT_DATA_CMD = 0x04
DATASYNC_REPLY_OK = 100000
DATASYNC_FEATURE_CONFIG_ID = 900100007
DATASYNC_FEATURE_SRC = "featureManager"
DATASYNC_FEATURE_DST = "hw.unitedevice.configManager"
KNOWN_SUPPORTED_SERVICES = bytes([
    0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A,
    0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10, 0x11, 0x12, 0x13, 0x14,
    0x15, 0x16, 0x17, 0x18, 0x19, 0x1A, 0x1B, 0x1D, 0x20,
    0x22, 0x23, 0x24, 0x25, 0x26, 0x27, 0x2A, 0x2B, 0x2D, 0x2E,
    0x30, 0x32, 0x33, 0x34, 0x35,
])
COMMANDS_PER_SERVICE = {
    0x01: bytes([0x04, 0x07, 0x08, 0x09, 0x0A, 0x0D, 0x0E, 0x10, 0x11, 0x12,
                 0x13, 0x14, 0x1B, 0x1A, 0x1D, 0x21, 0x22, 0x23, 0x24, 0x29,
                 0x2A, 0x2B, 0x32, 0x2E, 0x31, 0x30, 0x35, 0x36, 0x37, 0x2F]),
    0x02: bytes([0x01, 0x04, 0x05, 0x06, 0x07, 0x08]),
    0x03: bytes([0x01, 0x03, 0x04]),
    0x04: bytes([0x01]),
    0x05: bytes([0x01]),
    0x06: bytes([0x01]),
    0x07: bytes([0x01, 0x03, 0x05, 0x07, 0x08, 0x09, 0x0A, 0x0E, 0x10, 0x13,
                 0x16, 0x15, 0x17, 0x18, 0x1B, 0x1C, 0x1D, 0x1E, 0x21, 0x22,
                 0x23, 0x24, 0x25, 0x28, 0x29, 0x06, 0x1F]),
    0x08: bytes([0x01, 0x02, 0x03]),
    0x09: bytes([0x01, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F]),
    0x0A: bytes([0x01, 0x09, 0x0A]),
    0x0B: bytes([0x01, 0x03]),
    0x0C: bytes([0x01]),
    0x0D: bytes([0x01]),
    0x0F: bytes([0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C]),
    0x10: bytes([0x01]),
    0x11: bytes([0x01]),
    0x12: bytes([0x01]),
    0x13: bytes([0x01]),
    0x14: bytes([0x01]),
    0x15: bytes([0x01]),
    0x16: bytes([0x01, 0x03, 0x07]),
    0x17: bytes([0x01, 0x04, 0x06, 0x07, 0x0B, 0x0C, 0x10, 0x12, 0x15, 0x17]),
    0x18: bytes([0x01, 0x02, 0x04, 0x05, 0x06, 0x09]),
    0x19: bytes([0x01, 0x03, 0x04, 0x05]),
    0x1A: bytes([0x01, 0x03, 0x07, 0x05, 0x06]),
    0x1B: bytes([0x01, 0x0F, 0x19, 0x1A]),
    0x1D: bytes([0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A]),
    0x20: bytes([0x01, 0x02, 0x03, 0x04, 0x09, 0x0A]),
    0x22: bytes([0x01]),
    0x23: bytes([0x02, 0x0B]),
    0x24: bytes([0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A,
                 0x0B, 0x0C]),
    0x25: bytes([0x02, 0x04, 0x0E]),
    0x26: bytes([0x02, 0x03]),
    0x27: bytes([0x01, 0x02, 0x03, 0x05, 0x06, 0x0E]),
    0x2A: bytes([0x01, 0x06]),
    0x2B: bytes([0x12]),
    0x2D: bytes([0x01]),
    0x2E: bytes([0x01, 0x02, 0x03]),
    0x30: bytes([0x01]),
    0x32: bytes([0x01]),
    0x33: bytes([0x01, 0x02]),
    0x34: bytes([0x01]),
    0x35: bytes([0x03, 0x04]),
}
EXPAND_CAPABILITY_LABELS = {
    "p2p_get_app_version": 14,
    "send_country_code": 30,
    "track_p2p": 0x36,
    "multi_device": 109,
    "sleep_apnea": 107,
    "dict_sleep_sync": 143,
    "three_circle": 154,
    "three_circle_lite": 156,
    "send_site_id": 170,
    "diff_account_pairing_optimization": 172,
    "device_command_dict_data": 173,
    "reverse_capabilities": 182,
    "external_calendar": 184,
    "bed_time": 199,
    "emotion": 206,
    "hrv": 235,
    "ecg": 106,
    "ecg_open": 27,
    "ecg_notification": 138,
    "arrhythmia_other": 113,
    "arrhythmia_cn": 168,
    "arrhythmia_ce": 255,
    "weather_uv_index": 0x2F,
    "weather_extended_hour_forecast": 0xC0,
    "notification_picture": 256,
    "contacts_sync": 271,
}
SLEEP_DETAILS_FIELD_IDS = {
    700013686: "fallAsleepTime",
    700013298: "bedTime",
    700013973: "risingTime",
    700013156: "wakeupTime",
    700013786: "validData",
    700013254: "sleepDataQuality",
    700013679: "deepPart",
    700013721: "snoreFreq",
    700013245: "sleepScore",
    700013713: "sleepLatency",
    700013232: "sleepEfficiency",
    700013436: "minHeartRate",
    700013502: "maxHeartRate",
    700013340: "minOxygenSaturation",
    700013026: "maxOxygenSaturation",
    700013646: "minBreathRate",
    700013492: "maxBreathRate",
    700013824: "hrvDayToBaseline",
    700013355: "maxHrvBaseline",
    700013305: "minHrvBaseline",
    700013878: "avgHrv",
    700013236: "breathRateDayToBaseline",
    700013225: "maxBreathRateBaseline",
    700013839: "minBreathRateBaseline",
    700013886: "avgBreathRate",
    700013718: "oxygenSaturationDayToBaseline",
    700013227: "maxOxygenSaturationBaseline",
    700013633: "minOxygenSaturationBaseline",
    700013468: "avgOxygenSaturation",
    700013810: "heartRateDayToBaseline",
    700013841: "maxHeartRateBaseline",
    700013722: "minHeartRateBaseline",
    700013580: "avgHeartRate",
    700013759: "rdi",
    700013635: "wakeCount",
    700013670: "turnOverCount",
    700013821: "prepareSleepTime",
    700013925: "wakeUpFeeling",
    700013697: "sleepVersion",
    700013503: "stableSleepRatio",
}
SLEEP_DETAILS_DOUBLE_FIELDS = {
    "minOxygenSaturation",
    "maxOxygenSaturation",
}

# ── Packet framing ────────────────────────────────────────────────────────────

def tlv_enc(tag: int, value: bytes = b"") -> bytes:
    return bytes([tag]) + varint_enc(len(value)) + value

def varint_enc(value: int) -> bytes:
    """Huawei TLV length VarInt: big-endian 7-bit groups, high bit continues."""
    if value < 0:
        raise ValueError("negative TLV length")
    groups = [value & 0x7F]
    value >>= 7
    while value:
        groups.append((value & 0x7F) | 0x80)
        value >>= 7
    return bytes(reversed(groups))

def varint_dec(data: bytes, offset: int) -> tuple[int, int]:
    result = 0
    i = offset
    while True:
        if i >= len(data):
            raise ValueError("truncated TLV VarInt")
        b = data[i]
        i += 1
        result += b & 0x7F
        if (b & 0x80) == 0:
            return result, i
        result <<= 7

def tlv_dec(data: bytes) -> dict:
    out, i = {}, 0
    while i < len(data):
        tag = data[i]; i += 1
        if i >= len(data) and tag == 0:
            break
        ln, i = varint_dec(data, i)
        if i + ln > len(data):
            raise ValueError(f"TLV length {ln} exceeds remaining {len(data) - i}")
        out[tag] = data[i:i+ln]; i += ln
    return out

def tlv_items(data: bytes) -> list[tuple[int, bytes]]:
    """Decode TLVs while preserving duplicate tags such as repeated containers."""
    out, i = [], 0
    while i < len(data):
        tag = data[i]; i += 1
        if i >= len(data) and tag == 0:
            break
        ln, i = varint_dec(data, i)
        if i + ln > len(data):
            raise ValueError(f"TLV length {ln} exceeds remaining {len(data) - i}")
        out.append((tag, data[i:i+ln]))
        i += ln
    return out

def crc16(data: bytes) -> int:
    return binascii.crc_hqx(data, 0)

def _make_unsliced(svc: int, cmd: int, tlv: bytes) -> bytes:
    """Standard single-frame packet."""
    body   = bytes([svc, cmd]) + tlv
    hdr    = bytes([MAGIC]) + struct.pack(">H", len(body) + 1) + b"\x00" + body
    return hdr + struct.pack(">H", crc16(hdr))

def _make_sliced(svc: int, cmd: int, tlv: bytes, slice_size: int = 244) -> list[bytes]:
    """Split a large TLV payload into sliced BLE frames."""
    HEADER = 5   # magic + length(2) + slice + flag
    FOOTER = 2   # CRC16
    MAX    = slice_size - HEADER - FOOTER   # usable per frame

    # First slice needs sID + cID
    chunks = []
    pos    = 0
    first  = True
    total  = len(tlv)

    while pos < total or first:
        if first:
            avail = MAX - 2   # reserve 2 for sID+cID
        else:
            avail = MAX

        chunk = tlv[pos:pos + avail]
        pos  += len(chunk)
        last  = (pos >= total)
        first = False
        chunks.append((chunk, last))

    frames  = []
    flag    = 0
    for i, (chunk, last) in enumerate(chunks):
        is_first = (i == 0)
        slice_b  = 0x01 if is_first else (0x03 if last else 0x02)

        if is_first:
            payload = bytes([svc, cmd]) + chunk
        else:
            payload = chunk

        body_len = len(payload)
        hdr = bytes([MAGIC]) + struct.pack(">H", body_len + FOOTER) + bytes([slice_b, flag])
        frame = hdr + payload
        frame += struct.pack(">H", crc16(frame))
        frames.append(frame)
        flag = (flag + 1) & 0xFF

    return frames

def parse_frame(data: bytes):
    """Return (svc, cmd, tlv_dict). Handles only complete reassembled data."""
    svc, cmd, payload = frame_payload(data)
    return svc, cmd, tlv_dec(payload)

def frame_payload(data: bytes) -> tuple[int, int, bytes]:
    """Return (svc, cmd, raw payload) for a complete reassembled frame."""
    slice_b = data[3]
    if slice_b == 0x00:
        payload = data[4:-2]
    else:
        # Already reassembled — payload starts after magic+len+slice+flag
        payload = data[4:-2]
    svc = payload[0]
    cmd = payload[1]
    return svc, cmd, payload[2:]

# ── Crypto ────────────────────────────────────────────────────────────────────

def hmac_sha256(key: bytes, msg: bytes) -> bytes:
    return _hmac.new(key, msg=msg, digestmod=hashlib.sha256).digest()

def sha256(data: bytes) -> bytes:
    return hashlib.sha256(data).digest()

def hexu(data: bytes) -> str:
    """Gadgetbridge's StringUtils.bytesToHex emits uppercase hex."""
    return data.hex().upper()

def hkdf_sha256(key: bytes, salt: bytes, info: bytes, length: int) -> bytes:
    return HKDF(algorithm=hashes.SHA256(), length=length,
                salt=salt, info=info).derive(key)

def aes_cbc_enc(plain: bytes, key: bytes, iv: bytes) -> bytes:
    pad = _padding.PKCS7(128).padder()
    p   = pad.update(plain) + pad.finalize()
    enc = Cipher(algorithms.AES(key), modes.CBC(iv)).encryptor()
    return enc.update(p) + enc.finalize()

def aes_cbc_dec(ciph: bytes, key: bytes, iv: bytes) -> bytes:
    dec = Cipher(algorithms.AES(key), modes.CBC(iv)).decryptor()
    p   = dec.update(ciph) + dec.finalize()
    unp = _padding.PKCS7(128).unpadder()
    return unp.update(p) + unp.finalize()

def aes_gcm_enc(plain: bytes, key: bytes, nonce: bytes, aad: bytes = None) -> bytes:
    return AESGCM(key).encrypt(nonce, plain, aad)

def aes_gcm_dec(ciph: bytes, key: bytes, nonce: bytes, aad: bytes = None) -> bytes:
    return AESGCM(key).decrypt(nonce, ciph, aad)

def decrypt_pin_code(enc_method: int, auth_version: int, msg: bytes, iv: bytes) -> bytes:
    key = DIGEST_SECRETS.get(auth_version, DIGEST_SECRETS[1])
    if enc_method == 0x01:
        return aes_gcm_dec(msg, key, iv[:12])
    return aes_cbc_dec(msg, key, iv[:16])

def create_secret_key(auth_version: int, mac: str) -> bytes:
    k1 = SECRET_KEY_1.get(auth_version, SECRET_KEY_1[1])
    k2 = SECRET_KEY_2.get(auth_version, SECRET_KEY_2[1])
    mac_key = (mac.replace(":", "") + "0000").encode()[:16]
    mixed   = bytes([(((k1[i] & 0xFF) << 4) ^ (k2[i] & 0xFF)) & 0xFF for i in range(16)])
    mhash   = sha256(mixed)
    final   = bytes([(((mhash[i] & 0xFF) >> 6) ^ mac_key[i]) & 0xFF for i in range(16)])
    return sha256(final)[:16]

# ── Config ────────────────────────────────────────────────────────────────────

def load_or_create_config() -> dict:
    cfg = ConfigParser()
    if CONFIG_FILE.exists():
        cfg.read(CONFIG_FILE)
        c = dict(cfg["band10"])
        secret_key = bytes.fromhex(c["secret_key"]) if c.get("secret_key") else None
        auth_key = bytes.fromhex(c["auth_key"]) if c.get("auth_key") else None
        stored_android_id = c.get("android_id", "")
        android_id = stored_android_id.upper()
        if (not secret_key) and (len(android_id) != 32 or any(ch not in "0123456789abcdefABCDEF" for ch in android_id)):
            android_id = hexu(secrets.token_bytes(16))
            cfg["band10"]["android_id"] = android_id
            with open(CONFIG_FILE, "w") as f:
                cfg.write(f)
            logger.info("Regenerated Gadgetbridge-style 32-char android_id in band.ini.")
        elif stored_android_id != android_id:
            cfg["band10"]["android_id"] = android_id
            with open(CONFIG_FILE, "w") as f:
                cfg.write(f)
        c["secret"]     = bytes.fromhex(c["secret"])
        c["android_id"] = android_id
        c["secret_key"] = secret_key
        c["auth_key"]   = auth_key
        return c

    secret     = secrets.token_bytes(16)
    android_id = hexu(secrets.token_bytes(16))
    cfg["band10"] = {
        "device_mac":  DEVICE_MAC,
        "client_mac":  CLIENT_MAC,
        "secret":      secret.hex(),
        "android_id":  android_id,
        "auth_key":    "",    # persistent HiChain authToken for reconnect
        "secret_key":  "",    # filled after first HiChain auth
    }
    with open(CONFIG_FILE, "w") as f:
        cfg.write(f)
    logger.info("Created band.ini with fresh keys.")
    return {"device_mac": DEVICE_MAC, "client_mac": CLIENT_MAC,
            "secret": secret, "android_id": android_id,
            "auth_key": None, "secret_key": None}

def save_auth_key(key: bytes):
    cfg = ConfigParser()
    cfg.read(CONFIG_FILE)
    cfg["band10"]["auth_key"] = key.hex()
    with open(CONFIG_FILE, "w") as f:
        cfg.write(f)
    logger.info("Saved HiChain auth key to band.ini.")

def save_secret_key(key: bytes):
    cfg = ConfigParser()
    cfg.read(CONFIG_FILE)
    cfg["band10"]["secret_key"] = key.hex()
    with open(CONFIG_FILE, "w") as f:
        cfg.write(f)
    logger.info("Saved HiChain transaction key to band.ini.")


def save_device_mac(address: str):
    cfg = ConfigParser()
    cfg.read(CONFIG_FILE)
    if "band10" not in cfg:
        cfg["band10"] = {}
    cfg["band10"]["device_mac"] = address
    with open(CONFIG_FILE, "w") as f:
        cfg.write(f)
    logger.info(f"Saved BLE device address to band.ini: {address}")


def save_json_artifact(filename: str, payload: dict):
    DATA_DIR.mkdir(exist_ok=True)
    path = DATA_DIR / filename
    path.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
    logger.info(f"Saved {path}")

def save_binary_artifact(filename: str, payload: bytes):
    DATA_DIR.mkdir(exist_ok=True)
    path = DATA_DIR / filename
    path.write_bytes(payload)
    logger.info(f"Saved {path}")

def append_jsonl_artifact(filename: str, payload: dict):
    DATA_DIR.mkdir(exist_ok=True)
    path = DATA_DIR / filename
    with path.open("a", encoding="utf-8") as f:
        f.write(json.dumps(payload, separators=(",", ":")) + "\n")
    logger.info(f"Appended {path}")

def local_time_label(epoch_seconds: int) -> str:
    return time.strftime("%Y-%m-%d %H:%M:%S", time.localtime(epoch_seconds))


def _device_rssi(device) -> int:
    value = getattr(device, "rssi", None)
    if value is None:
        value = getattr(device, "metadata", {}).get("rssi")
    try:
        return int(value)
    except Exception:
        return -999


async def discover_band_device(cfg: dict, scan_timeout: float):
    target_address = str(cfg.get("device_mac") or "").lower()
    target_name = os.getenv("BAND10_DEVICE_NAME", "HUAWEI Band 10").strip().lower()
    devices = await BleakScanner.discover(timeout=scan_timeout)
    scan = []
    for device in devices:
        scan.append({
            "address": device.address,
            "name": device.name or "",
            "rssi": _device_rssi(device),
        })
    scan.sort(key=lambda item: item["rssi"], reverse=True)
    save_json_artifact("latest_ble_scan.json", {
        "target_address": cfg.get("device_mac"),
        "target_name": target_name,
        "timestamp": int(time.time()),
        "timestamp_local": local_time_label(int(time.time())),
        "devices": scan,
    })

    exact = next((device for device in devices if device.address.lower() == target_address), None)
    if exact is not None:
        return exact

    candidates = []
    if target_name:
        for device in devices:
            name = (device.name or "").lower()
            if target_name in name:
                candidates.append(device)
    if not candidates:
        if _env_enabled("BAND10_DIRECT_CONNECT_FALLBACK", "1") and cfg.get("device_mac"):
            logger.info(
                "Band was not visible in scan; falling back to direct Windows BLE connect "
                f"for stored address {cfg['device_mac']}"
            )
            return cfg["device_mac"]
        return None

    candidates.sort(key=_device_rssi, reverse=True)
    chosen = candidates[0]
    if chosen.address != cfg.get("device_mac"):
        logger.info(
            "Discovered band by name with a different BLE address: "
            f"{cfg.get('device_mac')} -> {chosen.address} ({chosen.name})"
        )
        cfg["device_mac"] = chosen.address
        save_device_mac(chosen.address)
    return chosen


def _env_enabled(name: str, default: str = "0") -> bool:
    return os.getenv(name, default).strip().lower() in {"1", "true", "yes", "on"}


def bleak_client_options() -> dict:
    options = {"timeout": float(os.getenv("BAND10_CONNECT_TIMEOUT_SECONDS", "30"))}
    if _env_enabled("BAND10_PAIR_BEFORE_CONNECT"):
        options["pair"] = True
    return options


def _parse_weather_time(value) -> int | None:
    if not value:
        return None
    try:
        text = str(value)
        if text.endswith("Z"):
            text = text[:-1] + "+00:00"
        dt = datetime.fromisoformat(text)
        if dt.tzinfo is None:
            dt = dt.replace(tzinfo=timezone.utc)
        return int(dt.timestamp())
    except Exception:
        return None


def _wmo_to_owm(code) -> int:
    try:
        code = int(code)
    except Exception:
        return 800
    if code == 0:
        return 800
    if code in (1, 2):
        return 801
    if code == 3:
        return 804
    if code in (45, 48):
        return 741
    if code in (51, 53, 55, 56, 57):
        return 300
    if code in (61, 63, 65, 66, 67, 80, 81, 82):
        return 500
    if code in (71, 73, 75, 77, 85, 86):
        return 600
    if code in (95, 96, 99):
        return 200
    return 800


def _safe_list_get(values, index, default=None):
    try:
        return values[index]
    except Exception:
        return default


def _load_weather_payload() -> dict:
    if os.getenv("BAND10_WEATHER_JSON"):
        return json.loads(os.environ["BAND10_WEATHER_JSON"])

    weather_file = os.getenv("BAND10_WEATHER_FILE")
    if weather_file:
        return json.loads(Path(weather_file).read_text(encoding="utf-8"))

    default_file = DATA_DIR / "weather_payload.json"
    if default_file.exists():
        return json.loads(default_file.read_text(encoding="utf-8"))

    lat = os.getenv("BAND10_WEATHER_LAT") or os.getenv("BAND10_LATITUDE")
    lon = os.getenv("BAND10_WEATHER_LON") or os.getenv("BAND10_LONGITUDE")
    if not lat or not lon:
        now = int(time.time())
        return {
            "location": os.getenv("BAND10_WEATHER_LOCATION", "Local Weather"),
            "timestamp": now,
            "source": "Huawhoop sample",
            "current_temp_c": float(os.getenv("BAND10_WEATHER_TEMP_C", "29")),
            "feels_like_c": float(os.getenv("BAND10_WEATHER_FEELS_C", os.getenv("BAND10_WEATHER_TEMP_C", "29"))),
            "humidity": int(os.getenv("BAND10_WEATHER_HUMIDITY", "60")),
            "condition_code": int(os.getenv("BAND10_WEATHER_CONDITION", "800")),
            "wind_speed_kmh": float(os.getenv("BAND10_WEATHER_WIND_KMH", "8")),
            "wind_direction": int(os.getenv("BAND10_WEATHER_WIND_DIR", "180")),
            "low_temp_c": float(os.getenv("BAND10_WEATHER_LOW_C", "24")),
            "high_temp_c": float(os.getenv("BAND10_WEATHER_HIGH_C", "32")),
            "uv_index": int(float(os.getenv("BAND10_WEATHER_UV", "0"))),
            "hourly": [],
            "daily": [],
        }

    params = urlencode({
        "latitude": lat,
        "longitude": lon,
        "current": "temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m,wind_direction_10m",
        "hourly": "temperature_2m,precipitation_probability,uv_index,weather_code",
        "daily": "weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,uv_index_max",
        "forecast_days": os.getenv("BAND10_WEATHER_DAYS", "8"),
        "timezone": "UTC",
    })
    with urlopen(f"https://api.open-meteo.com/v1/forecast?{params}", timeout=15) as response:
        data = json.loads(response.read().decode("utf-8"))

    daily_data = data.get("daily") or {}
    daily = []
    for index, day in enumerate((daily_data.get("time") or [])[:8]):
        daily.append({
            "label": day,
            "timestamp": _parse_weather_time(day),
            "condition_code": _wmo_to_owm(_safe_list_get(daily_data.get("weather_code") or [], index)),
            "high_temp_c": _safe_list_get(daily_data.get("temperature_2m_max") or [], index),
            "low_temp_c": _safe_list_get(daily_data.get("temperature_2m_min") or [], index),
            "sunrise": _parse_weather_time(_safe_list_get(daily_data.get("sunrise") or [], index)),
            "sunset": _parse_weather_time(_safe_list_get(daily_data.get("sunset") or [], index)),
            "uv_index": _safe_list_get(daily_data.get("uv_index_max") or [], index, 0),
        })

    hourly_data = data.get("hourly") or {}
    hourly = []
    for index, value in enumerate((hourly_data.get("time") or [])[:72]):
        hourly.append({
            "timestamp": _parse_weather_time(value),
            "condition_code": _wmo_to_owm(_safe_list_get(hourly_data.get("weather_code") or [], index)),
            "temp_c": _safe_list_get(hourly_data.get("temperature_2m") or [], index),
            "precipitation_probability": _safe_list_get(hourly_data.get("precipitation_probability") or [], index, 0),
            "uv_index": _safe_list_get(hourly_data.get("uv_index") or [], index, 0),
        })

    current = data.get("current") or {}
    return {
        "location": os.getenv("BAND10_WEATHER_LOCATION", "Local Weather"),
        "latitude": float(lat),
        "longitude": float(lon),
        "timestamp": int(time.time()),
        "source": "Open-Meteo",
        "current_temp_c": current.get("temperature_2m"),
        "feels_like_c": current.get("apparent_temperature"),
        "humidity": current.get("relative_humidity_2m"),
        "condition_code": _wmo_to_owm(current.get("weather_code")),
        "wind_speed_kmh": current.get("wind_speed_10m"),
        "wind_direction": current.get("wind_direction_10m"),
        "low_temp_c": daily[0].get("low_temp_c") if daily else current.get("temperature_2m"),
        "high_temp_c": daily[0].get("high_temp_c") if daily else current.get("temperature_2m"),
        "uv_index": int(float(daily[0].get("uv_index") or 0)) if daily else 0,
        "hourly": hourly,
        "daily": daily,
    }


class P2PNoDataError(asyncio.TimeoutError):
    def __init__(self, src_package: str, dst_package: str, ack: dict = None,
                 error_tlvs: dict = None):
        super().__init__(f"Timed out waiting for P2P data from {src_package}")
        self.src_package = src_package
        self.dst_package = dst_package
        self.ack = ack
        self.error_tlvs = error_tlvs


def save_recovery_report(summary: dict):
    DATA_DIR.mkdir(exist_ok=True)
    path = DATA_DIR / "latest_recovery_report.md"
    lines = [
        "# Huawei Band 10 Recovery",
        "",
        f"Generated: {summary.get('generated_at_local', 'unknown')}",
        "",
        f"- Recovery proxy: {summary.get('recovery_proxy_no_hrv', 'n/a')} / 100",
        f"- Strain: {summary.get('strain_score', 'n/a')} / 21",
        f"- Sleep score: {summary.get('sleep_score', 'n/a')} / 100",
        f"- Steps: {summary.get('step_total', 'n/a')}",
        f"- Sleep: {summary.get('sleep_minutes', 'n/a')} min",
        f"- HR: {summary.get('heart_rate_min', 'n/a')} / "
        f"{summary.get('heart_rate_avg', 'n/a')} / {summary.get('heart_rate_max', 'n/a')} bpm",
        f"- SpO2: {summary.get('spo2_min', 'n/a')} / "
        f"{summary.get('spo2_avg', 'n/a')} / {summary.get('spo2_max', 'n/a')} %",
        "",
        f"Step window: {summary.get('step_window_start', 'n/a')} to {summary.get('step_window_end', 'n/a')}",
        f"Sleep window: {summary.get('sleep_window_start', 'n/a')} to {summary.get('sleep_window_end', 'n/a')}",
        "",
        "Note: recovery is a proxy until HRV is available from this firmware.",
    ]
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    logger.info(f"Saved {path}")

# ── Band class ────────────────────────────────────────────────────────────────

class Band:
    def __init__(self, client: BleakClient, cfg: dict):
        self.client     = client
        self.device_mac = cfg["device_mac"]
        self.client_mac = cfg["client_mac"]
        self.android_id = cfg["android_id"]          # Gadgetbridge-style 32-char hex string
        self.auth_key   = cfg.get("auth_key")        # Persistent HiChain authToken
        self.secret_key = cfg.get("secret_key")      # Per-session hichain_return_key

        self.client_serial = self.client_mac.replace(":", "")[-6:].encode()

        # Filled during handshake
        self.auth_version     = 1
        self.server_nonce     = b""
        self.dev_support_type = 0
        self.auth_algo        = 0
        self.bond_state       = 0
        self.enc_method       = 0
        self.auth_mode        = 0
        self.slice_size       = 244
        self.enc_counter      = 0
        self.p2p_sequence     = 1
        self.first_key        = b""
        self.band_device_id   = ""   # set from security negotiation tag=0x05

        self._loop    = None
        self._event   = asyncio.Event()
        self._rx_queue = deque()   # queue of complete reassembled frames
        self._rx_buf  = None       # reassembly buffer for sliced packets
        self._rx_partial = b""     # BLE-level fragments of one LPv2 frame
        self._rx_svc  = 0
        self._rx_cmd  = 0
        self._pending_file_inits = []
        self._feature_config_requested = False
        self._live_rri_samples = []
        self._live_hr_samples = []
        self._live_rri_events = []
        self._last_weather_payload = None
        self._weather_push_active = False
        self.supported_services = set()
        self.commands_per_service = {}
        self.expand_capabilities = b""
        self.capability_flags = {}

    # ── BLE low-level ─────────────────────────────────────────────────────────

    async def connect(self):
        self._loop = asyncio.get_running_loop()
        await self.client.start_notify(GATT_READ, self._on_notify)
        logger.info("Subscribed to notifications.")

    def _on_notify(self, _char, data: bytearray):
        raw = bytes(data)
        logger.debug(f"← {raw.hex()}")

        buf = self._rx_partial + raw
        self._rx_partial = b""

        while buf:
            if len(buf) < 4:
                self._rx_partial = buf
                return
            if buf[0] != MAGIC:
                logger.warning(f"Dropping non-Huawei partial bytes: {buf.hex()}")
                return

            expected = struct.unpack(">H", buf[1:3])[0]
            frame_len = expected + 5
            if len(buf) < frame_len:
                self._rx_partial = buf
                return

            frame = buf[:frame_len]
            buf = buf[frame_len:]
            self._handle_complete_frame(frame)

    def _handle_complete_frame(self, raw: bytes):
        slice_b = raw[3]

        if slice_b == 0x00:
            # Complete unsliced packet — push to queue
            self._rx_queue.append(raw)
            self._loop.call_soon_threadsafe(self._event.set)

        elif slice_b == 0x01:
            # First slice — extract sID+cID then start buffer
            payload      = raw[5:-2]   # after magic,len,slice,flag; before CRC
            self._rx_svc = payload[0]
            self._rx_cmd = payload[1]
            self._rx_buf = payload[2:]

        elif slice_b == 0x02:
            # Middle slice
            if self._rx_buf is not None:
                self._rx_buf += raw[5:-2]

        elif slice_b == 0x03:
            # Last slice — complete reassembly, push to queue
            if self._rx_buf is not None:
                self._rx_buf += raw[5:-2]
                # Reconstruct as a fake unsliced frame so parse_frame works
                body  = bytes([self._rx_svc, self._rx_cmd]) + self._rx_buf
                hdr   = bytes([MAGIC]) + struct.pack(">H", len(body)+1) + b"\x00" + body
                self._rx_queue.append(hdr + struct.pack(">H", crc16(hdr)))
                self._rx_buf = None
                self._loop.call_soon_threadsafe(self._event.set)

    async def _send(self, svc: int, cmd: int, tlv: bytes):
        frames = _make_sliced(svc, cmd, tlv, self.slice_size)
        if len(frames) == 1 and frames[0][3] == 0x01:
            # Would be sliced but only one frame — use unsliced format instead
            frames = [_make_unsliced(svc, cmd, tlv)]
        for f in frames:
            logger.debug(f"→ {f.hex()}")
            await self.client.write_gatt_char(GATT_WRITE, bytearray(f))
            if len(frames) > 1:
                await asyncio.sleep(0.02)   # small gap between slices

    def _decrypt_and_log(self, tlvs: dict, label: str,
                          pin_code: bytes = b"", rand_self: bytes = b"", seed: bytes = b"") -> tuple:
        """Try to decrypt a CryptoTag-wrapped notification.
        Returns (True, key, nonce, aad) on success, (False, None, None, None) otherwise."""
        nonce      = tlvs.get(0x7D, b"")
        ciphertext = tlvs.get(0x7E, b"")
        logger.debug(f"  [{label}] nonce={nonce.hex()} ciphertext({len(ciphertext)}B)={ciphertext.hex()}")

        # Build candidate keys from all known pre-shared material
        candidates = []
        for k in DIGEST_SECRETS.values():
            candidates.append(("DIGEST_SECRET", k))
        if self.secret_key:
            candidates.append(("secret_key", self.secret_key))
        if pin_code:
            pin_hex = pin_code.hex().encode("utf-8")
            pin_key = sha256(pin_hex)
            candidates.append(("sha256(pin_hex)", pin_key))
            candidates.append(("sha256(pin_hex)[:16]", pin_key[:16]))
            for ds in DIGEST_SECRETS.values():
                candidates.append(("hmac(digest,pin_hex)", hmac_sha256(ds, pin_hex)))
            if seed:
                # PSK — the key both sides can compute from PIN + seed we sent in step 1
                psk = hmac_sha256(pin_key, seed)
                candidates.append(("PSK=hmac(sha256(pin),seed)", psk))
                candidates.append(("PSK[:16]", psk[:16]))
                # Reversed HMAC order — some implementations swap key/msg
                psk_rev = hmac_sha256(seed, pin_key)
                candidates.append(("PSK_rev=hmac(seed,sha256(pin))", psk_rev))
                candidates.append(("PSK_rev[:16]", psk_rev[:16]))
            if self.server_nonce:
                candidates.append(("hmac(pin_key,serverNonce)", hmac_sha256(pin_key, self.server_nonce)))
        if rand_self:
            for ds in DIGEST_SECRETS.values():
                candidates.append(("hmac(digest,rand_self)", hmac_sha256(ds, rand_self)))
        if seed:
            for ds in DIGEST_SECRETS.values():
                candidates.append(("hmac(digest,seed)", hmac_sha256(ds, seed)))
        if self.server_nonce:
            # server_nonce is 16 bytes — valid AES-128 key; it's also static across runs
            candidates.append(("server_nonce", self.server_nonce))
            candidates.append(("sha256(serverNonce)", sha256(self.server_nonce)))
            candidates.append(("sha256(serverNonce)[:16]", sha256(self.server_nonce)[:16]))
            for ds in DIGEST_SECRETS.values():
                candidates.append(("hmac(digest,serverNonce)", hmac_sha256(ds, self.server_nonce)))
        # android_id derived keys
        if self.android_id:
            android_id_bytes = self.android_id.encode("utf-8")
            candidates.append(("sha256(androidId)", sha256(android_id_bytes)))
            candidates.append(("sha256(androidId)[:16]", sha256(android_id_bytes)[:16]))
            for ds in DIGEST_SECRETS.values():
                candidates.append(("hmac(digest,androidId)", hmac_sha256(ds, android_id_bytes)))
        # band_device_id derived keys
        if self.band_device_id:
            try:
                bid = bytes.fromhex(self.band_device_id)
                candidates.append(("sha256(bandDevId)", sha256(bid)))
                candidates.append(("sha256(bandDevId)[:16]", sha256(bid)[:16]))
                candidates.append(("bandDevId[:16]", bid[:16]))
                candidates.append(("bandDevId[16:32]", bid[16:32]))
                for ds in DIGEST_SECRETS.values():
                    candidates.append(("hmac(digest,bandDevId)", hmac_sha256(ds, bid)))
            except Exception:
                pass
        # Key derived from band MAC (Gadgetbridge create_secret_key style)
        try:
            mac_key = create_secret_key(self.auth_version, self.device_mac)
            candidates.append(("create_secret_key(bandMAC)", mac_key))
            # Also try with client_mac (Gadgetbridge uses phone's MAC, not band's MAC)
            mac_key2 = create_secret_key(self.auth_version, self.client_mac)
            candidates.append(("create_secret_key(clientMAC)", mac_key2))
            # Gadgetbridge may use lowercase MAC internally
            mac_key3 = create_secret_key(self.auth_version, self.device_mac.lower())
            candidates.append(("create_secret_key(bandMAC_lower)", mac_key3))
            mac_key4 = create_secret_key(self.auth_version, self.client_mac.lower())
            candidates.append(("create_secret_key(clientMAC_lower)", mac_key4))
            # Try auth_version=0 and 2 variants as well
            for av in (0, 2, 3):
                try:
                    candidates.append((f"create_secret_key(v{av},bandMAC)",
                                       create_secret_key(av, self.device_mac)))
                    candidates.append((f"create_secret_key(v{av},clientMAC)",
                                       create_secret_key(av, self.client_mac)))
                except Exception:
                    pass
        except Exception:
            pass

        # Session key hypothesis: band sends nonce = rand_peer (its isoSalt).
        # Both sides can compute session_key = HKDF(PSK, rand_self + nonce, info, 32).
        # This lets the band encrypt svc=0x37 with session_key without needing a prior exchange.
        if pin_code and seed and rand_self and len(nonce) >= 16:
            try:
                pin_hex_sk = pin_code.hex().encode("utf-8")
                psk_sk     = hmac_sha256(sha256(pin_hex_sk), seed)
                rand_peer_cand = nonce[:16]
                for sk_info in (b"hichain_iso_session_key", b"hichain_session_key",
                                b"hichain_iso_notification_key", b"hichain_return_key"):
                    sk = hkdf_sha256(psk_sk, rand_self + rand_peer_cand, sk_info, 32)
                    candidates.append((f"sessionKey(nonce_as_randPeer,info={sk_info})", sk))
                    candidates.append((f"sessionKey[:16](nonce_as_randPeer,info={sk_info})", sk[:16]))
            except Exception:
                pass

        # Additional direct combinations
        if pin_code and seed:
            pin_hex2 = pin_code.hex().encode("utf-8")
            candidates.append(("hmac(pin_code,seed)", hmac_sha256(pin_code, seed)))
            candidates.append(("sha256(seed+pin_hex)", sha256(seed + pin_hex2)))
            candidates.append(("sha256(pin_hex+seed)", sha256(pin_hex2 + seed)))
        if rand_self and seed:
            candidates.append(("sha256(rand_self+seed)", sha256(rand_self + seed)))
            candidates.append(("sha256(seed+rand_self)", sha256(seed + rand_self)))
        if self.server_nonce and seed:
            candidates.append(("sha256(serverNonce+seed)", sha256(self.server_nonce + seed)))
        if pin_code and seed and self.server_nonce:
            pin_hex3 = pin_code.hex().encode("utf-8")
            psk3 = hmac_sha256(sha256(pin_hex3), seed)
            for sk_info in (b"hichain_iso_session_key", b"hichain_iso_notification_key"):
                sk = hkdf_sha256(psk3, self.server_nonce, sk_info, 32)
                candidates.append((f"HKDF(psk,serverNonce,{sk_info})", sk))

        # Try each candidate with 12-byte and 16-byte nonce variants, plus a set of AADs.
        # svc=0x37 and svc=0x01/cmd=0x3D AADs added — Huawei may use [svc, cmd] as AAD.
        aad_candidates = [
            None,
            bytes([0x25, 0x03]),
            bytes([0x01, 0x38]),
            bytes([0x37, 0x01]),
            bytes([0x01, 0x3D]),
            bytes([0x01, 0x28]),
        ]
        for name, key in candidates:
            if len(key) not in (16, 24, 32):
                continue
            for n in (nonce[:12], nonce):
                if len(n) == 0:
                    continue
                for aad in aad_candidates:
                    try:
                        plain = aes_gcm_dec(ciphertext, key, n, aad)
                        logger.info(f"  [{label}] DECRYPTED key={name}({key.hex()[:8]}…) "
                                    f"nonce_len={len(n)} aad={aad!r} → {plain.hex()} {plain!r}")
                        return True, key, n, aad
                    except Exception:
                        pass
        logger.debug(f"  [{label}] Could not decrypt with any known key")
        return False, None, None, None

    async def _recv(self, exp_svc: int, exp_cmd: int, timeout: float = 15.0,
                    pin_code: bytes = b"", rand_self: bytes = b"", seed: bytes = b"") -> dict:
        """Wait for a specific svc/cmd packet, silently skipping unrelated notifications.

        Uses a queue so rapid bursts of notifications are never overwritten.
        Gadgetbridge ignores svc=0x25 (MusicControl) and svc=0x37 (DataSync) during
        first auth (secretKey==None → handleResponse returns early). We do the same.
        """
        deadline = asyncio.get_running_loop().time() + timeout
        while True:
            # Drain any buffered notifications before waiting
            while self._rx_queue:
                raw = self._rx_queue.popleft()
                svc, cmd, tlvs = parse_frame(raw)
                if (svc, cmd) == (exp_svc, exp_cmd):
                    return tlvs
                if svc == SVC_DEV and cmd == CMD_PHONE_INFO:
                    await self._handle_phone_info_request(tlvs)
                    continue
                await self._handle_unsolicited(svc, cmd, tlvs, pin_code, rand_self, seed)

            remaining = deadline - asyncio.get_running_loop().time()
            if remaining <= 0:
                raise asyncio.TimeoutError(f"Timed out waiting for {exp_svc:#x}/{exp_cmd:#x}")

            # Clear event THEN re-check queue to avoid missing a notification
            # that arrived between the drain loop and this clear.
            self._event.clear()
            if self._rx_queue:
                continue
            await asyncio.wait_for(self._event.wait(), timeout=remaining)

    async def _recv_payload(self, exp_svc: int, exp_cmd: int,
                            timeout: float = 15.0) -> bytes:
        """Wait for a packet and return its raw payload bytes.

        File block packets can be raw binary rather than TLV, so parse_frame()
        is too strict for them.
        """
        deadline = asyncio.get_running_loop().time() + timeout
        while True:
            while self._rx_queue:
                raw = self._rx_queue.popleft()
                svc, cmd, payload = frame_payload(raw)
                if (svc, cmd) == (exp_svc, exp_cmd):
                    return payload
                try:
                    tlvs = tlv_dec(payload)
                except Exception:
                    logger.debug(f"  Skipping non-TLV packet svc={svc:#x} cmd={cmd:#x}")
                    continue
                if svc == SVC_DEV and cmd == CMD_PHONE_INFO:
                    await self._handle_phone_info_request(tlvs)
                else:
                    await self._handle_unsolicited(svc, cmd, tlvs, b"", b"", b"")

            remaining = deadline - asyncio.get_running_loop().time()
            if remaining <= 0:
                raise asyncio.TimeoutError(f"Timed out waiting for raw {exp_svc:#x}/{exp_cmd:#x}")
            self._event.clear()
            if self._rx_queue:
                continue
            await asyncio.wait_for(self._event.wait(), timeout=remaining)

    async def _recv_any(self, expected: set[tuple[int, int]],
                        timeout: float = 15.0) -> tuple[int, int, dict]:
        deadline = asyncio.get_running_loop().time() + timeout
        while True:
            while self._rx_queue:
                raw = self._rx_queue.popleft()
                svc, cmd, tlvs = parse_frame(raw)
                if (svc, cmd) in expected:
                    return svc, cmd, tlvs
                if svc == SVC_DEV and cmd == CMD_PHONE_INFO:
                    await self._handle_phone_info_request(tlvs)
                else:
                    await self._handle_unsolicited(svc, cmd, tlvs, b"", b"", b"")

            remaining = deadline - asyncio.get_running_loop().time()
            if remaining <= 0:
                raise asyncio.TimeoutError(f"Timed out waiting for one of {expected}")
            self._event.clear()
            if self._rx_queue:
                continue
            await asyncio.wait_for(self._event.wait(), timeout=remaining)

    async def _recv_any_encrypted(self, expected: set[tuple[int, int]],
                                  timeout: float = 15.0) -> tuple[int, int, dict]:
        svc, cmd, tlvs = await self._recv_any(expected, timeout=timeout)
        return svc, cmd, self._decrypt_transaction_tlvs(tlvs)

    async def _handle_unsolicited(self, svc: int, cmd: int, tlvs: dict,
                                  pin_code: bytes, rand_self: bytes, seed: bytes):
        """Handle or log an unsolicited packet."""
        if svc == SVC_DEV and cmd == 0x38:
            await self._handle_permission_check(tlvs)
        elif svc == SVC_DATASYNC:
            if not await self._handle_datasync(cmd, tlvs):
                logger.info(f"  svc={svc:#x}/cmd={cmd:#x} raw tlvs: { {hex(k): v.hex() for k,v in tlvs.items()} }")
                self._decrypt_and_log(tlvs, f"svc={svc:#x}/cmd={cmd:#x}", pin_code, rand_self, seed)
        elif svc == 0x25 and cmd == 0x03:
            # MusicControl.Control — band sends this as a background update.
            # Gadgetbridge ignores it entirely during first auth (secretKey==None).
            logger.debug(f"  svc=0x25/cmd=0x03 (MusicControl) — skipping, "
                         f"tlvs={ {hex(k): v.hex() for k,v in tlvs.items()} }")
        elif svc == SVC_WEATHER and cmd == WEATHER_DEVICE_REQUEST:
            decoded = self._decrypt_or_raw_tlvs(tlvs)
            status = self._tlv_int(decoded.get(0x7F, b""), default=-1)
            logger.info(f"  Weather device request: { {hex(k): v.hex() for k, v in decoded.items()} }")
            if status == RESULT_WEATHER_DEVICE_REQUEST and self._last_weather_payload and not self._weather_push_active:
                await self.send_weather_update(self._last_weather_payload)
            else:
                await self._weather_device_request_ack()
        elif svc == SVC_FILE_DOWNLOAD and cmd == FILE_CMD_INCOMING_INIT:
            init = self._parse_incoming_file_init(tlvs)
            self._pending_file_inits.append(init)
            logger.info(
                "  Incoming file init: "
                f"name={init['filename']!r} type={self._file_type_name(init['file_type'])} "
                f"id={init['file_id']:#x} size={init['file_size']}"
            )
        elif svc == SVC_FILE_UPLOAD:
            try:
                plain = self._decrypt_transaction_tlvs(tlvs)
                logger.info(
                    f"  FileUpload unsolicited cmd={cmd:#x} "
                    f"tlvs={ {hex(k): v.hex() for k, v in plain.items()} }"
                )
            except Exception as e:
                logger.debug(f"  FileUpload unsolicited parse failed cmd={cmd:#x}: {e!r}")
        elif svc == SVC_HR_RRI_TEST and cmd == CMD_HR_RRI_OPEN_CLOSE:
            data = self._decrypt_or_raw_tlvs(tlvs)
            event = {
                "timestamp": int(time.time()),
                "svc": svc,
                "cmd": cmd,
                "tlvs": {hex(k): v.hex() for k, v in data.items()},
            }
            if 0x7F in data:
                event["status"] = self._tlv_int(data[0x7F])
            self._live_rri_events.append(event)
            logger.info(f"  Live RRI open/close status: {event}")
        elif svc == SVC_HR_RRI_TEST and cmd == CMD_HR_REALTIME_DATA:
            samples = self._parse_realtime_hr_packet(tlvs)
            self._live_hr_samples.extend(samples)
            logger.info(f"  Realtime HR packet: {len(samples)} samples, total={len(self._live_hr_samples)}")
        elif svc == SVC_HR_RRI_TEST and cmd == CMD_HR_RRI_DATA:
            samples = self._parse_live_rri_packet(tlvs)
            self._live_rri_samples.extend(samples)
            logger.debug(f"  Live RRI packet: {len(samples)} samples, total={len(self._live_rri_samples)}")
        elif svc == SVC_HR_RRI_TEST:
            data = self._decrypt_or_raw_tlvs(tlvs)
            event = {
                "timestamp": int(time.time()),
                "svc": svc,
                "cmd": cmd,
                "tlvs": {hex(k): v.hex() for k, v in data.items()},
            }
            if 0x7F in data:
                event["status"] = self._tlv_int(data[0x7F])
            self._live_rri_events.append(event)
            logger.info(f"  Live HR/RRI unsolicited: {event}")
        elif svc == 0x37 or (svc == SVC_DEV and cmd == 0x3D):
            # DataSync or encrypted dev packet — log but don't respond
            logger.info(f"  svc={svc:#x}/cmd={cmd:#x} raw tlvs: { {hex(k): v.hex() for k,v in tlvs.items()} }")
            self._decrypt_and_log(tlvs, f"svc={svc:#x}/cmd={cmd:#x}", pin_code, rand_self, seed)
        else:
            logger.debug(f"  Skipping unsolicited packet svc={svc:#x} cmd={cmd:#x}")

    async def _handle_permission_check(self, tlvs: dict):
        try:
            decoded = self._decrypt_transaction_tlvs(tlvs)
        except Exception as e:
            logger.warning(f"  PermissionCheck decrypt failed: {e!r} raw={tlvs}")
            return
        if 0x7F in decoded:
            status = self._tlv_int(decoded[0x7F])
            logger.info(f"  PermissionCheck status response: {status:#x}")
            return
        permission = self._tlv_int(decoded.get(0x01, b""))
        if permission <= 0:
            logger.warning(f"  PermissionCheck missing permission: { {hex(k): v.hex() for k, v in decoded.items()} }")
            return
        reply = tlv_enc(0x01, struct.pack(">H", permission)) + tlv_enc(0x02, b"\x00\x00")
        logger.info(f"  PermissionCheck permission={permission} -> status=0")
        await self._send_encrypted_no_wait(SVC_DEV, 0x38, reply)

    async def _respond_to_notification(self, svc: int, cmd: int, key: bytes):
        """Send an encrypted ACK to a band notification using the given key."""
        nonce_out = secrets.token_bytes(12)
        enc = aes_gcm_enc(b"", key, nonce_out, None)
        resp_tlv = tlv_enc(0x7C, b"\x01") + tlv_enc(0x7D, nonce_out) + tlv_enc(0x7E, enc)
        logger.debug(f"  → ACK svc={svc:#x}/cmd={cmd:#x} key={key.hex()[:8]}…")
        await self._send(svc, cmd, resp_tlv)

    async def _transact(self, svc: int, cmd: int, tlv: bytes,
                        timeout: float = 15.0,
                        pin_code: bytes = b"", rand_self: bytes = b"", seed: bytes = b"") -> dict:
        await self._send(svc, cmd, tlv)
        return await self._recv(svc, cmd, timeout, pin_code=pin_code, rand_self=rand_self, seed=seed)

    def _uses_gcm(self) -> bool:
        # Gadgetbridge uses GCM for encryptMethod=1, and always for deviceSupportType=4.
        return self.enc_method == 0x01 or self.dev_support_type == 0x04

    def _next_crypto_iv(self) -> bytes:
        if self.dev_support_type == 0x04:
            return secrets.token_bytes(16)
        self.enc_counter = 1 if self.enc_counter == 0xFFFFFFFF else self.enc_counter + 1
        return secrets.token_bytes(12) + struct.pack(">I", self.enc_counter)

    def _encrypt_transaction_tlv(self, plain_tlv: bytes) -> bytes:
        if not self.secret_key:
            raise RuntimeError("Cannot encrypt transaction before HiChain secret key is available")

        iv = self._next_crypto_iv()
        if self._uses_gcm():
            cipher = aes_gcm_enc(plain_tlv, self.secret_key, iv, None)
        else:
            cipher = aes_cbc_enc(plain_tlv, self.secret_key, iv)

        logger.debug(f"  encrypt tx iv={iv.hex()} plain={plain_tlv.hex()} cipher={cipher.hex()}")
        return tlv_enc(0x7C, b"\x01") + tlv_enc(0x7D, iv) + tlv_enc(0x7E, cipher)

    def _decrypt_transaction_tlvs(self, tlvs: dict) -> dict:
        plain = self._decrypt_transaction_payload(tlvs)
        decrypted = tlv_dec(plain)
        logger.debug(f"  decrypt rx plain={plain.hex()} "
                     f"tlvs={ {hex(k): v.hex() for k, v in decrypted.items()} }")
        return decrypted

    def _decrypt_or_raw_tlvs(self, tlvs: dict) -> dict:
        try:
            return self._decrypt_transaction_tlvs(tlvs)
        except Exception:
            return tlvs

    def _decrypt_transaction_payload(self, tlvs: dict) -> bytes:
        if not (self.secret_key and 0x7C in tlvs and 0x7D in tlvs and 0x7E in tlvs):
            return b"".join(tlv_enc(k, v) for k, v in tlvs.items())

        iv = tlvs[0x7D]
        cipher = tlvs[0x7E]
        if self._uses_gcm():
            plain = aes_gcm_dec(cipher, self.secret_key, iv, None)
        else:
            plain = aes_cbc_dec(cipher, self.secret_key, iv)

        logger.debug(f"  decrypt rx iv={iv.hex()} plain={plain.hex()}")
        return plain

    async def _transact_encrypted(self, svc: int, cmd: int, tlv: bytes,
                                  timeout: float = 15.0) -> dict:
        wrapped = self._encrypt_transaction_tlv(tlv)
        response = await self._transact(svc, cmd, wrapped, timeout=timeout)
        return self._decrypt_transaction_tlvs(response)

    async def _send_encrypted_no_wait(self, svc: int, cmd: int, tlv: bytes):
        await self._send(svc, cmd, self._encrypt_transaction_tlv(tlv))

    @staticmethod
    def _datasync_config_item(config_id: int, action: int = None,
                              data: bytes = None, unknown: int = None) -> bytes:
        item = tlv_enc(0x05, struct.pack(">I", config_id))
        if action is not None:
            item += tlv_enc(0x06, bytes([action & 0xFF]))
        if data is not None:
            item += tlv_enc(0x07, data)
        if unknown is not None:
            item += tlv_enc(0x08, struct.pack(">Q", unknown))
        return item

    @staticmethod
    def _datasync_config_command_tlv(src_package: str, dst_package: str,
                                     config_items: list[bytes]) -> bytes:
        tlv = (
            tlv_enc(0x01, src_package.encode("utf-8")) +
            tlv_enc(0x02, dst_package.encode("utf-8"))
        )
        if config_items:
            nested = b"".join(tlv_enc(0x84, item) for item in config_items)
            tlv += tlv_enc(0x83, nested)
        return tlv

    @staticmethod
    def _datasync_parse_config_items(container: bytes) -> list[dict]:
        out = []
        for tag, payload in tlv_items(container):
            if tag != 0x84:
                continue
            entry = {}
            for item_tag, value in tlv_items(payload):
                if item_tag == 0x05:
                    entry["config_id"] = int.from_bytes(value, "big")
                elif item_tag == 0x06:
                    entry["action"] = value[0] if value else None
                elif item_tag == 0x07:
                    entry["data"] = value
                elif item_tag == 0x08:
                    entry["unknown"] = int.from_bytes(value, "big")
            out.append(entry)
        return out

    async def _handle_datasync(self, cmd: int, tlvs: dict) -> bool:
        try:
            plain = self._decrypt_transaction_payload(tlvs)
            items = tlv_items(plain)
        except Exception as e:
            logger.debug(f"  DataSync decrypt/parse failed: {e!r}")
            return False

        parsed = {tag: value for tag, value in items}
        src = self._tlv_str(parsed.get(0x01, b""))
        dst = self._tlv_str(parsed.get(0x02, b""))
        logger.info(f"  DataSync cmd={cmd:#x} src={src!r} dst={dst!r} plain={plain.hex()}")

        if cmd == DATASYNC_CONFIG_CMD:
            configs = []
            if 0x83 in parsed:
                configs = self._datasync_parse_config_items(parsed[0x83])
            logger.info(f"  DataSync config items={configs}")
            for config in configs:
                if (
                    src == DATASYNC_FEATURE_SRC
                    and dst == DATASYNC_FEATURE_DST
                    and config.get("config_id") == DATASYNC_FEATURE_CONFIG_ID
                    and config.get("action") == 2
                ):
                    item = self._datasync_config_item(
                        DATASYNC_FEATURE_CONFIG_ID,
                        action=2,
                        data=b"01",
                    )
                    reply = self._datasync_config_command_tlv(
                        DATASYNC_FEATURE_DST,
                        DATASYNC_FEATURE_SRC,
                        [item],
                    )
                    logger.info("  DataSync FeatureManager request -> replying with feature config ACK")
                    await self._send_encrypted_no_wait(SVC_DATASYNC, DATASYNC_CONFIG_CMD, reply)
                    self._feature_config_requested = True
                    return True
            return True

        if cmd in (DATASYNC_EVENT_CMD, DATASYNC_DATA_CMD, DATASYNC_DICT_DATA_CMD):
            return True

        return False

    @staticmethod
    def _feature_file_content(country_code: str) -> bytes:
        features = [
            {
                "devPackageName": "com.huawei.health.pwvdetection",
                "support": 1,
                "extInfo": {"medical": False},
            },
            {
                "devPackageName": "com.huawei.health.bloodpressure",
                "support": 1,
                "extInfo": {"ABPM": [135, 85, 120, 70, 130, 80], "local": 2},
            },
            {
                "devPackageName": "com.huawei.watch.health.ecganalysis",
                "support": 1,
                "extInfo": {"medical": False},
            },
            {
                "devPackageName": "com.huawei.health.healthcheck",
                "support": 1,
                "extInfo": {"checkItem": 23},
            },
            {"devPackageName": "com.huawei.hmos.watch.emotional", "support": 1},
            {
                "devPackageName": "com.huawei.hmos.watch.plateaucare",
                "support": 1,
                "extInfo": {"medical": False},
            },
            {
                "devPackageName": "com.huawei.watch.health.arrhythmia",
                "support": 1,
                "extInfo": {"medical": False},
            },
        ]
        payload = {
            "featureList": features,
            "country": country_code,
            "ver": 1,
            "name": f"feature_{country_code}_device",
        }
        return json.dumps(payload, separators=(",", ":")).encode("utf-8")

    @staticmethod
    def _feature_notify_content(country_code: str) -> bytes:
        payload = {
            "filterCondition": [{"filterCountry": country_code}],
            "configName": "com.huawei.health_deviceFeature_config",
            "fileData": [{
                "version": 1,
                "fileName": f"feature_{country_code}_device.txt",
            }],
        }
        return json.dumps(payload, separators=(",", ":")).encode("utf-8")

    async def _send_upload_chunk(self, file_id: int, chunk: bytes, upload_position: int,
                                 unit_size: int, encrypt: bool):
        offset = upload_position
        index = 0
        pos = 0
        while pos < len(chunk):
            part = chunk[pos:pos + unit_size]
            payload = bytes([file_id & 0xFF, index & 0xFF]) + struct.pack(">I", offset) + part
            if encrypt:
                payload = self._encrypt_transaction_tlv(payload)
            await self._send(SVC_FILE_UPLOAD, UPLOAD_CMD_CHUNK, payload)
            pos += len(part)
            offset += len(part)
            index += 1

    async def upload_file_28(self, file_name: str, file_type: int, payload: bytes,
                             timeout: float = 20.0):
        file_hash = hashlib.sha256(payload).digest()
        logger.info(
            f"Feature upload: info name={file_name!r} type={file_type:#x} "
            f"size={len(payload)} sha256={file_hash.hex()}"
        )
        info_tlv = (
            tlv_enc(0x01, file_name.encode("utf-8")) +
            tlv_enc(0x02, struct.pack(">I", len(payload))) +
            tlv_enc(0x03, bytes([file_type & 0xFF]))
        )
        info = await self._transact_encrypted(SVC_FILE_UPLOAD, UPLOAD_CMD_INFO, info_tlv, timeout=timeout)
        info_code = self._tlv_int(info.get(0x7F, b""))
        if info_code and info_code != DATASYNC_REPLY_OK:
            raise RuntimeError(f"feature upload info failed: {info_code}")

        _, _, hash_req = await self._recv_any_encrypted({(SVC_FILE_UPLOAD, UPLOAD_CMD_HASH)}, timeout=timeout)
        file_id = hash_req.get(0x01, b"\x00")[0]
        logger.info(f"Feature upload: file id={file_id:#x}, sending hash")
        hash_tlv = tlv_enc(0x01, bytes([file_id])) + tlv_enc(0x03, file_hash)
        await self._send_encrypted_no_wait(SVC_FILE_UPLOAD, UPLOAD_CMD_HASH, hash_tlv)

        _, _, consult = await self._recv_any_encrypted({(SVC_FILE_UPLOAD, UPLOAD_CMD_CONSULT)}, timeout=timeout)
        file_id = consult.get(0x01, bytes([file_id]))[0]
        unit_size = self._tlv_int(consult.get(0x05, b""), default=512) or 512
        no_encrypt = consult.get(0x09, b"\x00")[0] if consult.get(0x09) else 0
        logger.info(
            f"Feature upload: consult file_id={file_id:#x} unit={unit_size} "
            f"no_encrypt={no_encrypt} raw={ {hex(k): v.hex() for k, v in consult.items()} }"
        )
        ack_tlv = (
            tlv_enc(0x7F, struct.pack(">I", DATASYNC_REPLY_OK)) +
            tlv_enc(0x01, bytes([file_id]))
        )
        if no_encrypt == 1:
            ack_tlv += tlv_enc(0x09, b"\x01")
        await self._send_encrypted_no_wait(SVC_FILE_UPLOAD, UPLOAD_CMD_CONSULT, ack_tlv)

        while True:
            _, cmd, data = await self._recv_any_encrypted(
                {
                    (SVC_FILE_UPLOAD, UPLOAD_CMD_NEXT_CHUNK),
                    (SVC_FILE_UPLOAD, UPLOAD_CMD_RESULT),
                    (SVC_FILE_UPLOAD, UPLOAD_CMD_DEVICE_RESPONSE),
                },
                timeout=timeout,
            )
            if cmd == UPLOAD_CMD_NEXT_CHUNK:
                uploaded = self._tlv_int(data.get(0x02, b""))
                next_size = self._tlv_int(data.get(0x03, b""))
                if uploaded >= len(payload):
                    logger.info("Feature upload: device requested chunk after full payload; waiting for result")
                    continue
                if next_size <= 0:
                    raise RuntimeError(f"feature upload invalid next chunk size: {next_size}")
                chunk = payload[uploaded:uploaded + next_size]
                logger.info(f"Feature upload: chunk offset={uploaded} size={len(chunk)}")
                await self._send_upload_chunk(
                    file_id,
                    chunk,
                    uploaded,
                    unit_size,
                    encrypt=(no_encrypt == 0),
                )
            elif cmd == UPLOAD_CMD_RESULT:
                status = data.get(0x02, b"\x00")[0] if data.get(0x02) else 0
                logger.info(f"Feature upload: result status={status}, sending completion ACK")
                done_tlv = (
                    tlv_enc(0x7F, struct.pack(">I", DATASYNC_REPLY_OK)) +
                    tlv_enc(0x01, bytes([file_id]))
                )
                await self._send_encrypted_no_wait(SVC_FILE_UPLOAD, UPLOAD_CMD_RESULT, done_tlv)
                return
            else:
                code = self._tlv_int(data.get(0x7F, b""))
                raise RuntimeError(f"feature upload device error: {code}")

    async def send_feature_config_file_if_requested(self):
        if not self._feature_config_requested:
            return
        if os.getenv("BAND10_FEATURE_UPLOAD", "1") == "0":
            logger.info("Feature upload skipped by BAND10_FEATURE_UPLOAD=0")
            return
        country_code = os.getenv("BAND10_COUNTRY", "IN").upper()
        file_name = f"feature_{country_code}_device.txt"
        payload = self._feature_file_content(country_code)
        save_binary_artifact(file_name, payload)
        await self.upload_file_28(file_name, 0x0F, payload)
        notify = self._feature_notify_content(country_code)
        item = self._datasync_config_item(
            DATASYNC_FEATURE_CONFIG_ID,
            action=1,
            data=notify,
        )
        reply = self._datasync_config_command_tlv(
            DATASYNC_FEATURE_DST,
            DATASYNC_FEATURE_SRC,
            [item],
        )
        logger.info(f"Feature upload: notifying device with {notify.decode('utf-8')}")
        await self._send_encrypted_no_wait(SVC_DATASYNC, DATASYNC_CONFIG_CMD, reply)

    @staticmethod
    def _phone_info_response_tlv(requested_tags: bytes) -> bytes:
        out = b""
        for tag in requested_tags:
            if tag in (0x00, 0x0F):
                continue
            if tag in (0x02, 0x04, 0x15):
                out += tlv_enc(tag)
            elif tag == 0x08:
                out += tlv_enc(tag, b"14")
            elif tag == 0x11:
                out += tlv_enc(tag, struct.pack(">I", 1600008300))
            else:
                out += tlv_enc(tag, b"\x00")
        return out

    async def _handle_phone_info_request(self, tlvs: dict):
        decoded = self._decrypt_transaction_tlvs(tlvs)
        if 0x7F in decoded and self._tlv_int(decoded[0x7F]) == 0x0186A0:
            logger.debug("  PhoneInfo ACK received")
            return
        requested = bytes(tag for tag, _value in tlv_items(b"".join(tlv_enc(k, v) for k, v in decoded.items())))
        if not requested:
            requested = bytes(decoded.keys())
        reply = self._phone_info_response_tlv(requested)
        logger.info(f"  PhoneInfo requested tags={requested.hex()} -> replying")
        await self._send_encrypted_no_wait(SVC_DEV, CMD_PHONE_INFO, reply)

    def _next_p2p_sequence(self) -> int:
        self.p2p_sequence = (self.p2p_sequence + 1) % (0x7FFF - 1)
        if self.p2p_sequence == 0:
            self.p2p_sequence = 1
        return self.p2p_sequence

    @staticmethod
    def _tlv_int(value: bytes, default: int = 0) -> int:
        return int.from_bytes(value, "big") if value else default

    @staticmethod
    def _tlv_str(value: bytes) -> str:
        return value.decode("utf-8", errors="replace")

    def _parse_p2p_response(self, tlvs: dict) -> dict:
        return {
            "cmd_id": tlvs.get(0x01, b"\x00")[0],
            "sequence": self._tlv_int(tlvs.get(0x02, b"\x00\x00")),
            "src_package": self._tlv_str(tlvs.get(0x03, b"")),
            "dst_package": self._tlv_str(tlvs.get(0x04, b"")),
            "src_fingerprint": self._tlv_str(tlvs.get(0x05, b"")) if 0x05 in tlvs else None,
            "dst_fingerprint": self._tlv_str(tlvs.get(0x06, b"")) if 0x06 in tlvs else None,
            "data": tlvs.get(0x07, b""),
            "code": self._tlv_int(tlvs.get(0x08, b"\x00")),
        }

    def _build_p2p_tlv(self, cmd_id: int, src_package: str, dst_package: str,
                       src_fingerprint: str = None, dst_fingerprint: str = None,
                       data: bytes = None, code: int = None,
                       sequence: int = None) -> tuple[int, bytes]:
        seq = self._next_p2p_sequence() if sequence is None else sequence
        tlv = (
            tlv_enc(0x01, bytes([cmd_id])) +
            tlv_enc(0x02, struct.pack(">H", seq)) +
            tlv_enc(0x03, src_package.encode("utf-8")) +
            tlv_enc(0x04, dst_package.encode("utf-8"))
        )
        if cmd_id == 0x02:
            tlv += tlv_enc(0x05, (src_fingerprint or "").encode("utf-8"))
            tlv += tlv_enc(0x06, (dst_fingerprint or "").encode("utf-8"))
        if data:
            tlv += tlv_enc(0x07, data)
        if cmd_id == 0x03 and code is not None:
            tlv += tlv_enc(0x08, struct.pack(">I", code))
        return seq, tlv

    async def p2p_command(self, cmd_id: int, src_package: str, dst_package: str,
                          src_fingerprint: str = None, dst_fingerprint: str = None,
                          data: bytes = None, code: int = None,
                          timeout: float = 20.0) -> dict:
        seq = await self.send_p2p_command(
            cmd_id, src_package, dst_package, src_fingerprint, dst_fingerprint, data, code
        )
        parsed = await self.wait_for_p2p_sequence(seq, timeout=timeout)
        logger.debug(f"  P2P response: {parsed | {'data': parsed['data'].hex()}}")
        return parsed

    async def send_p2p_command(self, cmd_id: int, src_package: str, dst_package: str,
                               src_fingerprint: str = None, dst_fingerprint: str = None,
                               data: bytes = None, code: int = None,
                               sequence: int = None) -> int:
        seq, tlv = self._build_p2p_tlv(
            cmd_id, src_package, dst_package, src_fingerprint, dst_fingerprint, data, code, sequence
        )
        await self._send(SVC_P2P, 0x01, self._encrypt_transaction_tlv(tlv))
        return seq

    async def _send_p2p_ack(self, response: dict, code: int = 0xCF):
        await self.send_p2p_command(
            0x03,
            response["dst_package"],
            response["src_package"],
            code=code,
            sequence=response["sequence"],
        )

    async def wait_for_p2p_sequence(self, sequence: int, timeout: float = 20.0) -> dict:
        deadline = asyncio.get_running_loop().time() + timeout
        while True:
            remaining = deadline - asyncio.get_running_loop().time()
            if remaining <= 0:
                raise asyncio.TimeoutError(f"Timed out waiting for P2P sequence {sequence}")

            tlvs = await self._recv(SVC_P2P, 0x01, timeout=remaining)
            decoded = self._decrypt_transaction_tlvs(tlvs)
            parsed = self._parse_p2p_response(decoded)
            logger.info(f"P2P packet: cmd={parsed['cmd_id']:#x} code={parsed['code']:#x} "
                        f"seq={parsed['sequence']} src={parsed['src_package']} "
                        f"dst={parsed['dst_package']} data_len={len(parsed['data'])}")
            if parsed["sequence"] == sequence:
                return parsed

            if parsed["cmd_id"] == 0x02:
                await self._send_p2p_ack(parsed)

    async def wait_for_p2p_data(self, src_package: str, dst_package: str,
                                timeout: float = 45.0) -> dict:
        deadline = asyncio.get_running_loop().time() + timeout
        last_ack = None
        last_error = None
        while True:
            remaining = deadline - asyncio.get_running_loop().time()
            if remaining <= 0:
                raise P2PNoDataError(src_package, dst_package, ack=last_ack, error_tlvs=last_error)

            try:
                tlvs = await self._recv(SVC_P2P, 0x01, timeout=remaining)
            except asyncio.TimeoutError as e:
                raise P2PNoDataError(
                    src_package,
                    dst_package,
                    ack=last_ack,
                    error_tlvs=last_error,
                ) from e
            decoded = self._decrypt_transaction_tlvs(tlvs)
            parsed = self._parse_p2p_response(decoded)
            logger.info(f"P2P async packet: cmd={parsed['cmd_id']:#x} code={parsed['code']:#x} "
                        f"seq={parsed['sequence']} src={parsed['src_package']} "
                        f"dst={parsed['dst_package']} data_len={len(parsed['data'])}")
            if parsed["cmd_id"] == 0:
                logger.info(f"P2P unparsed TLVs: { {hex(k): v.hex() for k, v in decoded.items()} }")
                if 0x7F in decoded:
                    last_error = decoded
                    logger.info(f"P2P error TLV while waiting for data: {decoded[0x7F].hex()}")

            if parsed["cmd_id"] == 0x02 and parsed["src_package"] == src_package:
                await self._send_p2p_ack(parsed)
                if not dst_package or parsed["dst_package"] == dst_package:
                    return parsed

            if parsed["cmd_id"] == 0x03:
                if parsed["src_package"] == src_package and (not dst_package or parsed["dst_package"] == dst_package):
                    last_ack = parsed
                continue

    async def ping_dictionary_sync(self) -> dict:
        return await self.p2p_command(
            0x01,
            "com.huawei.health",
            P2P_DICT_PACKAGE,
            timeout=20.0,
        )

    @staticmethod
    def _dict_to_bytes(value: int) -> bytes:
        return bytes([(value >> 16) & 0xFF, (value >> 8) & 0xFF, value & 0xFF])

    @staticmethod
    def _p2p_dict_request_payload(dict_class: int, start_ms: int, end_ms: int) -> bytes:
        data_tlv = (
            tlv_enc(0x01, b"\x01") +
            tlv_enc(0x02, Band._dict_to_bytes(dict_class)) +
            tlv_enc(0x05, struct.pack(">Q", start_ms)) +
            tlv_enc(0x06, struct.pack(">Q", end_ms)) +
            tlv_enc(0x0D, b"\x01")
        )
        return b"\x01" + data_tlv

    @staticmethod
    def _parse_dictionary_samples(data: bytes) -> list[dict]:
        if not data or data[0] != 0x01:
            logger.debug(f"  P2P dictionary payload has unsupported type: {data.hex()}")
            return []

        samples = []
        for tag, block in tlv_items(data[1:]):
            if tag != 0x83:
                continue
            for rec_tag, rec_block in tlv_items(block):
                if rec_tag != 0x84:
                    continue
                rec = tlv_dec(rec_block)
                start_ms = Band._tlv_int(rec.get(0x05, b""))
                end_ms = Band._tlv_int(rec.get(0x06, b""))
                modify_ms = Band._tlv_int(rec.get(0x0C, b""))
                for value_tag, value_block in tlv_items(rec_block):
                    if value_tag != 0x87:
                        continue
                    for entry_tag, entry_block in tlv_items(value_block):
                        if entry_tag != 0x88:
                            continue
                        entry = tlv_dec(entry_block)
                        data_type = Band._tlv_int(entry.get(0x09, b""))
                        raw_value = entry.get(0x0A) or entry.get(0x0B) or b""
                        if data_type != DICT_HRV_RMSSD_VALUE or len(raw_value) != 8:
                            continue
                        rmssd = struct.unpack(">d", raw_value)[0]
                        if 0 <= rmssd <= 200:
                            samples.append({
                                "start_ms": start_ms,
                                "end_ms": end_ms,
                                "modify_ms": modify_ms,
                                "rmssd": round(rmssd, 1),
                            })
        return samples

    @staticmethod
    def _dictionary_payload_class(data: bytes) -> int:
        if not data or data[0] != 0x01:
            return 0
        top = tlv_dec(data[1:])
        return Band._tlv_int(top.get(0x02, b""))

    async def sync_dictionary_class(self, dict_class: int, days: int = 0,
                                    timeout: float = 45.0) -> dict:
        end_ms = int(time.time() * 1000)
        start_ms = 0 if days <= 0 else end_ms - days * 24 * 60 * 60 * 1000
        payload = self._p2p_dict_request_payload(dict_class, start_ms, end_ms)
        seq = await self.send_p2p_command(
            0x02,
            P2P_DICT_MODULE,
            P2P_DICT_PACKAGE,
            P2P_LOCAL_FINGERPRINT,
            P2P_REMOTE_FINGERPRINT,
            payload,
        )
        window = "full" if start_ms == 0 else f"{days}d"
        logger.info(f"P2P dictionary sync request sent: class={dict_class} seq={seq} window={window}")
        response = await self.wait_for_p2p_data(P2P_DICT_PACKAGE, P2P_DICT_MODULE, timeout=timeout)
        logger.info(f"P2P dictionary sync response: class={self._dictionary_payload_class(response['data'])} "
                    f"cmd={response['cmd_id']:#x} code={response['code']:#x} "
                    f"data_len={len(response['data'])}")
        return response

    async def get_hrv_samples(self, days: int = 0) -> list[dict]:
        response = await self.sync_dictionary_class(DICT_HRV_CLASS, days=days, timeout=15.0)
        return self._parse_dictionary_samples(response["data"])

    async def probe_dictionary_classes(self, days: int = 0) -> dict:
        results = {}
        for name, dict_class in DICT_PROBE_CLASSES.items():
            try:
                response = await self.sync_dictionary_class(dict_class, days=days, timeout=12.0)
                results[name] = {
                    "class": dict_class,
                    "data_class": self._dictionary_payload_class(response["data"]),
                    "data_len": len(response["data"]),
                    "code": response["code"],
                }
            except P2PNoDataError as e:
                item = {"class": dict_class, "status": "timeout_no_data"}
                if e.ack:
                    item.update({
                        "status": "ack_no_data",
                        "ack_code": e.ack["code"],
                        "ack_cmd": e.ack["cmd_id"],
                        "ack_sequence": e.ack["sequence"],
                    })
                if e.error_tlvs:
                    item.update({
                        "status": "error_tlv",
                        "error_tlvs": {hex(k): v.hex() for k, v in e.error_tlvs.items()},
                    })
                results[name] = item
            except asyncio.TimeoutError:
                results[name] = {"class": dict_class, "timeout": True}
        return results

    # ── Handshake steps ───────────────────────────────────────────────────────

    async def get_link_params(self):
        logger.info("Step 1: Link params...")
        tlv  = tlv_enc(0x01) + tlv_enc(0x02) + tlv_enc(0x03) + tlv_enc(0x04)
        tlvs = await self._transact(SVC_DEV, CMD_LINK, tlv)

        nf = tlvs.get(0x05, b"\x00\x01" + b"\x00"*16)
        self.auth_version     = nf[1]
        self.server_nonce     = nf[2:18]
        self.dev_support_type = tlvs[0x07][0] if 0x07 in tlvs else 0
        self.auth_algo        = tlvs[0x08][0] if 0x08 in tlvs else 0
        self.bond_state       = tlvs[0x09][0] if 0x09 in tlvs else 0
        self.enc_method       = tlvs[0x0C][0] if 0x0C in tlvs else 0
        if 0x02 in tlvs:
            self.slice_size   = struct.unpack(">H", tlvs[0x02])[0]

        logger.info(f"  auth_version={self.auth_version} dev_support={self.dev_support_type} "
                    f"auth_algo={self.auth_algo} bond_state={self.bond_state} "
                    f"enc_method={self.enc_method} slice_size={self.slice_size}")
        logger.info(f"  serverNonce={self.server_nonce.hex()}")

    async def security_negotiation(self) -> int:
        logger.info("Step 2: Security negotiation...")
        self.auth_mode = 4 if self.dev_support_type == 4 else 2

        tlv  = tlv_enc(0x01, bytes([self.auth_mode]))
        if self.auth_mode in (0x02, 0x04):
            tlv += tlv_enc(0x02, b"\x01")
        tlv += tlv_enc(0x05, self.android_id.encode("utf-8"))
        tlv += tlv_enc(0x03, b"\x01") + tlv_enc(0x04, b"\x00")
        if self.auth_mode == 0x04:
            # Gadgetbridge adds these for authMode=4
            tlv += tlv_enc(0x06)                          # empty tag 0x06
            tlv += tlv_enc(0x07, b"Python")               # phone model (tag 0x07)
        if self.enc_method == 0x01:
            tlv += tlv_enc(0x0D, b"\x01")

        tlvs = await self._transact(SVC_DEV, CMD_SECNEGO, tlv)

        auth_type, pw = -1, -1
        if 0x01 in tlvs:
            v = tlvs[0x01][0]
            if v == 0x01: auth_type = 0x0186A0
            elif v == 0x04: pw = 4
        if 0x02 in tlvs:
            auth_type = tlvs[0x02][0]
            if pw != -1: auth_type ^= pw
        if 0x7F in tlvs:
            auth_type = tlvs[0x7F][0]

        if 0x05 in tlvs:
            self.band_device_id = tlvs[0x05].decode("ascii", errors="replace").lower()
            logger.info(f"  bandDeviceId={self.band_device_id}")
        if 0x09 in tlvs:
            band_mac = tlvs[0x09].rstrip(b"\x00").decode("ascii", errors="replace")
            logger.info(f"  bandMac={band_mac}")

        logger.info(f"  authType={auth_type:#x}")
        return auth_type

    async def get_pin_code(self) -> bytes:
        logger.info("Step 3: PIN code (watch for confirmation prompt on band)...")
        tlvs = await self._transact(SVC_DEV, CMD_PINCODE, tlv_enc(0x01), timeout=30.0)
        msg  = tlvs.get(0x01, b"")
        iv   = tlvs.get(0x02, b"")
        pin  = decrypt_pin_code(self.enc_method, self.auth_version, msg, iv)
        logger.info(f"  Received PIN: {pin.hex()}")
        return pin

    # ── HiChain3 ──────────────────────────────────────────────────────────────

    def _hichain_json(self, step: int, payload_extra: dict,
                      op_code: int, request_id: int,
                      value_extra: dict = None) -> bytes:
        """Build the JSON TLV blob for a HiChain step packet."""
        android_id_hex = self.android_id
        message_id     = step if op_code == 0x01 else (step | 0x10)

        version_obj = {"minVersion": "1.0.0", "currentVersion": "2.0.16"}
        payload_obj = {"version": version_obj, **payload_extra}

        value_obj = {
            "authForm": 0,
            "payload": payload_obj,
            "groupAndModuleVersion": "2.0.1",
            "message": message_id,
        }
        if op_code == 0x01:
            # peerDeviceId and connDeviceId = our own android_id as hex string.
            # Gadgetbridge: new String(getAndroidId(), UTF_8) = the hex string itself.
            value_obj.update({
                "requestId":    str(request_id),
                "groupId":      GROUP_ID,
                "groupName":    "health_group_name",
                "groupOp":      2,
                "groupType":    256,
                "peerDeviceId": android_id_hex,
                "connDeviceId": android_id_hex,
                "appId":        "com.huawei.health",
                "ownerName":    "",
            })
        else:
            value_obj["isDeviceLevel"] = False

        if value_extra:
            value_obj.update(value_extra)

        json_str = json.dumps(value_obj, separators=(",", ":")).encode("utf-8")
        tlv  = tlv_enc(0x01, json_str)
        tlv += tlv_enc(0x02, bytes([op_code]))
        tlv += tlv_enc(0x03, struct.pack(">q", request_id))
        return tlv

    def _parse_hichain_response(self, tlvs: dict) -> dict:
        """Parse HiChain JSON response, return (step, payload_dict)."""
        json_bytes = tlvs.get(0x01, b"{}")
        # Band sends TLV-format (non-JSON) error responses when it rejects auth.
        # tag=0x01 will contain the appId string ("com.huawei.health") not a JSON object.
        if not json_bytes.startswith(b"{"):
            app_id     = json_bytes.decode("utf-8", errors="replace")
            resp_type  = tlvs.get(0x04, b"\x00")[0] if 0x04 in tlvs else 0
            status     = tlvs.get(0x05, b"\x00")[0] if 0x05 in tlvs else 0
            if app_id == "com.huawei.health" and resp_type != 0 and status == 0:
                logger.debug(f"  HiChain final TLV ACK: appId={app_id!r} type={resp_type} status={status}")
                return 4, {"appId": app_id, "type": resp_type, "status": status}
            logger.warning(f"  HiChain TLV-format (non-JSON) response: appId={app_id!r} "
                           f"type={resp_type} status={status} all_tags={ {hex(k): v.hex() for k,v in tlvs.items()} }")
            raise ValueError(f"HiChain TLV response type={resp_type} status={status} (appId={app_id!r})")
        obj     = json.loads(json_bytes.rstrip(b"\x00").decode("utf-8"))
        step    = obj.get("message", 0) & 0x0F   # mask reconnect bit
        payload = obj.get("payload", {})
        error   = obj.get("errorCode", 0)
        if error != 0:
            raise ValueError(f"HiChain errorCode={error}")
        return step, payload

    async def hichain_authenticate(self, pin_code: bytes, op_code: int = 0x01):
        """Execute the 4-step HiChain3 mutual authentication."""
        logger.info(f"Step 4: HiChain3 auth (op={op_code:#x})...")
        request_id  = int(time.time() * 1000)
        seed        = secrets.token_bytes(32)
        rand_self   = secrets.token_bytes(16)
        auth_id_self = self.android_id.encode("utf-8")
        session_key  = None
        challenge    = None

        # ── Step 1 ────────────────────────────────────────────────────────────
        step1_payload = {
            "isoSalt":       hexu(rand_self),
            "peerAuthId":    hexu(auth_id_self),
            "operationCode": op_code,
            "seed":          hexu(seed),
            "peerUserType":  0,
        }
        if op_code == 0x02:
            step1_payload.update({
                "pkgName":     "com.huawei.devicegroupmanage",
                "serviceType": GROUP_ID,
                "keyLength":   32,
            })

        logger.info("  HiChain step 1...")
        tlv      = self._hichain_json(1, step1_payload, op_code, request_id)
        step1_json_str = tlv_dec(tlv).get(0x01, b"{}").decode("utf-8", errors="replace")
        logger.debug(f"  Step 1 JSON: {step1_json_str}")
        if op_code == 0x01:
            print("\n" + "="*60)
            print("ACTION REQUIRED: Check your band screen NOW.")
            print("The band will show a second pairing/bond confirmation.")
            print("You have 90 seconds to accept it on the band.")
            print("="*60 + "\n")
        resp_tlvs = await self._transact(SVC_DEV, CMD_HICHAIN, tlv, timeout=90.0,
                                         pin_code=pin_code, rand_self=rand_self, seed=seed)
        step, p1 = self._parse_hichain_response(resp_tlvs)
        logger.debug(f"  Step 1 response: step={step} payload={p1}")

        if op_code == 0x01:
            print("\n" + "="*70)
            print("👉 PAIRING CONFIRMATION IS ON YOUR BAND SCREEN RIGHT NOW!")
            print("👉 1. TAP THE CHECKMARK / YES ON YOUR WATCH SCREEN!")
            print("👉 2. THEN PRESS ENTER IN THIS TERMINAL TO FINISH PAIRING...")
            print("="*70 + "\n")
            try:
                await asyncio.to_thread(input, "Press Enter AFTER tapping the checkmark on your watch: ")
            except Exception:
                await asyncio.sleep(10)

        required_step1 = {"isoSalt", "peerAuthId", "token"}
        if not required_step1.issubset(p1):
            logger.warning(f"  Unexpected HiChain step 1 payload: step={step} payload={p1} "
                           f"raw_tlvs={ {hex(k): v.hex() for k, v in resp_tlvs.items()} }")
            raise ValueError(f"HiChain step 1 missing {sorted(required_step1 - set(p1))}")

        rand_peer    = bytes.fromhex(p1["isoSalt"])
        auth_id_peer = bytes.fromhex(p1["peerAuthId"])
        peer_token   = bytes.fromhex(p1["token"])

        # Derive PSK
        if op_code == 0x01:
            pin_hex_utf8 = hexu(pin_code).encode("utf-8")
            key          = sha256(pin_hex_utf8)
        else:
            key = self.auth_key
            if not key:
                raise RuntimeError("Cannot reconnect HiChain without stored auth_key")

        psk = hmac_sha256(key, seed)

        # Verify peer token: HMAC(psk, randPeer+randSelf+authIdSelf+authIdPeer)
        token_check = hmac_sha256(psk,
                                  rand_peer + rand_self + auth_id_self + auth_id_peer)
        if token_check != peer_token:
            logger.warning(f"  Peer token mismatch! expected={token_check.hex()} got={peer_token.hex()}")
        else:
            logger.info("  Peer token verified OK.")

        # ── Step 2 ────────────────────────────────────────────────────────────
        self_token = hmac_sha256(psk,
                                 rand_self + rand_peer + auth_id_peer + auth_id_self)
        step2_payload = {
            "peerAuthId": hexu(auth_id_self),
            "token":      hexu(self_token),
        }
        logger.info("  HiChain step 2...")
        tlv      = self._hichain_json(2, step2_payload, op_code, request_id,
                                       value_extra={"isDeviceLevel": False} if op_code == 0x02 else None)
        resp_tlvs = await self._transact(SVC_DEV, CMD_HICHAIN, tlv)
        step, p2 = self._parse_hichain_response(resp_tlvs)
        logger.debug(f"  Step 2 response: step={step} payload={p2}")

        ret_code_mac       = bytes.fromhex(p2["returnCodeMac"])
        ret_code_mac_check = hmac_sha256(psk, b"\x00\x00\x00\x00")
        if ret_code_mac_check != ret_code_mac:
            logger.warning(f"  returnCodeMac mismatch! expected={ret_code_mac_check.hex()}")
        else:
            logger.info("  returnCodeMac verified OK.")

        # ── Step 3 (first-auth only) ──────────────────────────────────────────
        salt        = rand_self + rand_peer
        session_key = hkdf_sha256(psk, salt, b"hichain_iso_session_key", 32)
        logger.debug(f"  sessionKey={session_key.hex()}")

        if op_code == 0x01:
            nonce     = secrets.token_bytes(12)
            challenge = secrets.token_bytes(16)
            aad       = b"hichain_iso_exchange"
            enc_data  = aes_gcm_enc(challenge, session_key, nonce, aad)

            step3_payload = {
                "nonce":   hexu(nonce),
                "encData": hexu(enc_data),
            }
            logger.info("  HiChain step 3 (first-auth)...")
            tlv      = self._hichain_json(3, step3_payload, op_code, request_id)
            resp_tlvs = await self._transact(SVC_DEV, CMD_HICHAIN, tlv, timeout=30.0)
            step, p3 = self._parse_hichain_response(resp_tlvs)
            logger.debug(f"  Step 3 response: step={step} payload={p3}")

            nonce3       = bytes.fromhex(p3["nonce"])
            enc_auth_tok = bytes.fromhex(p3["encAuthToken"])
            auth_token   = aes_gcm_dec(enc_auth_tok, session_key, nonce3, challenge)
            self.auth_key = auth_token
            save_auth_key(auth_token)
            logger.info(f"  authToken stored: {auth_token.hex()}")
        else:
            logger.info("  Step 3 skipped (reconnect mode).")

        # ── Step 4 ────────────────────────────────────────────────────────────
        nonce4    = secrets.token_bytes(12)
        aad4      = b"hichain_iso_result"
        enc_res   = aes_gcm_enc(b"\x00\x00\x00\x00", session_key, nonce4, aad4)
        step4_payload = {
            "nonce":         hexu(nonce4),
            "encResult":     hexu(enc_res),
            "operationCode": op_code,
        }
        step4_num = 4 if op_code == 0x01 else 3
        logger.info("  HiChain step 4...")
        tlv      = self._hichain_json(step4_num, step4_payload, op_code, request_id)
        resp_tlvs = await self._transact(SVC_DEV, CMD_HICHAIN, tlv)
        step, _  = self._parse_hichain_response(resp_tlvs)
        logger.info(f"  HiChain complete (final step={step}).")

        if op_code == 0x01:
            logger.info("  First-auth finished; starting HiChain bind pass (op=0x02)...")
            await self.hichain_authenticate(pin_code=b"", op_code=0x02)
            return

        # For reconnect: derive final session key
        if op_code == 0x02:
            final_key = hkdf_sha256(session_key, salt, b"hichain_return_key", 32)
            self.secret_key = final_key
            save_secret_key(final_key)
            logger.info(f"  Session transaction key stored: {final_key.hex()}")

    # ── Full handshake ────────────────────────────────────────────────────────

    async def handshake(self):
        await self.get_link_params()

        auth_type = 0
        if self.dev_support_type != 0:
            auth_type = await self.security_negotiation()

        def is_hichain3(at):
            return (at ^ 0x01) == 0x04 or (at ^ 0x02) == 0x04

        def is_hichainlite(at):
            return at in (0x01, 0x02)

        if auth_type == 0x0186A0 or is_hichain3(auth_type):
            logger.info("  Mode: HiChain3")
            if auth_type == 0x05:
                if self.auth_key:
                    logger.warning("  Band requested first-auth despite stored key; refreshing auth token (op=0x01)")
                else:
                    logger.info("  No stored key -> first-auth (op=0x01), fetching PIN...")
                pin = await self.get_pin_code()
                await self.hichain_authenticate(pin_code=pin, op_code=0x01)
            elif self.auth_key:
                logger.info("  Stored auth key found → reconnect (op=0x02)")
                await self.hichain_authenticate(pin_code=b"", op_code=0x02)
            else:
                logger.info("  No stored key → first-auth (op=0x01), fetching PIN...")
                pin = await self.get_pin_code()
                await self.hichain_authenticate(pin_code=pin, op_code=0x01)

        elif is_hichainlite(auth_type) or self.dev_support_type == 0x02:
            logger.info("  Mode: HiChainLite (not yet implemented)")

        else:
            logger.info("  Mode: Normal (not yet implemented)")

        logger.info("Handshake complete!")

    @staticmethod
    def _timezone_offset_bytes() -> bytes:
        if time.daylight and time.localtime().tm_isdst > 0:
            offset = -time.altzone
        else:
            offset = -time.timezone
        hour = ((-offset // 3600) + 128) if offset < 0 else offset // 3600
        minute = int(offset / 60) % 60
        return bytes([hour & 0xFF, minute & 0xFF])

    async def get_product_info(self) -> dict:
        tags = (0x01, 0x02, 0x07, 0x09, 0x0A, 0x11, 0x12, 0x16,
                0x1A, 0x1D, 0x1E, 0x1F, 0x20, 0x21, 0x22, 0x23)
        tlv = b"".join(tlv_enc(tag) for tag in tags)
        tlvs = await self._transact_encrypted(SVC_DEV, CMD_PRODUCT, tlv, timeout=8.0)
        info = {
            "software": self._tlv_str(tlvs.get(0x07, b"")),
            "serial": self._tlv_str(tlvs.get(0x09, b"")),
            "model": self._tlv_str(tlvs.get(0x0A, b"")).strip(),
            "name": self._tlv_str(tlvs.get(0x11, b"")),
        }
        logger.info(f"Product info: {info}")
        return info

    async def set_time(self) -> dict:
        now = int(time.time())
        tlv = tlv_enc(0x01, struct.pack(">I", now)) + tlv_enc(0x02, self._timezone_offset_bytes())
        tlvs = await self._transact_encrypted(SVC_DEV, CMD_TIME, tlv, timeout=8.0)
        device_time = self._tlv_int(tlvs.get(0x01, b""))
        if device_time:
            logger.info(f"Time sync: phone={now} band={device_time} delta={device_time - now}s")
        return tlvs

    async def get_supported_services(self) -> set[int]:
        tlvs = await self._transact_encrypted(
            SVC_DEV, CMD_SUPPORTED_SERVICES, tlv_enc(0x01, KNOWN_SUPPORTED_SERVICES), timeout=8.0
        )
        bitmap = tlvs.get(0x02, b"")
        active = {0x01}
        for i, supported in enumerate(bitmap):
            if supported and i < len(KNOWN_SUPPORTED_SERVICES):
                active.add(KNOWN_SUPPORTED_SERVICES[i])
        self.supported_services = active
        logger.info(f"Supported services: {len(active)} active -> {[hex(s) for s in sorted(active)]}")
        return active

    @staticmethod
    def _parse_supported_commands_response(container: bytes) -> dict[int, bytes]:
        out = {}
        current_service = None
        for tag, value in tlv_items(container):
            if tag == 0x02:
                current_service = value[0] if value else None
            elif tag == 0x04 and current_service is not None:
                candidates = COMMANDS_PER_SERVICE.get(current_service, b"")
                commands = bytes(
                    candidates[i]
                    for i, flag in enumerate(value)
                    if flag and i < len(candidates)
                )
                out[current_service] = commands
                current_service = None
        return out

    def _supports_command(self, service: int, command: int) -> bool:
        return command in self.commands_per_service.get(service, b"")

    def _supports_expand_capability(self, which: int) -> bool:
        if not self.expand_capabilities:
            return False
        idx = which // 8
        if idx >= len(self.expand_capabilities):
            return False
        return bool(self.expand_capabilities[idx] & (1 << (which % 8)))

    async def get_supported_commands(self) -> dict[int, bytes]:
        if not self.supported_services:
            await self.get_supported_services()
        commands_by_service = {}
        for service in sorted(self.supported_services):
            candidates = COMMANDS_PER_SERVICE.get(service)
            if not candidates:
                continue
            request = tlv_enc(0x81, tlv_enc(0x02, bytes([service])) + tlv_enc(0x03, candidates))
            tlvs = await self._transact_encrypted(SVC_DEV, CMD_SUPPORTED_COMMANDS, request, timeout=8.0)
            if 0x81 not in tlvs:
                logger.debug(f"Supported commands missing container for service={service:#x}: {tlvs}")
                continue
            commands_by_service.update(self._parse_supported_commands_response(tlvs[0x81]))
        self.commands_per_service = commands_by_service
        logger.info(
            "Supported commands: "
            f"{ {hex(s): [hex(c) for c in cmds] for s, cmds in sorted(commands_by_service.items())} }"
        )
        return commands_by_service

    async def get_expand_capabilities(self) -> dict:
        if not self.commands_per_service:
            await self.get_supported_commands()
        if not self._supports_command(SVC_DEV, CMD_EXPAND_CAPABILITY):
            logger.info("Expand capabilities not supported by command bitmap")
            self.capability_flags = {}
            return {}
        tlvs = await self._transact_encrypted(SVC_DEV, CMD_EXPAND_CAPABILITY, tlv_enc(0x01), timeout=8.0)
        self.expand_capabilities = tlvs.get(0x01, b"")
        self.capability_flags = {
            label: self._supports_expand_capability(bit)
            for label, bit in EXPAND_CAPABILITY_LABELS.items()
        }
        artifact = {
            "supported_services": [f"0x{s:02x}" for s in sorted(self.supported_services)],
            "commands_per_service": {
                f"0x{s:02x}": [f"0x{c:02x}" for c in cmds]
                for s, cmds in sorted(self.commands_per_service.items())
            },
            "expand_capabilities_hex": self.expand_capabilities.hex(),
            "capability_flags": self.capability_flags,
        }
        save_json_artifact("latest_capabilities.json", artifact)
        logger.info(f"Expand capabilities: {self.capability_flags}")
        return artifact

    async def get_setting_related(self) -> dict:
        tlv = b"".join(tlv_enc(tag) for tag in (0x01, 0x02, 0x03, 0x04, 0x05, 0x06))
        tlvs = await self._transact_encrypted(SVC_DEV, CMD_SETTING_RELATED, tlv, timeout=8.0)
        file_flags = tlvs.get(0x02, b"\x00")[0] if tlvs.get(0x02) else 0
        settings = {
            "raw": {hex(k): v.hex() for k, v in tlvs.items()},
            "file_flags": file_flags,
            "trusleep_new_sync": bool(file_flags & 0x02),
            "rri_new_sync": bool(file_flags & 0x04),
            "gps_new_sync": bool(file_flags & 0x08),
        }
        logger.info(f"Setting related: {settings}")
        return settings

    async def send_accept_agreements(self) -> dict:
        timestamp = str(int(time.time() * 1000)).encode("utf-8")
        version = b"20230508-20230508-0-0"
        items = []
        for key in (
            b"software_update_service_statement",
            b"device_information_management",
            b"user_license_agreement",
        ):
            items.append(tlv_enc(0x82, (
                tlv_enc(0x03, key) +
                tlv_enc(0x04, b"\x01") +
                tlv_enc(0x05, version) +
                tlv_enc(0x06, timestamp)
            )))
        return await self._transact_encrypted(
            SVC_DEV,
            CMD_ACCEPT_AGREEMENT,
            tlv_enc(0x81, b"".join(items)),
            timeout=8.0,
        )

    async def send_reverse_capabilities(self) -> dict:
        return await self._transact_encrypted(
            SVC_DEV,
            CMD_REVERSE_CAPABILITIES,
            tlv_enc(0x01, bytes([0xFD, 0xF7, 0x73, 0x7A])),
            timeout=8.0,
        )

    async def send_extended_account(self, account: str = "") -> dict:
        tlv = tlv_enc(0x01, account.encode("utf-8") if account else b"\x00")
        if self.capability_flags.get("diff_account_pairing_optimization"):
            tlv += tlv_enc(0x03, b"\x01")
        return await self._transact_encrypted(SVC_ACCOUNT, CMD_EXTENDED_ACCOUNT, tlv, timeout=8.0)

    async def send_country_code(self, country_code: str = "IN", site_id: int = None) -> dict:
        tlv = tlv_enc(0x01, country_code.encode("utf-8"))
        if site_id is not None:
            tlv += tlv_enc(0x02, bytes([site_id & 0xFF]))
        return await self._transact_encrypted(SVC_ACCOUNT, CMD_COUNTRY_CODE, tlv, timeout=8.0)

    async def send_setup_device_status(self):
        tlv = (
            tlv_enc(0x01, b"\x01") +
            tlv_enc(0x02, b"HUAWEI Band 10-44F") +
            tlv_enc(0x03, b"\x00")
        )
        await self._send_encrypted_no_wait(SVC_DEV, CMD_SETUP_DEVICE_STATUS, tlv)
        await asyncio.sleep(0.1)
        logger.info("Setup device status sent")

    async def get_wear_status(self) -> dict:
        return await self._transact_encrypted(SVC_DEV, CMD_WEAR_STATUS, tlv_enc(0x01), timeout=5.0)

    async def get_connect_status(self) -> dict:
        return await self._transact_encrypted(SVC_DEV, CMD_CONNECT_STATUS, tlv_enc(0x01, b"\x01"), timeout=5.0)

    async def get_device_status(self) -> dict:
        return await self._transact(SVC_DEV, CMD_DEVICE_STATUS, tlv_enc(0x01), timeout=5.0)

    @staticmethod
    def _byte(value: int) -> bytes:
        return bytes([int(value) & 0xFF])

    @staticmethod
    def _i8(value: float | int) -> bytes:
        value = int(round(value))
        return struct.pack(">b", max(-128, min(127, value)))

    @staticmethod
    def _u8(value: float | int) -> bytes:
        value = int(round(value))
        return bytes([max(0, min(255, value))])

    @staticmethod
    def _i16(value: int) -> bytes:
        return struct.pack(">h", max(-32768, min(32767, int(value))))

    @staticmethod
    def _i32(value: int) -> bytes:
        return struct.pack(">i", max(-2147483648, min(2147483647, int(value))))

    @staticmethod
    def _f64_le(value: float | int) -> bytes:
        # HuaweiTLV writes Java Double values little-endian.
        return struct.pack("<d", float(value))

    @staticmethod
    def _same_utc_day_timestamp(candidate: int | float | None, day_timestamp: int | float | None) -> bool:
        if not candidate or not day_timestamp:
            return False
        day_start = int(day_timestamp) - (int(day_timestamp) % 86400)
        return day_start <= int(candidate) < day_start + 86400

    @staticmethod
    def _weather_icon_byte(condition_code: int) -> int:
        # Gadgetbridge's OpenWeatherMap-condition to Huawei-icon mapping.
        exact = {
            500: 0x07, 501: 0x08, 502: 0x09, 503: 0x0A, 504: 0x0C, 511: 0x13,
            600: 0x0E, 601: 0x0F, 602: 0x10, 611: 0x06,
            701: 0x12, 741: 0x12, 721: 0x35, 751: 0x1E, 761: 0x1D,
            800: 0x00, 801: 0x01, 802: 0x01, 803: 0x02, 804: 0x02,
        }
        if condition_code in exact:
            return exact[condition_code]
        if 200 <= condition_code < 300:
            return 0x04
        if 300 <= condition_code < 400:
            return 0x07
        if 500 <= condition_code < 600:
            return 0x08
        if 600 <= condition_code < 700:
            return 0x0F
        return 0x63

    @staticmethod
    def _weather_wind_direction(degrees: float | int | None) -> int:
        if degrees is None:
            return 0
        degrees = float(degrees)
        if degrees < 0 or degrees > 360:
            return 0
        value = int((degrees / 45.0) + 0.5) % 8
        return 8 if value == 0 else value

    @staticmethod
    def _weather_beaufort(wind_speed_kmh: float | int | None) -> int:
        if wind_speed_kmh is None:
            return 0
        speed = max(0.0, float(wind_speed_kmh))
        for index, upper in enumerate([1, 6, 12, 20, 29, 39, 50, 62, 75, 89, 103, 118]):
            if speed < upper:
                return index
        return 12

    @staticmethod
    def _weather_support_defaults() -> dict:
        return {
            "weather": True,
            "wind": True,
            "pm25": False,
            "temperature": True,
            "location": True,
            "current_temperature": True,
            "unit": True,
            "aqi": False,
            "time": True,
            "source": True,
            "icon": True,
            "sun_moon": False,
            "moon_phase": False,
            "uv_index": False,
            "extended_hourly": False,
        }

    def _weather_parse_basic_support(self, settings: dict, tlvs: dict):
        bitmap = tlvs.get(0x01, b"\x00")[0]
        settings.update({
            "weather": bool(bitmap & 0x01),
            "wind": bool(bitmap & 0x02),
            "pm25": bool(bitmap & 0x04),
            "temperature": bool(bitmap & 0x08),
            "location": bool(bitmap & 0x10),
            "current_temperature": bool(bitmap & 0x20),
            "unit": bool(bitmap & 0x40),
            "aqi": bool(bitmap & 0x80),
        })

    def _weather_parse_extended_support(self, settings: dict, tlvs: dict):
        bitmap = self._tlv_int(tlvs.get(0x01, b"\x00\x00"))
        settings.update({
            "time": bool(bitmap & 0x01),
            "source": bool(bitmap & 0x02),
            "icon": bool(bitmap & 0x04),
        })

    def _weather_parse_sun_moon_support(self, settings: dict, tlvs: dict):
        bitmap = self._tlv_int(tlvs.get(0x01, b"\x00\x00\x00\x00"))
        settings.update({
            "sun_moon": bool(bitmap & 0x01),
            "moon_phase": bool(bitmap & 0x02),
        })

    def _weather_current_tlv(self, payload: dict, settings: dict) -> bytes:
        condition = int(payload.get("condition_code", 800) or 800)
        now = int(payload.get("timestamp") or time.time())
        location = str(payload.get("location") or "Weather")[:64]
        source = str(payload.get("source") or "Open-Meteo")[:64]
        current_temp = payload.get("current_temp_c")
        low_temp = payload.get("low_temp_c")
        high_temp = payload.get("high_temp_c")
        wind_speed = payload.get("wind_speed_kmh")
        wind_dir = payload.get("wind_direction")

        tlv = b""
        nested_81 = b""
        if settings.get("icon"):
            nested_81 += tlv_enc(0x02, self._byte(self._weather_icon_byte(condition)))
        if settings.get("wind"):
            wind = (self._weather_wind_direction(wind_dir) << 8) | self._weather_beaufort(wind_speed)
            nested_81 += tlv_enc(0x03, self._i16(wind))
        if nested_81:
            tlv += tlv_enc(0x81, nested_81)

        if settings.get("temperature") and low_temp is not None and high_temp is not None:
            tlv += tlv_enc(0x85, tlv_enc(0x06, self._i8(low_temp)) + tlv_enc(0x07, self._i8(high_temp)))
        if settings.get("pm25") and payload.get("pm25") is not None:
            tlv += tlv_enc(0x04, self._i16(payload["pm25"]))
        if settings.get("location"):
            tlv += tlv_enc(0x08, location.encode("utf-8"))
        if settings.get("current_temperature") and current_temp is not None:
            tlv += tlv_enc(0x09, self._i8(current_temp))
        if settings.get("unit"):
            tlv += tlv_enc(0x0A, b"\x00")
        if settings.get("aqi") and payload.get("aqi") is not None:
            tlv += tlv_enc(0x0B, self._i16(payload["aqi"]))
        if settings.get("time"):
            tlv += tlv_enc(0x0C, self._i32(now))
        if settings.get("source"):
            tlv += tlv_enc(0x0E, source.encode("utf-8"))
        if settings.get("uv_index") and payload.get("uv_index") is not None:
            tlv += tlv_enc(0x0F, self._u8(payload["uv_index"]))

        if settings.get("extended_hourly"):
            if payload.get("humidity") is not None:
                tlv += tlv_enc(0x10, self._u8(payload["humidity"]))
            if wind_speed is not None:
                tlv += tlv_enc(0x11, self._i32(round(float(wind_speed))))
            if payload.get("feels_like_c") is not None:
                tlv += tlv_enc(0x12, self._i32(round(float(payload["feels_like_c"]))))
        return tlv

    def _weather_forecast_tlv(self, payload: dict, settings: dict) -> bytes:
        tlv = b""
        now = int(payload.get("timestamp") or time.time())
        hourly_items = []
        future_hourly = [
            item for item in (payload.get("hourly") or [])
            if item.get("timestamp") and int(item["timestamp"]) >= now - 3600
        ]
        for item in future_hourly[:24]:
            # Open-Meteo returns hourly rows starting at midnight. Gadgetbridge
            # sends forecast rows, so keep stale hours out of the packet.
            entry = tlv_enc(0x03, self._i32(item["timestamp"]))
            if settings.get("icon"):
                entry += tlv_enc(0x04, self._byte(self._weather_icon_byte(int(item.get("condition_code") or 800))))
            if settings.get("temperature") or settings.get("current_temperature"):
                if item.get("temp_c") is not None:
                    entry += tlv_enc(0x05, self._i8(item["temp_c"]))
            if settings.get("extended_hourly"):
                precip = item.get("precipitation_probability", item.get("precipitation"))
                if precip is not None:
                    entry += tlv_enc(0x06, self._u8(precip))
                if item.get("uv_index") is not None:
                    entry += tlv_enc(0x07, self._u8(item["uv_index"]))
            hourly_items.append(tlv_enc(0x82, entry))
        if hourly_items:
            tlv += tlv_enc(0x81, b"".join(hourly_items))

        daily = list(payload.get("daily") or [])
        today = {
            "timestamp": now,
            "condition_code": payload.get("condition_code", 800),
            "max_temp_c": payload.get("high_temp_c"),
            "min_temp_c": payload.get("low_temp_c"),
        }
        if daily and daily[0].get("timestamp"):
            current_utc_day = datetime.fromtimestamp(now, timezone.utc).date()
            first_utc_day = datetime.fromtimestamp(int(daily[0]["timestamp"]), timezone.utc).date()
            if first_utc_day == current_utc_day:
                today.update({k: v for k, v in daily[0].items() if k in {
                    "sunrise", "sunset", "moonrise", "moonset", "moon_phase",
                } and v is not None})
                daily = daily[1:]
            elif first_utc_day < current_utc_day:
                daily = [item for item in daily if item.get("timestamp") and datetime.fromtimestamp(int(item["timestamp"]), timezone.utc).date() > current_utc_day]
        day_sources = [today]
        for offset, item in enumerate(daily[:7], start=1):
            future = dict(item)
            # Gadgetbridge uses current weather timestamp + N days for future
            # daily forecast rows, not midnight timestamps.
            future["timestamp"] = now + (86400 * offset)
            day_sources.append(future)
        day_items = []
        for item in day_sources[:8]:
            if not item.get("timestamp"):
                continue
            entry = tlv_enc(0x12, self._i32(item["timestamp"]))
            if settings.get("icon"):
                entry += tlv_enc(0x13, self._byte(self._weather_icon_byte(int(item.get("condition_code") or 800))))
            if settings.get("temperature"):
                max_temp = item.get("max_temp_c", item.get("high_temp_c"))
                min_temp = item.get("min_temp_c", item.get("low_temp_c"))
                if max_temp is not None:
                    entry += tlv_enc(0x14, self._i8(max_temp))
                if min_temp is not None:
                    entry += tlv_enc(0x15, self._i8(min_temp))
            if settings.get("sun_moon"):
                for src, tag in (("sunrise", 0x16), ("sunset", 0x17), ("moonrise", 0x1A), ("moonset", 0x1B)):
                    if item.get(src) and self._same_utc_day_timestamp(item.get(src), item["timestamp"]):
                        entry += tlv_enc(tag, self._i32(item[src]))
            if settings.get("moon_phase") and item.get("moon_phase") is not None:
                phase = max(1, min(8, int(item["moon_phase"])))
                entry += tlv_enc(0x1E, self._byte(phase))
            day_items.append(tlv_enc(0x91, entry))
        if day_items:
            tlv += tlv_enc(0x90, b"".join(day_items))
        return tlv

    async def _weather_device_request_ack(self, status: int = RESULT_SUCCESS):
        await self._send(SVC_WEATHER, WEATHER_DEVICE_REQUEST, tlv_enc(0x01, struct.pack(">I", status)))

    async def _send_current_gps_time_for_weather(self, payload: dict) -> dict | None:
        lat = payload.get("latitude")
        lon = payload.get("longitude")
        if lat is None or lon is None:
            return None
        try:
            lat = round(float(lat), 7)
            lon = round(float(lon), 7)
        except (TypeError, ValueError):
            return None

        # Gadgetbridge gates this on command 0x18/0x06 but sends CurrentGPSRequest
        # on 0x18/0x07. Mirror that quirk and keep failure non-fatal for weather.
        if self.commands_per_service and not self._supports_command(SVC_GPS_TIME, 0x06):
            return {"status": "unsupported", "reason": "gps/time support command 0x06 not advertised"}

        timestamp = int(time.time()) - 86400
        tlv = (
            tlv_enc(0x01, self._i32(timestamp)) +
            tlv_enc(0x02, self._f64_le(lat)) +
            tlv_enc(0x03, self._f64_le(lon))
        )
        try:
            response = await self._transact_encrypted(SVC_GPS_TIME, GPS_CURRENT, tlv, timeout=8.0)
            return {
                "status": "ok",
                "latitude": lat,
                "longitude": lon,
                "timestamp": timestamp,
                "tlvs": {hex(k): v.hex() for k, v in response.items()},
            }
        except Exception as e:
            return {"status": "failed", "error": repr(e), "latitude": lat, "longitude": lon}

    async def send_weather_update(self, payload: dict) -> dict:
        if not self.secret_key:
            raise RuntimeError("Cannot send weather before authenticated encrypted session")
        if not payload:
            raise ValueError("Weather payload is empty")

        payload = dict(payload)
        self._last_weather_payload = payload
        if self._weather_push_active:
            raise RuntimeError("Weather push already in progress")
        self._weather_push_active = True
        settings = self._weather_support_defaults()
        try:
            settings["uv_index"] = self._supports_expand_capability(0x2F) or settings["uv_index"]
            settings["extended_hourly"] = self._supports_expand_capability(0xC0) or settings["extended_hourly"]
            status = {
                "started_at": int(time.time()),
                "started_at_local": local_time_label(int(time.time())),
                "location": payload.get("location"),
                "steps": [],
                "settings": settings,
            }

            logger.info("Weather push: start")
            start = await self._transact_encrypted(SVC_WEATHER, WEATHER_START, tlv_enc(0x01, b"\x03"), timeout=8.0)
            start_code = self._tlv_int(start.get(0x7F, b""))
            status["steps"].append({"name": "start", "code": start_code, "tlvs": {hex(k): v.hex() for k, v in start.items()}})
            if start_code not in (RESULT_SUCCESS, RESULT_WEATHER_ALREADY_STARTED):
                save_json_artifact("latest_weather_push.json", status)
                raise RuntimeError(f"WeatherStart failed: {start_code:#x}")

            if self._supports_command(SVC_WEATHER, WEATHER_UNIT):
                unit = await self._transact_encrypted(SVC_WEATHER, WEATHER_UNIT, tlv_enc(0x01, b"\x00"), timeout=8.0)
                status["steps"].append({"name": "unit", "tlvs": {hex(k): v.hex() for k, v in unit.items()}})

            basic = await self._transact_encrypted(SVC_WEATHER, WEATHER_SUPPORT, tlv_enc(0x01), timeout=8.0)
            self._weather_parse_basic_support(settings, basic)
            status["steps"].append({"name": "support", "bitmap": basic.get(0x01, b"").hex()})

            if self._supports_command(SVC_WEATHER, WEATHER_EXTENDED_SUPPORT):
                extended = await self._transact_encrypted(SVC_WEATHER, WEATHER_EXTENDED_SUPPORT, tlv_enc(0x01), timeout=8.0)
                self._weather_parse_extended_support(settings, extended)
                status["steps"].append({"name": "extended_support", "bitmap": extended.get(0x01, b"").hex()})

            if self._supports_command(SVC_WEATHER, WEATHER_SUN_MOON_SUPPORT):
                sun_moon = await self._transact_encrypted(SVC_WEATHER, WEATHER_SUN_MOON_SUPPORT, tlv_enc(0x01), timeout=8.0)
                self._weather_parse_sun_moon_support(settings, sun_moon)
                status["steps"].append({"name": "sun_moon_support", "bitmap": sun_moon.get(0x01, b"").hex()})

            current_tlv = self._weather_current_tlv(payload, settings)
            current = await self._transact_encrypted(SVC_WEATHER, WEATHER_CURRENT, current_tlv, timeout=10.0)
            status["steps"].append({"name": "current", "tlvs": {hex(k): v.hex() for k, v in current.items()}})

            gps_time = await self._send_current_gps_time_for_weather(payload)
            if gps_time:
                status["steps"].append({"name": "gps_time", **gps_time})

            if self._supports_command(SVC_WEATHER, WEATHER_FORECAST):
                forecast_tlv = self._weather_forecast_tlv(payload, settings)
                if forecast_tlv:
                    forecast = await self._transact_encrypted(SVC_WEATHER, WEATHER_FORECAST, forecast_tlv, timeout=15.0)
                    status["steps"].append({"name": "forecast", "tlvs": {hex(k): v.hex() for k, v in forecast.items()}})

            status["finished_at"] = int(time.time())
            status["finished_at_local"] = local_time_label(status["finished_at"])
            status["settings"] = settings
            save_json_artifact("latest_weather_push.json", status)
            logger.info("Weather push: complete")
            return status
        finally:
            self._weather_push_active = False

    @staticmethod
    def _watchface_type_flags(type_byte: int, expanded_type: int = 0) -> dict:
        return {
            "current": bool(type_byte & 0x01),
            "factory": bool(type_byte & 0x02),
            "editable": bool(type_byte & 0x08),
            "video": bool(type_byte & 0x10),
            "photo": bool(type_byte & 0x20),
            "tryout": bool(type_byte & 0x40),
            "kaleidoscope": bool(type_byte & 0x80),
            "expanded_type": expanded_type,
        }

    async def get_watchface_params(self) -> dict:
        tlv = b"".join(tlv_enc(tag) for tag in (0x01, 0x02, 0x03, 0x04, 0x05, 0x0E, 0x0F))
        resp = await self._transact_encrypted(SVC_WATCHFACE, WATCHFACE_PARAMS, tlv, timeout=10.0)
        return {
            "max_version": self._tlv_str(resp.get(0x01, b"")),
            "width": self._tlv_int(resp.get(0x02, b"")),
            "height": self._tlv_int(resp.get(0x03, b"")),
            "support_file_type": resp.get(0x04, b"\x00")[0] if resp.get(0x04) else None,
            "sort": resp.get(0x05, b"\x00")[0] if resp.get(0x05) else None,
            "other_watchface_versions": self._tlv_str(resp.get(0x06, b"")) if 0x06 in resp else "",
            "raw": {hex(k): v.hex() for k, v in resp.items()},
        }

    async def get_watchfaces_list(self) -> list[dict]:
        tlv = tlv_enc(0x01) + tlv_enc(0x06, b"\x03")
        resp = await self._transact_encrypted(SVC_WATCHFACE, WATCHFACE_LIST, tlv, timeout=10.0)
        faces = []
        for tag, value in tlv_items(resp.get(0x81, b"")):
            if tag != 0x82:
                continue
            item = tlv_dec(value)
            file_name = self._tlv_str(item.get(0x03, b""))
            version = self._tlv_str(item.get(0x04, b""))
            type_byte = item.get(0x05, b"\x00")[0] if item.get(0x05) else 0
            expanded_type = item.get(0x07, b"\x00")[0] if item.get(0x07) else 0
            faces.append({
                "file_name": file_name,
                "version": version,
                "type": type_byte,
                **self._watchface_type_flags(type_byte, expanded_type),
            })
        return faces

    async def get_watchfaces_names(self, faces: list[dict]) -> dict:
        if not faces:
            return {}
        entries = []
        for face in faces:
            file_name = face.get("file_name")
            if file_name:
                entries.append(tlv_enc(0x83, tlv_enc(0x04, file_name.encode("utf-8"))))
        if not entries:
            return {}
        tlv = tlv_enc(0x01, b"\x01") + tlv_enc(0x82, b"".join(entries))
        resp = await self._transact_encrypted(SVC_WATCHFACE, WATCHFACE_NAMES, tlv, timeout=10.0)
        names = {}
        for tag, value in tlv_items(resp.get(0x82, b"")):
            if tag != 0x83:
                continue
            item = tlv_dec(value)
            file_name = self._tlv_str(item.get(0x04, b""))
            display_name = self._tlv_str(item.get(0x05, b""))
            if file_name:
                names[file_name] = display_name
        return names

    async def get_watchface_inventory(self) -> dict:
        if not self._supports_command(SVC_WATCHFACE, WATCHFACE_PARAMS):
            result = {"supported": False, "reason": "watchface params command not advertised"}
            save_json_artifact("latest_watchfaces.json", result)
            return result
        params = await self.get_watchface_params()
        faces = []
        names = {}
        if self._supports_command(SVC_WATCHFACE, WATCHFACE_LIST):
            faces = await self.get_watchfaces_list()
        if faces and self._supports_command(SVC_WATCHFACE, WATCHFACE_NAMES):
            names = await self.get_watchfaces_names(faces)
            for face in faces:
                if face.get("file_name") in names:
                    face["display_name"] = names[face["file_name"]]
        result = {
            "supported": True,
            "generated_at": int(time.time()),
            "generated_at_local": local_time_label(int(time.time())),
            "params": params,
            "installed": faces,
            "names": names,
        }
        save_json_artifact("latest_watchfaces.json", result)
        return result

    async def activate_watchface(self, file_name: str, version: str | None = None) -> dict:
        if not self._supports_command(SVC_WATCHFACE, WATCHFACE_OPERATION):
            result = {
                "supported": False,
                "operation": "activate",
                "reason": "watchface operation command not advertised",
                "generated_at": int(time.time()),
            }
            save_json_artifact("latest_watchface_operation.json", result)
            return result
        if not file_name:
            raise ValueError("watchface file_name is required")

        if "_" in file_name and version is None:
            file_name, version = file_name.split("_", 1)
        version = version or ""
        tlv = (
            tlv_enc(0x01, file_name.encode("utf-8")) +
            tlv_enc(0x02, version.encode("utf-8")) +
            tlv_enc(0x03, b"\x01")
        )
        response = await self._transact_encrypted(SVC_WATCHFACE, WATCHFACE_OPERATION, tlv, timeout=12.0)
        result = {
            "supported": True,
            "operation": "activate",
            "file_name": file_name,
            "version": version,
            "generated_at": int(time.time()),
            "generated_at_local": local_time_label(int(time.time())),
            "tlvs": {hex(k): v.hex() for k, v in response.items()},
        }
        save_json_artifact("latest_watchface_operation.json", result)
        try:
            await self.get_watchface_inventory()
        except Exception as e:
            result["refresh_error"] = repr(e)
            save_json_artifact("latest_watchface_operation.json", result)
        return result

    @staticmethod
    def _stress_seed_from_preview(preview: dict) -> dict | None:
        rows = preview.get("stress") or []
        if not rows:
            return None
        latest = rows[-1]
        features = [float(v) for v in (latest.get("features") or [])]
        if len(features) < 10:
            return None
        features = (features + [0.0, 0.0])[:12]
        score = int(latest.get("score") or 0)
        end_ms = int(latest.get("end_ms") or 0)
        if score <= 0 or end_ms <= 0:
            return None
        return {
            "source": "latest_stress_preview",
            "features": features,
            "score": score,
            "timestamp": end_ms // 1000,
        }

    @staticmethod
    def _stress_seed_from_live(live: dict) -> dict | None:
        stress = live.get("huawei_stress") or {}
        features = [float(v) for v in (stress.get("features") or [])]
        score = int(stress.get("stress_score") or 0)
        if stress.get("status") != "ok" or len(features) < 10 or score <= 0:
            return None
        features = (features + [0.0, 0.0])[:12]
        return {
            "source": "latest_live_hrv",
            "features": features,
            "score": score,
            "timestamp": int(live.get("generated_at") or time.time()),
        }

    @classmethod
    def _load_stress_seed(cls) -> dict | None:
        try:
            preview = json.loads((DATA_DIR / "latest_stress_preview.json").read_text(encoding="utf-8"))
            seed = cls._stress_seed_from_preview(preview)
            if seed:
                return seed
        except Exception:
            pass
        try:
            live = json.loads((DATA_DIR / "latest_live_hrv.json").read_text(encoding="utf-8"))
            seed = cls._stress_seed_from_live(live)
            if seed:
                return seed
        except Exception:
            pass
        return None

    async def set_automatic_stress(self, enabled: bool = True, seed: dict | None = None) -> dict:
        """Enable/disable Huawei automatic stress using Gadgetbridge's svc=0x20/cmd=0x09."""
        if not self._supports_command(SVC_STRESS, CMD_STRESS_AUTO):
            result = {
                "supported": False,
                "enabled": enabled,
                "reason": "stress auto command not advertised",
                "generated_at": int(time.time()),
            }
            save_json_artifact("latest_stress_settings.json", result)
            return result

        status = 0x01 if enabled else 0x02
        tlv = tlv_enc(0x01, bytes([status]))
        seed_source = None
        if enabled:
            seed = seed or self._load_stress_seed()
            if not seed:
                raise RuntimeError("No stress seed available; run a 60-second live RRI measurement or sync rrisqi_data.bin first")
            features = [float(v) for v in seed["features"][:12]]
            if len(features) != 12:
                raise RuntimeError(f"stress seed must contain 12 features, got {len(features)}")
            score = max(1, min(99, int(seed["score"])))
            timestamp = int(seed.get("timestamp") or time.time())
            tlv += (
                tlv_enc(0x02, bytes([score])) +
                tlv_enc(0x03, b"".join(struct.pack(">f", value) for value in features)) +
                tlv_enc(0x04, struct.pack(">I", timestamp))
            )
            seed_source = seed.get("source")

        await self._send_encrypted_no_wait(SVC_STRESS, CMD_STRESS_AUTO, tlv)
        await self._drain_unsolicited_for(1.0)
        result = {
            "supported": True,
            "enabled": enabled,
            "status_byte": status,
            "seed_source": seed_source,
            "generated_at": int(time.time()),
            "generated_at_local": local_time_label(int(time.time())),
        }
        save_json_artifact("latest_stress_settings.json", result)
        return result

    async def calibrate_and_enable_stress(self, duration: float = 62.0) -> dict:
        live = await self.measure_live_hrv(duration=duration)
        seed = self._stress_seed_from_live(live)
        if not seed:
            result = {
                "enabled": False,
                "generated_at": int(time.time()),
                "reason": "live RRI measurement did not produce a valid Huawei stress seed",
                "live_status": (live.get("huawei_stress") or {}).get("status"),
            }
            save_json_artifact("latest_stress_settings.json", result)
            return result
        return await self.set_automatic_stress(True, seed=seed)

    async def post_auth_initialize(self):
        logger.info("Post-auth init: starting Huawei connected-state bootstrap...")

        async def run_step(label: str, coro):
            try:
                result = await coro
                logger.info(f"Post-auth init: {label} OK")
                return result
            except asyncio.TimeoutError:
                logger.warning(f"Post-auth init: {label} timed out")
            except Exception as e:
                logger.warning(f"Post-auth init: {label} failed: {e!r}")
            return None

        await run_step("product info", self.get_product_info())
        await run_step("time sync", self.set_time())
        battery = await run_step("battery", self.get_battery())
        if battery is not None:
            logger.info(f"Battery level: {battery}%")
        await run_step("supported services", self.get_supported_services())
        await run_step("supported commands", self.get_supported_commands())
        await run_step("expand capabilities", self.get_expand_capabilities())
        if self._supports_command(SVC_ACCOUNT, CMD_EXTENDED_ACCOUNT) and self._supports_command(SVC_ACCOUNT, CMD_ACCOUNT_SWITCH):
            await run_step("extended account", self.send_extended_account())
        await run_step("setting related", self.get_setting_related())
        if self._supports_command(SVC_DEV, CMD_ACCEPT_AGREEMENT):
            await run_step("accept agreements", self.send_accept_agreements())
        if self.capability_flags.get("reverse_capabilities"):
            await run_step("reverse capabilities", self.send_reverse_capabilities())
        if self.capability_flags.get("multi_device"):
            await run_step("setup device status", self.send_setup_device_status())
            wear = await run_step("wear status", self.get_wear_status())
            if wear is not None:
                logger.info(f"Wear status TLVs: { {hex(k): v.hex() for k, v in wear.items()} }")
        else:
            logger.info("Post-auth init: skipping multi-device status/wear requests")
        connect = await run_step("connect status", self.get_connect_status())
        if connect is not None:
            logger.info(f"Connect status TLVs: { {hex(k): v.hex() for k, v in connect.items()} }")
        await run_step("feature config upload", self.send_feature_config_file_if_requested())
        if self.capability_flags.get("send_country_code") and self._supports_command(SVC_ACCOUNT, CMD_COUNTRY_CODE):
            site_id = 5 if self.capability_flags.get("send_site_id") else None
            await run_step("country code", self.send_country_code("IN", site_id=site_id))
        elif self.capability_flags.get("send_country_code"):
            logger.info("Post-auth init: skipping country code because 0x1a/0x0a is not advertised")
        logger.info("Post-auth init: complete")

    # ── Data queries ─────────────────────────────────────────────────────────

    # File sync (new 0x2c service)

    @staticmethod
    def _file_type_name(file_type: int) -> str:
        return {
            0x0E: "sleep_state",
            0x0F: "sleep_data",
            0x10: "rri",
            0x11: "gps",
            0x12: "pdr",
            0x16: "sequence_data",
            0x18: "ecg_analysis",
        }.get(file_type, f"unknown_{file_type:#x}")

    def _parse_incoming_file_init(self, tlvs: dict) -> dict:
        data = self._decrypt_transaction_tlvs(tlvs)
        return {
            "filename": self._tlv_str(data.get(0x01, b"")),
            "file_type": data.get(0x02, b"\x00")[0],
            "file_id": data.get(0x03, b"\x00")[0],
            "file_size": self._tlv_int(data.get(0x04, b"")),
            "description": self._tlv_str(data.get(0x07, b"")),
            "src_package": self._tlv_str(data.get(0x08, b"")),
            "dst_package": self._tlv_str(data.get(0x09, b"")),
            "src_fingerprint": self._tlv_str(data.get(0x0A, b"")),
            "dst_fingerprint": self._tlv_str(data.get(0x0B, b"")),
            "raw": {hex(k): v.hex() for k, v in data.items()},
        }

    async def _ack_incoming_file_init(self, init: dict, status: int = 0):
        tlv = (
            tlv_enc(0x01, init["filename"].encode("utf-8")) +
            tlv_enc(0x02, bytes([init["file_type"]])) +
            tlv_enc(0x03, bytes([init["file_id"]])) +
            tlv_enc(0x04, struct.pack(">I", init["file_size"]))
        )
        if init.get("src_package") and init.get("dst_package"):
            tlv += (
                tlv_enc(0x08, init["src_package"].encode("utf-8")) +
                tlv_enc(0x09, init["dst_package"].encode("utf-8")) +
                tlv_enc(0x0A, init.get("src_fingerprint", "").encode("utf-8")) +
                tlv_enc(0x0B, init.get("dst_fingerprint", "").encode("utf-8"))
            )
        tlv += tlv_enc(0x0D, bytes([status]))
        logger.info(
            f"ACK incoming file init: name={init['filename']!r} "
            f"id={init['file_id']:#x} size={init['file_size']} status={status}"
        )
        await self._send_encrypted_no_wait(SVC_FILE_DOWNLOAD, FILE_CMD_INCOMING_INIT, tlv)

    def _pop_pending_file_init(self, filename: str, file_type: int) -> dict | None:
        for i, init in enumerate(self._pending_file_inits):
            if init.get("filename") == filename and init.get("file_type") == file_type:
                return self._pending_file_inits.pop(i)
        return None

    async def _wait_for_incoming_file_init(self, filename: str, file_type: int,
                                           timeout: float = 4.0) -> dict | None:
        found = self._pop_pending_file_init(filename, file_type)
        if found:
            return found
        deadline = asyncio.get_running_loop().time() + timeout
        while True:
            while self._rx_queue:
                raw = self._rx_queue.popleft()
                svc, cmd, tlvs = parse_frame(raw)
                if svc == SVC_FILE_DOWNLOAD and cmd == FILE_CMD_INCOMING_INIT:
                    init = self._parse_incoming_file_init(tlvs)
                    if init.get("filename") == filename and init.get("file_type") == file_type:
                        return init
                    self._pending_file_inits.append(init)
                    continue
                if svc == SVC_DEV and cmd == CMD_PHONE_INFO:
                    await self._handle_phone_info_request(tlvs)
                else:
                    await self._handle_unsolicited(svc, cmd, tlvs, b"", b"", b"")
                found = self._pop_pending_file_init(filename, file_type)
                if found:
                    return found

            remaining = deadline - asyncio.get_running_loop().time()
            if remaining <= 0:
                return None
            self._event.clear()
            if self._rx_queue:
                continue
            try:
                await asyncio.wait_for(self._event.wait(), timeout=remaining)
            except asyncio.TimeoutError:
                return None

    def _parse_file_block_2c(self, payload: bytes) -> dict:
        if payload.startswith(b"\x7c"):
            plain = self._decrypt_transaction_payload(tlv_dec(payload))
        else:
            plain = payload
        if len(plain) < 6:
            raise ValueError(f"short file block payload: {plain.hex()}")
        return {
            "file_id": plain[0],
            "offset": struct.unpack(">I", plain[1:5])[0],
            "unknown": plain[5],
            "data": plain[6:],
        }

    async def download_file_2c(self, filename: str, file_type: int,
                               start_ts: int, end_ts: int,
                               dict_id: int = 0) -> bytes:
        init_tlv = (
            tlv_enc(0x01, filename.encode("utf-8")) +
            tlv_enc(0x02, bytes([file_type])) +
            tlv_enc(0x05, struct.pack(">I", start_ts)) +
            tlv_enc(0x06, struct.pack(">I", end_ts))
        )
        if dict_id and file_type == 0x16:
            init_tlv += tlv_enc(0x0C, struct.pack(">I", dict_id))

        logger.debug(f"File init request: name={filename!r} type={self._file_type_name(file_type)} "
                     f"start={start_ts} end={end_ts} dict_id={dict_id}")
        init = await self._transact_encrypted(SVC_FILE_DOWNLOAD, FILE_CMD_INIT, init_tlv, timeout=12.0)
        logger.debug(f"File init response tlvs: { {hex(k): v.hex() for k, v in init.items()} }")
        incoming = None
        status = self._tlv_int(init[0x7F]) if 0x7F in init else None
        if status is not None and status != 0x000186A0:
            raise RuntimeError(f"file init failed: {init[0x7F].hex()}")

        has_metadata = all(tag in init for tag in (0x01, 0x02, 0x03, 0x04))
        if has_metadata:
            resp_name = self._tlv_str(init.get(0x01, b""))
            resp_type = init.get(0x02, b"\x00")[0]
            file_id = init.get(0x03, b"\x00")[0]
            file_size = self._tlv_int(init.get(0x04, b""))
            if status == 0x000186A0:
                logger.debug(f"File init for {filename!r} included success status plus metadata")
        elif status == 0x000186A0:
            pending_timeout = 45.0 if file_type == FILE_TYPE_SEQUENCE_DATA else 6.0
            logger.info(
                f"File init returned success status only for {filename!r}; "
                f"waiting up to {pending_timeout:.0f}s for incoming metadata"
            )
            incoming = await self._wait_for_incoming_file_init(
                filename, file_type, timeout=pending_timeout
            )
            if not incoming:
                logger.info(
                    f"File init returned success status only for {filename!r}; no file metadata available"
                )
                return b""
            await self._ack_incoming_file_init(incoming, status=0)
            resp_name = incoming["filename"]
            resp_type = incoming["file_type"]
            file_id = incoming["file_id"]
            file_size = incoming["file_size"]
        else:
            raise RuntimeError(f"file init missing metadata: { {hex(k): v.hex() for k, v in init.items()} }")
        if incoming:
            logger.debug(f"File init metadata came via incoming init: {incoming['raw']}")
        logger.info(
            f"File init: name={resp_name!r} type={self._file_type_name(resp_type)} "
            f"id={file_id:#x} size={file_size}"
        )
        if file_size <= 0:
            complete_tlv = tlv_enc(0x01, bytes([file_id])) + tlv_enc(0x02, b"\x01")
            await self._send_encrypted_no_wait(SVC_FILE_DOWNLOAD, FILE_CMD_COMPLETE, complete_tlv)
            return b""

        info_tlv = (
            tlv_enc(0x01, bytes([file_id])) +
            tlv_enc(0x02) +
            tlv_enc(0x03) +
            tlv_enc(0x04) +
            tlv_enc(0x05)
        )
        info = await self._transact_encrypted(SVC_FILE_DOWNLOAD, FILE_CMD_INFO, info_tlv, timeout=12.0)
        if 0x7F in info:
            raise RuntimeError(f"file info failed: {info[0x7F].hex()}")
        timeout_s = info.get(0x02, b"\x0a")[0] if info.get(0x02) else 10
        max_block_size = self._tlv_int(info.get(0x04, b"")) or 4096
        no_encrypt = bool(info.get(0x05, b"\x00")[0]) if info.get(0x05) else False
        logger.info(
            f"File info: id={info.get(0x01, b'').hex()} timeout={timeout_s}s "
            f"max_block={max_block_size} no_encrypt={no_encrypt}"
        )

        out = bytearray(file_size)
        offset = 0
        while offset < file_size:
            block_size = min(file_size - offset, max_block_size)
            block_req = (
                tlv_enc(0x01, bytes([file_id])) +
                tlv_enc(0x02, struct.pack(">I", offset)) +
                tlv_enc(0x03, struct.pack(">I", block_size))
            )
            if dict_id and file_id == FILE_TYPE_SEQUENCE_DATA:
                block_req += tlv_enc(0x04, struct.pack(">I", dict_id))
            if no_encrypt:
                await self._send(SVC_FILE_DOWNLOAD, FILE_CMD_REQUEST_BLOCK, block_req)
            else:
                await self._send_encrypted_no_wait(SVC_FILE_DOWNLOAD, FILE_CMD_REQUEST_BLOCK, block_req)

            received = 0
            while received < block_size:
                payload = await self._recv_payload(
                    SVC_FILE_DOWNLOAD,
                    FILE_CMD_BLOCK,
                    timeout=max(float(timeout_s), 8.0),
                )
                block = self._parse_file_block_2c(payload)
                if block["file_id"] != file_id:
                    logger.debug(f"Skipping file block for id={block['file_id']:#x}")
                    continue
                pos = block["offset"]
                chunk = block["data"]
                if pos >= file_size:
                    raise RuntimeError(f"file block offset outside file: {pos} >= {file_size}")
                end = min(pos + len(chunk), file_size)
                out[pos:end] = chunk[:end - pos]
                received += end - pos
                logger.debug(f"File block: id={file_id:#x} offset={pos} bytes={end - pos}")

            offset += block_size

        complete_tlv = tlv_enc(0x01, bytes([file_id])) + tlv_enc(0x02, b"\x01")
        await self._send_encrypted_no_wait(SVC_FILE_DOWNLOAD, FILE_CMD_COMPLETE, complete_tlv)
        logger.info(f"File download complete: {filename} ({len(out)} bytes)")
        return bytes(out)

    @staticmethod
    def parse_rri_stress(data: bytes) -> dict:
        if len(data) < 48:
            return {"file_size": len(data), "version": None, "stress": []}
        declared_size = struct.unpack(">Q", data[0:8])[0]
        bitmap = struct.unpack(">H", data[8:10])[0]
        version = struct.unpack(">H", data[10:12])[0]
        stress = []
        if version == 3:
            pos = 48
            while pos + 66 <= len(data):
                entry = data[pos:pos + 66]
                pos += 66
                start = struct.unpack(">I", entry[0:4])[0]
                end = struct.unpack(">I", entry[4:8])[0]
                if not start or not end:
                    continue
                features = [struct.unpack(">f", entry[18 + i * 4:22 + i * 4])[0] for i in range(12)]
                stress.append({
                    "start_ms": start * 1000,
                    "end_ms": end * 1000,
                    "start_local": local_time_label(start),
                    "end_local": local_time_label(end),
                    "algorithm": struct.unpack(">I", entry[8:12])[0],
                    "score": entry[12],
                    "level": entry[13],
                    "c_flag": entry[14],
                    "type": entry[15],
                    "acc_flag": entry[16],
                    "ppg_flag": entry[17],
                    "features": [round(f, 6) for f in features],
                })
        return {
            "file_size": len(data),
            "declared_size": declared_size,
            "bitmap": bitmap,
            "version": version,
            "stress": stress,
        }

    @staticmethod
    def parse_trusleep_state(data: bytes) -> dict:
        sessions = []
        for pos in range(0, len(data) - (len(data) % 16), 16):
            start, end = struct.unpack_from("<II", data, pos)
            if not start or not end:
                continue
            sessions.append({
                "start_ts": start,
                "end_ts": end,
                "start_local": local_time_label(start),
                "end_local": local_time_label(end),
                "minutes": max(0, round((end - start) / 60)),
                "raw_tail": data[pos + 8:pos + 16].hex(),
            })
        return {
            "file_size": len(data),
            "session_count": len(sessions),
            "sessions": sessions,
        }

    @staticmethod
    def _read_i16_le(data: bytes, pos: int) -> int:
        return struct.unpack_from("<h", data, pos)[0]

    @classmethod
    def _decode_trusleep_short_series(cls, count: int, data: bytes, pos: int) -> tuple[list[int], int]:
        if pos >= len(data) or count <= 0:
            return [], pos
        tag = data[pos]
        pos += 1
        values = []
        if tag == 0xAA:
            for _ in range(count):
                if pos + 2 > len(data):
                    break
                values.append(cls._read_i16_le(data, pos))
                pos += 2
        elif tag == 0xBB:
            if pos + 2 > len(data):
                return values, pos
            working = cls._read_i16_le(data, pos)
            pos += 2
            values.append(working)
            while len(values) < count and pos < len(data):
                delta = data[pos]
                pos += 1
                if delta == 0xFF:
                    if pos + 2 > len(data):
                        break
                    working = cls._read_i16_le(data, pos)
                    pos += 2
                else:
                    if delta >= 0x80:
                        delta -= 0x100
                    working += delta
                values.append(working)
        return values, pos

    @classmethod
    def _decode_trusleep_amp_series(cls, count: int, data: bytes, pos: int) -> tuple[list[int], int]:
        if pos >= len(data) or count <= 0:
            return [], pos
        tag = data[pos]
        pos += 1
        values = []
        if tag == 0xAA:
            for _ in range(count):
                if pos + 2 > len(data):
                    break
                values.append(cls._read_i16_le(data, pos))
                pos += 2
        elif tag == 0xBB:
            if pos + 2 > len(data):
                return values, pos
            offset = cls._read_i16_le(data, pos)
            pos += 2
            while len(values) < count and pos < len(data):
                delta = data[pos]
                pos += 1
                if delta == 0xFF:
                    if pos + 2 > len(data):
                        break
                    working = offset + cls._read_i16_le(data, pos)
                    pos += 2
                else:
                    if delta >= 0x80:
                        delta -= 0x100
                    working = offset + delta
                values.append(working)
        return values, pos

    @classmethod
    def parse_trusleep_data(cls, data: bytes, max_examples: int = 12) -> dict:
        acc_examples = []
        ppg_examples = []
        acc_count = 0
        ppg_count = 0
        errors = []
        pos = 0
        while pos < len(data):
            tag = data[pos]
            pos += 1
            if tag not in (0x01, 0x02):
                continue
            if tag == 0x01:
                if pos >= len(data):
                    break
                length = data[pos]
                pos += 1
                chunk = data[pos:pos + length]
                pos += min(length, len(data) - pos)
                if len(chunk) < 6:
                    errors.append(f"short ACC record len={len(chunk)}")
                    continue
                ts = struct.unpack_from("<I", chunk, 0)[0]
                flags = struct.unpack_from("<H", chunk, 4)[0]
                acc_count += 1
                if len(acc_examples) < max_examples:
                    acc_examples.append({
                        "timestamp": ts,
                        "local": local_time_label(ts),
                        "flags": flags,
                        "raw": chunk.hex(),
                    })
            else:
                if pos + 2 > len(data):
                    break
                length = struct.unpack_from("<H", data, pos)[0]
                pos += 2
                chunk = data[pos:pos + length]
                pos += min(length, len(data) - pos)
                if len(chunk) < 6:
                    errors.append(f"short PPG record len={len(chunk)}")
                    continue
                start = struct.unpack_from("<I", chunk, 0)[0]
                count = chunk[4]
                peaks, amp_pos = cls._decode_trusleep_short_series(count, chunk, 5)
                amps, _ = cls._decode_trusleep_amp_series(count, chunk, amp_pos)
                decoded = min(len(peaks), len(amps))
                ppg_count += decoded
                for i in range(decoded):
                    if len(ppg_examples) >= max_examples:
                        break
                    sample_ms = start * 1000 + peaks[i] * 10
                    sample_ts = sample_ms // 1000
                    ppg_examples.append({
                        "timestamp_ms": sample_ms,
                        "local": local_time_label(sample_ts),
                        "amplitude": amps[i],
                    })
                if decoded != count:
                    errors.append(f"PPG decoded {decoded}/{count} samples")
        return {
            "file_size": len(data),
            "acc_count": acc_count,
            "ppg_count": ppg_count,
            "acc_examples": acc_examples,
            "ppg_examples": ppg_examples,
            "errors": errors[:20],
        }

    @staticmethod
    def _read_sequence_value(data_type: int, value: bytes):
        if not value:
            return None
        if value == b"\xBF\x80\x00\x00":
            return None
        if data_type in (1, 2, 4):
            if len(value) > 4:
                return None
            return int.from_bytes(value, "big", signed=False)
        if data_type == 3:
            if len(value) > 8:
                return None
            return int.from_bytes(value, "big", signed=False)
        if data_type == 5:
            return value.decode("utf-8", errors="replace")
        if data_type == 6:
            if len(value) != 8:
                return None
            return struct.unpack(">d", value)[0]
        return value.hex()

    @classmethod
    def _parse_sleep_sequence_summary(cls, summary: bytes) -> dict:
        parsed = {}
        raw_fields = []
        for _container_tag, container in tlv_items(summary):
            for _item_tag, item in tlv_items(container):
                fields = tlv_dec(item)
                dict_id = cls._tlv_int(fields.get(0x03, b""))
                data_type = cls._tlv_int(fields.get(0x04, b""))
                value = cls._read_sequence_value(data_type, fields.get(0x05, b""))
                name = SLEEP_DETAILS_FIELD_IDS.get(dict_id, f"unknown_{dict_id}")
                parsed[name] = value
                raw_fields.append({
                    "dict_id": dict_id,
                    "field": name,
                    "data_type": data_type,
                    "value": value,
                })
        parsed["_raw_fields"] = raw_fields
        if parsed.get("validData") is None:
            for field in (
                "bedTime", "risingTime", "sleepScore", "sleepDataQuality",
                "deepPart", "snoreFreq", "sleepEfficiency", "sleepLatency",
                "prepareSleepTime",
            ):
                parsed.setdefault(field, None)
        return parsed

    @staticmethod
    def _minute_aligned(ts: int | None) -> int | None:
        if ts is None:
            return None
        return (ts // 60) * 60

    @classmethod
    def _parse_sleep_sequence_details(cls, details: bytes, start_time: int | None) -> dict:
        stages = []
        stage_counts = {}
        errors = []
        if start_time is None:
            return {"stage_minute_count": 0, "stage_counts": {}, "stages": [], "errors": ["missing start time"]}
        if len(details) % 8 != 0:
            errors.append(f"detail length {len(details)} is not a multiple of 8")
        pos = 0
        cursor = start_time
        while pos + 8 <= len(details):
            duration = struct.unpack_from("<I", details, pos)[0]
            stage = details[pos + 4]
            pos += 8
            if duration == 0 or stage == 0 or duration % 60 != 0:
                errors.append(f"invalid detail duration={duration} stage={stage} pos={pos}")
                continue
            minutes = duration // 60
            if minutes > 1440:
                errors.append(f"detail duration too large: {minutes} minutes pos={pos}")
                continue
            stage_counts[str(stage)] = stage_counts.get(str(stage), 0) + minutes
            for _ in range(minutes):
                stages.append({
                    "timestamp": cursor,
                    "local": local_time_label(cursor),
                    "stage": stage,
                })
                cursor += 60
        return {
            "stage_minute_count": len(stages),
            "stage_counts": stage_counts,
            "stages": stages[:240],
            "stage_window_start": local_time_label(stages[0]["timestamp"]) if stages else None,
            "stage_window_end": local_time_label(stages[-1]["timestamp"]) if stages else None,
            "errors": errors[:20],
        }

    @classmethod
    def parse_sleep_sequence_data(cls, data: bytes) -> dict:
        if len(data) < 32:
            return {
                "file_size": len(data),
                "dict_id": None,
                "file_type": None,
                "file_version": None,
                "sequence_count": 0,
                "sessions": [],
                "errors": ["file too short"],
            }

        errors = []
        data_size = struct.unpack_from(">I", data, 0)[0]
        dict_id = struct.unpack_from(">I", data, 4)[0]
        file_type = struct.unpack_from(">H", data, 8)[0]
        file_version = struct.unpack_from(">H", data, 10)[0]
        extra = data[12:32]
        pos = 32
        sessions = []
        supports_bed_time = True
        while pos + 32 <= len(data):
            start_time = struct.unpack_from(">I", data, pos)[0]
            end_time = struct.unpack_from(">I", data, pos + 4)[0]
            data_version = data[pos + 8]
            summary_type = data[pos + 9]
            summary_len = struct.unpack_from(">H", data, pos + 10)[0]
            detail_len = struct.unpack_from(">I", data, pos + 12)[0]
            record_extra = data[pos + 16:pos + 32]
            pos += 32

            summary = b""
            if data_version == 1:
                if pos + summary_len > len(data):
                    errors.append(f"summary length {summary_len} exceeds remaining at {pos}")
                    break
                summary = data[pos:pos + summary_len]
                pos += summary_len
            else:
                errors.append(f"unsupported data version {data_version} at sequence {len(sessions)}")

            details = b""
            if detail_len > 0:
                if detail_len > 20 * 1025 * 1024:
                    errors.append(f"detail length {detail_len} too large at sequence {len(sessions)}")
                    break
                if pos + detail_len > len(data):
                    errors.append(f"detail length {detail_len} exceeds remaining at {pos}")
                    break
                details = data[pos:pos + detail_len]
                pos += detail_len

            summary_obj = {}
            if data_version == 1 and summary_type == 2 and file_type != 2 and summary:
                try:
                    summary_obj = cls._parse_sleep_sequence_summary(summary)
                except Exception as e:
                    errors.append(f"summary parse failed at sequence {len(sessions)}: {e!r}")

            bed_time = summary_obj.get("bedTime")
            fall_asleep = summary_obj.get("fallAsleepTime")
            valid_data = summary_obj.get("validData")
            base_time = bed_time if supports_bed_time and valid_data is not None else fall_asleep
            base_time = cls._minute_aligned(base_time if isinstance(base_time, int) else None)
            stages = cls._parse_sleep_sequence_details(details, base_time)
            sessions.append({
                "start_ts": start_time,
                "end_ts": end_time,
                "start_local": local_time_label(start_time) if start_time else None,
                "end_local": local_time_label(end_time) if end_time else None,
                "data_version": data_version,
                "summary_type": summary_type,
                "summary_len": summary_len,
                "detail_len": detail_len,
                "extra": record_extra.hex(),
                "summary": summary_obj,
                "details": stages,
            })

        if pos < len(data):
            errors.append(f"{len(data) - pos} trailing bytes")

        return {
            "file_size": len(data),
            "declared_data_size": data_size,
            "dict_id": dict_id,
            "file_type": file_type,
            "file_version": file_version,
            "extra": extra.hex(),
            "sequence_count": len(sessions),
            "sessions": sessions,
            "errors": errors[:20],
        }

    async def get_sleep_sequence_preview(self, hours: int = 168) -> dict:
        end_ts = int(time.time())
        start_ts = end_ts - hours * 3600
        raw = await self.download_file_2c(
            "sequence_data",
            FILE_TYPE_SEQUENCE_DATA,
            start_ts,
            end_ts,
            dict_id=DICT_SLEEP_DETAILS_CLASS,
        )
        save_binary_artifact("latest_sleep_sequence_data.bin", raw)
        parsed = self.parse_sleep_sequence_data(raw)
        parsed["hours"] = hours
        parsed["route"] = "sequence_data/SLEEP_DETAILS"
        save_json_artifact("latest_sleep_sequence_preview.json", parsed)
        return parsed

    async def get_recent_trusleep_preview(self, hours: int = 168) -> dict:
        end_ts = int(time.time())
        start_ts = end_ts - hours * 3600
        if self.capability_flags.get("dict_sleep_sync"):
            sequence = await self.get_sleep_sequence_preview(hours=hours)
            result = {
                "hours": hours,
                "route": "sequence_data/SLEEP_DETAILS",
                "state": {"file_size": 0, "session_count": sequence.get("sequence_count", 0), "sessions": []},
                "data": {"file_size": sequence.get("file_size", 0), "acc_count": 0, "ppg_count": 0},
                "sequence": sequence,
            }
            save_json_artifact("latest_trusleep_preview.json", result)
            if sequence.get("file_size", 0) > 0 or os.getenv("BAND10_TRUSLEEP_LEGACY_FALLBACK") != "1":
                return result

        state_raw = await self.download_file_2c(
            "sleep_state.bin",
            FILE_TYPE_SLEEP_STATE,
            start_ts,
            end_ts,
        )
        save_binary_artifact("latest_trusleep_state.bin", state_raw)
        data_raw = await self.download_file_2c(
            "sleep_data.bin",
            FILE_TYPE_SLEEP_DATA,
            start_ts,
            end_ts,
        )
        save_binary_artifact("latest_trusleep_data.bin", data_raw)
        parsed = {
            "hours": hours,
            "state": self.parse_trusleep_state(state_raw),
            "data": self.parse_trusleep_data(data_raw),
        }
        save_json_artifact("latest_trusleep_preview.json", parsed)
        return parsed

    async def get_recent_stress_preview(self, hours: int = 24) -> dict:
        end_ts = int(time.time())
        start_ts = end_ts - hours * 3600
        raw = await self.download_file_2c(
            "rrisqi_data.bin",
            FILE_TYPE_RRI,
            start_ts,
            end_ts,
        )
        save_binary_artifact("latest_rri.bin", raw)
        parsed = self.parse_rri_stress(raw)
        scores = [row["score"] for row in parsed.get("stress", [])]
        parsed["hours"] = hours
        parsed["stress_count"] = len(scores)
        if scores:
            parsed["stress_min"] = min(scores)
            parsed["stress_avg"] = round(sum(scores) / len(scores), 1)
            parsed["stress_max"] = max(scores)
        save_json_artifact("latest_stress_preview.json", parsed)
        return parsed

    def _parse_realtime_hr_packet(self, tlvs: dict) -> list[dict]:
        """Parse Huawei Health's realtime HR notifications (svc=0x19/cmd=0x03)."""
        data = self._decrypt_or_raw_tlvs(tlvs)

        if 0x7F in data:
            status = self._tlv_int(data[0x7F])
            event = {
                "timestamp": int(time.time()),
                "svc": SVC_HR_RRI_TEST,
                "cmd": CMD_HR_REALTIME_DATA,
                "status": status,
                "tlvs": {hex(k): v.hex() for k, v in data.items()},
            }
            self._live_rri_events.append(event)
            logger.info(f"  Realtime HR status: {event}")
            return []

        samples = []
        now = int(time.time())
        container = data.get(0x01) or data.get(0x81) or b""
        for tag, payload in tlv_items(container):
            if tag not in (0x02, 0x82):
                continue
            item = tlv_dec(payload)
            hr = self._tlv_int(item.get(0x03, b""))
            ts = self._tlv_int(item.get(0x04, b"")) or now
            credibility = self._tlv_int(item.get(0x05, b"")) if 0x05 in item else None
            if hr:
                samples.append({
                    "timestamp": ts,
                    "heart_rate": hr,
                    "credibility": credibility,
                })
        if samples:
            self._live_rri_events.append({
                "timestamp": now,
                "svc": SVC_HR_RRI_TEST,
                "cmd": CMD_HR_REALTIME_DATA,
                "sample_count": len(samples),
                "latest_hr": samples[-1]["heart_rate"],
            })
        else:
            self._live_rri_events.append({
                "timestamp": now,
                "svc": SVC_HR_RRI_TEST,
                "cmd": CMD_HR_REALTIME_DATA,
                "sample_count": 0,
                "tlvs": {hex(k): v.hex() for k, v in data.items()},
            })
        return samples

    def _parse_live_rri_packet(self, tlvs: dict) -> list[dict]:
        """Parse HrRriTest.RriData notifications (svc=0x19/cmd=0x05)."""
        data = self._decrypt_or_raw_tlvs(tlvs)

        if 0x7F in data:
            status = self._tlv_int(data[0x7F])
            event = {
                "timestamp": int(time.time()),
                "svc": SVC_HR_RRI_TEST,
                "cmd": CMD_HR_RRI_DATA,
                "status": status,
                "tlvs": {hex(k): v.hex() for k, v in data.items()},
            }
            self._live_rri_events.append(event)
            logger.info(f"  Live RRI data status: {event}")
            return []

        container = data.get(0x82, b"")
        samples = []
        timestamp_ms = int(time.time() * 1000)
        for tag, payload in tlv_items(container):
            if tag != 0x83:
                continue
            item = tlv_dec(payload)
            rri = self._tlv_int(item.get(0x04, b""))
            sqi = item.get(0x05, b"\x00")[0] if item.get(0x05) else 0
            if rri:
                samples.append({
                    "timestamp_ms": timestamp_ms,
                    "rri_ms": rri,
                    "sqi": sqi,
                })
        return samples

    async def _drain_unsolicited_for(self, duration: float):
        deadline = asyncio.get_running_loop().time() + duration
        while True:
            while self._rx_queue:
                raw = self._rx_queue.popleft()
                svc, cmd, tlvs = parse_frame(raw)
                if svc == SVC_DEV and cmd == CMD_PHONE_INFO:
                    await self._handle_phone_info_request(tlvs)
                else:
                    await self._handle_unsolicited(svc, cmd, tlvs, b"", b"", b"")
            remaining = deadline - asyncio.get_running_loop().time()
            if remaining <= 0:
                return
            self._event.clear()
            if self._rx_queue:
                continue
            try:
                await asyncio.wait_for(self._event.wait(), timeout=min(remaining, 2.0))
            except asyncio.TimeoutError:
                continue

    async def measure_live_hrv(self, duration: float = 62.0) -> dict:
        """Open Gadgetbridge's live HR/RRI test stream and compute HRV locally."""
        if not self._supports_command(SVC_HR_RRI_TEST, CMD_HR_RRI_OPEN_CLOSE):
            result = {
                "source": "live_rri",
                "generated_at": int(time.time()),
                "sample_count": 0,
                "time_domain": {"status": "unsupported"},
                "huawei_stress": {"status": "unsupported"},
            }
            save_json_artifact("latest_live_hrv.json", result)
            return result

        self._live_rri_samples = []
        self._live_hr_samples = []
        self._live_rri_events = []
        open_type = int(os.getenv("BAND10_RRI_OPEN_TYPE", "3"), 0)
        close_type = int(os.getenv("BAND10_RRI_CLOSE_TYPE", "4"), 0)
        vol_status = os.getenv("BAND10_RRI_VOL_STATUS")
        open_tlv = tlv_enc(0x01, bytes([open_type & 0xFF]))
        if vol_status is not None:
            open_tlv += tlv_enc(0x02, bytes([int(vol_status, 0) & 0xFF]))
        start = time.time()
        logger.info(
            f"Live HRV: opening RRI stream for {duration:.0f}s "
            f"type={open_type:#x} vol_status={vol_status if vol_status is not None else 'unset'}"
        )
        await self._send_encrypted_no_wait(
            SVC_HR_RRI_TEST,
            CMD_HR_RRI_OPEN_CLOSE,
            open_tlv,
        )
        try:
            await self._drain_unsolicited_for(duration)
        finally:
            logger.info("Live HRV: closing RRI stream")
            await self._send_encrypted_no_wait(
                SVC_HR_RRI_TEST,
                CMD_HR_RRI_OPEN_CLOSE,
                tlv_enc(0x01, bytes([close_type & 0xFF])),
            )
            await self._drain_unsolicited_for(1.5)

        elapsed = max(1, round(time.time() - start))
        result = band_analytics.analyze_rri_samples(self._live_rri_samples, signal_time_sec=elapsed)
        result["request"] = {
            "open_type": open_type,
            "close_type": close_type,
            "vol_status": None if vol_status is None else int(vol_status, 0),
        }
        result["transport_events"] = self._live_rri_events
        result["realtime_hr_sample_count"] = len(self._live_hr_samples)
        result["realtime_hr_preview"] = self._live_hr_samples[-10:]
        save_json_artifact("latest_live_hrv.json", result)
        append_jsonl_artifact("live_hrv_history.jsonl", result)
        logger.info(
            "Live HRV: "
            f"samples={result.get('sample_count', 0)} "
            f"rmssd={result.get('time_domain', {}).get('rmssd_ms', 'n/a')} "
            f"stress={result.get('huawei_stress', {}).get('stress_score', 'n/a')}"
        )
        return result

    async def enable_passive_health_settings(self) -> dict:
        """Best-effort Gadgetbridge-style setting enables for richer future data."""
        results = {}

        async def send(label: str, svc: int, cmd: int, tlv: bytes, wait: bool = False):
            if self.commands_per_service and not self._supports_command(svc, cmd):
                results[label] = {"status": "unsupported"}
                return
            try:
                if wait:
                    resp = await self._transact_encrypted(svc, cmd, tlv, timeout=8.0)
                    results[label] = {"status": "ok", "tlvs": {hex(k): v.hex() for k, v in resp.items()}}
                else:
                    await self._send_encrypted_no_wait(svc, cmd, tlv)
                    await asyncio.sleep(0.2)
                    results[label] = {"status": "sent"}
            except Exception as e:
                results[label] = {"status": "failed", "error": repr(e)}

        await send("trusleep", SVC_FITNESS, CMD_TRUSLEEP, tlv_enc(0x01, b"\x01"), wait=True)
        await send("automatic_heart_rate", SVC_FITNESS, CMD_AUTO_HR, tlv_enc(0x01, b"\x01"))
        await send("automatic_spo2", SVC_FITNESS, CMD_AUTO_SPO2, tlv_enc(0x01, b"\x01"))
        if self._supports_command(SVC_BREATH, CMD_SLEEP_BREATH):
            await send("sleep_breath", SVC_BREATH, CMD_SLEEP_BREATH, tlv_enc(0x01, b"\x02"))
        else:
            results["sleep_breath"] = {"status": "unsupported"}
        save_json_artifact("latest_passive_health_settings.json", results)
        logger.info(f"Passive health settings: {results}")
        return results

    async def get_battery(self) -> int:
        tlvs = await self._transact_encrypted(SVC_DEV, 0x08, tlv_enc(0x01))
        if 0x01 in tlvs:
            return tlvs[0x01][0]
        if 0x02 in tlvs:
            return tlvs[0x02][0]
        return -1

    async def get_fitness_message_count(self, label: str, cmd: int,
                                        start_ts: int, end_ts: int) -> int:
        # Gadgetbridge FitnessData.MessageCount.Request:
        # tag 0x81 empty, tag 0x03 start epoch, tag 0x04 end epoch.
        tlv = (
            tlv_enc(0x81) +
            tlv_enc(0x03, struct.pack(">I", start_ts)) +
            tlv_enc(0x04, struct.pack(">I", end_ts))
        )
        tlvs = await self._transact_encrypted(SVC_FITNESS, cmd, tlv, timeout=20.0)
        logger.debug(f"  {label} count tlvs={ {hex(k): v.hex() for k, v in tlvs.items()} }")

        if 0x81 in tlvs:
            container = tlv_dec(tlvs[0x81])
            if 0x02 in container:
                return struct.unpack(">H", container[0x02])[0]
        if 0x02 in tlvs:
            return struct.unpack(">H", tlvs[0x02]) [0]
        return -1

    async def get_recent_fitness_counts(self, hours: int = 24) -> dict:
        end_ts = int(time.time())
        start_ts = end_ts - hours * 3600
        return {
            "steps": await self.get_fitness_message_count("steps", 0x0A, start_ts, end_ts),
            "sleep": await self.get_fitness_message_count("sleep", 0x0C, start_ts, end_ts),
        }

    @staticmethod
    def _parse_step_features(data: bytes) -> dict:
        if not data:
            return {"error": "missing feature bitmap"}

        i = 0
        bitmap1 = data[i]
        i += 1
        bitmap2 = 0
        if bitmap1 & 0x80:
            if i >= len(data):
                return {"error": "missing second feature bitmap"}
            bitmap2 = data[i]
            i += 1

        parsed = {}
        for bit in (0x01, 0x02, 0x04, 0x08, 0x10, 0x20, 0x40):
            if not (bitmap1 & bit):
                continue
            width = 1 if bit in (0x20, 0x40) else 2
            if i + width > len(data):
                return {**parsed, "error": "feature payload too short"}
            if width == 1:
                value = data[i]
                i += 1
            else:
                value = struct.unpack(">H", data[i:i + 2])[0]
                i += 2
            if bit == 0x02:
                parsed["steps"] = value
            elif bit == 0x04:
                parsed["calories"] = value
            elif bit == 0x08:
                parsed["distance"] = value
            elif bit == 0x40:
                parsed["heart_rate"] = value
            else:
                parsed[f"bitmap1_{bit:#04x}"] = value

        for bit in (0x01, 0x02, 0x04, 0x08, 0x10, 0x20, 0x40, 0x80):
            if not (bitmap2 & bit):
                continue
            if i >= len(data):
                return {**parsed, "error": "second bitmap payload too short"}
            value = data[i]
            i += 1
            if bit == 0x01:
                parsed["spo2"] = value
            elif bit == 0x02:
                parsed["resting_heart_rate"] = value
            else:
                parsed[f"bitmap2_{bit:#04x}"] = value
        return parsed

    async def get_step_data_record(self, index: int) -> list[dict]:
        tlv = tlv_enc(0x81, tlv_enc(0x02, struct.pack(">H", index)))
        tlvs = await self._transact_encrypted(SVC_FITNESS, 0x0B, tlv, timeout=20.0)
        container = tlv_dec(tlvs[0x81])
        items = tlv_items(tlvs[0x81])
        base_ts = struct.unpack(">I", container[0x03])[0]

        records = []
        for tag, value in items:
            if tag != 0x84:
                continue
            sub = tlv_dec(value)
            offset = sub.get(0x05, b"\x00")[0]
            raw_data = sub.get(0x06, b"")
            records.append({
                "record": index,
                "timestamp": base_ts + 60 * offset,
                "raw": raw_data.hex(),
                **self._parse_step_features(raw_data),
            })
        return records

    async def get_sleep_data_record(self, index: int) -> list[dict]:
        tlv = tlv_enc(0x81, tlv_enc(0x02, struct.pack(">H", index)))
        tlvs = await self._transact_encrypted(SVC_FITNESS, 0x0D, tlv, timeout=20.0)
        items = tlv_items(tlvs[0x81])

        records = []
        for tag, value in items:
            if tag != 0x83:
                continue
            sub = tlv_dec(value)
            stamp = sub.get(0x05, b"\x00" * 6)
            start = struct.unpack(">I", stamp[:4])[0]
            duration = struct.unpack(">H", stamp[4:6])[0] * 60
            records.append({
                "record": index,
                "start": start,
                "end": start + duration,
                "duration_sec": duration,
                "stage": sub.get(0x04, b"\x00")[0],
            })
        return records

    async def get_recent_fitness_preview(self, hours: int = 24,
                                         max_step_records: int = None) -> dict:
        counts = await self.get_recent_fitness_counts(hours=hours)
        steps, sleep = [], []
        step_count = max(counts.get("steps", 0), 0)
        if max_step_records is not None:
            step_count = min(step_count, max_step_records)
        for index in range(step_count):
            steps.extend(await self.get_step_data_record(index))
        for index in range(max(counts.get("sleep", 0), 0)):
            sleep.extend(await self.get_sleep_data_record(index))
        return {"counts": counts, "steps": steps, "sleep": sleep}

    @staticmethod
    def summarize_fitness_preview(preview: dict) -> dict:
        steps = preview.get("steps", [])
        sleep = preview.get("sleep", [])
        heart_rates = [r["heart_rate"] for r in steps if "heart_rate" in r and r["heart_rate"] > 0]
        resting_hrs = [r["resting_heart_rate"] for r in steps
                       if "resting_heart_rate" in r and r["resting_heart_rate"] > 0]
        spo2 = [r["spo2"] for r in steps if "spo2" in r and r["spo2"] > 0]
        active_steps = [r for r in steps if any(k in r for k in ("steps", "heart_rate", "spo2"))]
        sleep_minutes = sum(r.get("duration_sec", 0) for r in sleep) // 60
        step_total = sum(r.get("steps", 0) for r in steps)
        step_times = [r["timestamp"] for r in steps if "timestamp" in r]
        sleep_times = [t for r in sleep for t in (r.get("start"), r.get("end")) if t]

        summary = {
            "counts": preview.get("counts", {}),
            "downloaded_step_minutes": len(steps),
            "downloaded_sleep_segments": len(sleep),
            "step_total": step_total,
            "sleep_minutes": sleep_minutes,
            "heart_rate_samples": len(heart_rates),
            "spo2_samples": len(spo2),
            "examples": active_steps[:5],
        }
        if step_times:
            summary["step_window_start"] = local_time_label(min(step_times))
            summary["step_window_end"] = local_time_label(max(step_times))
        if sleep_times:
            summary["sleep_window_start_ts"] = min(sleep_times)
            summary["sleep_window_end_ts"] = max(sleep_times)
            summary["sleep_window_start"] = local_time_label(summary["sleep_window_start_ts"])
            summary["sleep_window_end"] = local_time_label(summary["sleep_window_end_ts"])
        if heart_rates:
            summary["heart_rate_min"] = min(heart_rates)
            hr_avg = sum(heart_rates) / len(heart_rates)
            summary["heart_rate_avg"] = round(hr_avg, 1)
            summary["heart_rate_max"] = max(heart_rates)
            hr_load = sum(max(0, (hr - 60) / 130) ** 2 for hr in heart_rates)
            summary["strain_score"] = round(min(21, 21 * (1 - pow(2.718281828, -hr_load / 40))), 1)
        if spo2:
            summary["spo2_min"] = min(spo2)
            summary["spo2_avg"] = round(sum(spo2) / len(spo2), 1)
            summary["spo2_max"] = max(spo2)
        if resting_hrs:
            summary["resting_hr_min"] = min(resting_hrs)
            summary["resting_hr_avg"] = round(sum(resting_hrs) / len(resting_hrs), 1)
        if sleep_minutes or heart_rates:
            sleep_score = min(100, round((sleep_minutes / 480) * 100))
            hr_baseline = min(resting_hrs) if resting_hrs else (min(heart_rates) if heart_rates else 70)
            hr_recovery = max(0, min(100, round(100 - max(0, hr_baseline - 55) * 2.2)))
            summary["sleep_score"] = sleep_score
            summary["recovery_proxy_no_hrv"] = round(0.7 * sleep_score + 0.3 * hr_recovery)
        return summary

    async def disconnect(self):
        await asyncio.sleep(0.3)
        await self.client.stop_notify(GATT_READ)


# ── Main ──────────────────────────────────────────────────────────────────────

async def run_one_shot_modes(band: Band) -> bool:
    ran = False

    if _env_enabled("BAND10_ONLY_WEATHER"):
        payload = _load_weather_payload()
        logger.info(
            "One-shot weather push: "
            f"location={payload.get('location')} "
            f"source={payload.get('source')} "
            f"hourly={len(payload.get('hourly') or [])} "
            f"daily={len(payload.get('daily') or [])}"
        )
        status = await band.send_weather_update(payload)
        logger.info(f"One-shot weather status: {json.dumps(status, indent=2)}")
        ran = True

    if _env_enabled("BAND10_ONLY_WATCHFACES"):
        inventory = await band.get_watchface_inventory()
        logger.info(f"One-shot watchface inventory: {json.dumps(inventory, indent=2)}")
        ran = True

    if _env_enabled("BAND10_ONLY_WATCHFACE_ACTIVATE"):
        file_name = os.getenv("BAND10_WATCHFACE_FILE", "").strip()
        if not file_name:
            raise RuntimeError("BAND10_WATCHFACE_FILE is required for BAND10_ONLY_WATCHFACE_ACTIVATE=1")
        result = await band.activate_watchface(file_name, os.getenv("BAND10_WATCHFACE_VERSION"))
        logger.info(f"One-shot watchface activation: {json.dumps(result, indent=2)}")
        ran = True

    if _env_enabled("BAND10_ONLY_STRESS"):
        if _env_enabled("BAND10_STRESS_CALIBRATE"):
            duration = float(os.getenv("BAND10_LIVE_HRV_SECONDS", "62"))
            result = await band.calibrate_and_enable_stress(duration=duration)
        else:
            enable = os.getenv("BAND10_STRESS_ENABLE")
            result = await band.set_automatic_stress(True if enable is None else _env_enabled("BAND10_STRESS_ENABLE"))
        logger.info(f"One-shot stress status: {json.dumps(result, indent=2)}")
        ran = True

    return ran


async def _run_session():
    cfg = load_or_create_config()
    scan_timeout = float(os.getenv("BAND10_SCAN_TIMEOUT_SECONDS", "20"))
    logger.info(f"Scanning for {cfg['device_mac']} for {scan_timeout:g}s...")
    save_json_artifact("connection_status.json", {
        "state": "scanning",
        "device_mac": cfg["device_mac"],
        "timeout_sec": scan_timeout,
        "timestamp": int(time.time()),
        "timestamp_local": local_time_label(int(time.time())),
    })
    device = await discover_band_device(cfg, scan_timeout)
    if device is None:
        save_json_artifact("connection_status.json", {
            "state": "not_found",
            "device_mac": cfg["device_mac"],
            "timeout_sec": scan_timeout,
            "timestamp": int(time.time()),
            "timestamp_local": local_time_label(int(time.time())),
        })
        raise RuntimeError(f"Band was not visible to Windows Bluetooth within {scan_timeout:g}s")

    logger.info(f"Connecting to {cfg['device_mac']}...")

    async with BleakClient(device, **bleak_client_options()) as client:
        band = Band(client=client, cfg=cfg)
        await band.connect()
        await band.handshake()
        await band.post_auth_initialize()
        if os.getenv("BAND10_ENABLE_PASSIVE_SETTINGS", "1") != "0":
            await band.enable_passive_health_settings()

        if await run_one_shot_modes(band):
            await band.disconnect()
            return

        if os.getenv("BAND10_ONLY_LIVE_HRV", "0") == "1":
            try:
                live_duration = float(os.getenv("BAND10_LIVE_HRV_SECONDS", "62"))
                await band.measure_live_hrv(duration=live_duration)
            finally:
                try:
                    insights = band_analytics.build_insights(DATA_DIR)
                    logger.info(
                        "Insights: "
                        f"recovery={insights.get('recovery_score')} "
                        f"strain={insights.get('strain', {}).get('strain')} "
                        f"hrv_source={insights.get('data_quality', {}).get('hrv_source')}"
                    )
                except Exception as e:
                    logger.warning(f"Insight generation failed: {e!r}")
                await band.disconnect()
            return

        try:
            bat = await band.get_battery()
            logger.info(f"Battery: {bat}%")
        except Exception as e:
            logger.warning(f"Battery query failed (expected until fully initialised): {e}")

        try:
            preview = await band.get_recent_fitness_preview(hours=24)
            summary = band.summarize_fitness_preview(preview)
            summary["generated_at"] = int(time.time())
            summary["generated_at_local"] = local_time_label(summary["generated_at"])
            logger.info(f"Recent fitness summary (24h): {json.dumps(summary, indent=2)}")
            if summary.get("downloaded_step_minutes") or summary.get("downloaded_sleep_segments"):
                save_json_artifact("latest_fitness_preview.json", preview)
                save_json_artifact("latest_recovery_summary.json", summary)
                append_jsonl_artifact("recovery_history.jsonl", summary)
                save_recovery_report(summary)
            else:
                logger.warning("Fitness preview was empty; preserving previous dashboard artifacts")
        except Exception as e:
            logger.warning(f"Fitness preview query failed: {e}")

        if os.getenv("BAND10_STRESS_SYNC", "1") != "0":
            try:
                stress_hours = int(os.getenv("BAND10_STRESS_HOURS", "168"))
                stress = await band.get_recent_stress_preview(hours=stress_hours)
                logger.info(
                    "Stress preview: "
                    f"count={stress.get('stress_count', 0)} "
                    f"avg={stress.get('stress_avg', 'n/a')} "
                    f"min={stress.get('stress_min', 'n/a')} "
                    f"max={stress.get('stress_max', 'n/a')}"
                )
            except Exception as e:
                logger.warning(f"Stress/RRI file sync failed: {e!r}")

        if os.getenv("BAND10_TRUSLEEP_SYNC", "1") != "0":
            try:
                trusleep_hours = int(os.getenv("BAND10_TRUSLEEP_HOURS", "168"))
                trusleep = await band.get_recent_trusleep_preview(hours=trusleep_hours)
                logger.info(
                    "TruSleep preview: "
                    f"sessions={trusleep['state'].get('session_count', 0)} "
                    f"acc={trusleep['data'].get('acc_count', 0)} "
                    f"ppg={trusleep['data'].get('ppg_count', 0)}"
                )
            except Exception as e:
                logger.warning(f"TruSleep file sync failed: {e!r}")

        try:
            p2p_ping = await band.ping_dictionary_sync()
            logger.info(f"P2P dictionary-sync ping: code={p2p_ping['code']:#x} "
                        f"src={p2p_ping['src_package']} dst={p2p_ping['dst_package']}")
        except Exception as e:
            logger.warning(f"P2P dictionary-sync ping failed: {e}")

        if os.getenv("BAND10_HRV_SYNC") == "1":
            try:
                hrv_samples = await band.get_hrv_samples(days=0)
                logger.info(f"HRV samples: count={len(hrv_samples)} "
                            f"examples={json.dumps(hrv_samples[:5], indent=2)}")
            except Exception as e:
                logger.warning(f"HRV sync probe failed: {e!r}")
                if os.getenv("BAND10_P2P_PROBE") == "1":
                    try:
                        dictionary_probe = await band.probe_dictionary_classes(days=0)
                        save_json_artifact("latest_dictionary_probe.json", dictionary_probe)
                        logger.info(f"P2P dictionary class probe: {json.dumps(dictionary_probe, indent=2)}")
                    except Exception as probe_e:
                        logger.warning(f"P2P dictionary class probe failed: {probe_e!r}")

        if os.getenv("BAND10_LIVE_HRV", "0") == "1":
            try:
                live_duration = float(os.getenv("BAND10_LIVE_HRV_SECONDS", "62"))
                await band.measure_live_hrv(duration=live_duration)
            except Exception as e:
                logger.warning(f"Live HRV measurement failed: {e!r}")

        try:
            insights = band_analytics.build_insights(DATA_DIR)
            logger.info(
                "Insights: "
                f"recovery={insights.get('recovery_score')} "
                f"strain={insights.get('strain', {}).get('strain')} "
                f"hrv_source={insights.get('data_quality', {}).get('hrv_source')}"
            )
        except Exception as e:
            logger.warning(f"Insight generation failed: {e!r}")

        print("\n" + "=" * 70)
        print("🎉 SUCCESS! Band 10 connected, authenticated, and initialized.")
        print("Look at your watch screen — it should now show the active watch face!")
        print("\n📡 Maintaining active Bluetooth connection (Battery keepalive every 15s)...")
        print("Press Ctrl+C to disconnect.")
        print("=" * 70 + "\n")

        while client.is_connected:
            await asyncio.sleep(15)
            try:
                bat = await band.get_battery()
                logger.info(f"Keepalive ping OK (Battery: {bat}%)")
            except Exception as e:
                logger.debug(f"Keepalive ping: {e}")


async def run():
    agent_mgr = BlueZAgentManager()
    await agent_mgr.start()
    try:
        await _run_session()
    finally:
        await agent_mgr.stop()


if __name__ == "__main__":
    asyncio.run(run())
