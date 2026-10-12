"""HiChain3 Cryptographic Primitives & Derivations for Huawei Wearables.

Implements the key derivation, PIN decryption, HMAC token verification,
and AES-GCM encryption used in Huawei HiChain3 authentication.
"""

import hashlib
import hmac
import os
from typing import Tuple
from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes
from cryptography.hazmat.primitives.kdf.hkdf import HKDF
from cryptography.hazmat.primitives import hashes

# Standard Huawei digest secrets for AES-CBC PIN decryption
DIGEST_SECRETS = {
    1: bytes.fromhex("70FB6C24035FDB552F38898AEEDE3F69"),
    2: bytes.fromhex("93ACDEF76ACB09857DBFE5261AABCD78"),
    3: bytes.fromhex("9C2763A9CCE134766DE3FF6118200550"),
}

GROUP_ID = "7B0BC0CBCE474F6C238D9661C63400B797B166EA7849B3A370FC73A9A236E989"


def decrypt_pin(encrypted_pin: bytes, iv: bytes, auth_version: int = 1) -> str:
    """Decrypt the 6-digit pairing PIN shown on the Huawei Band screen.

    The band sends encrypted PIN bytes and an IV on svc=0x01, cmd=0x2C.
    Returns the decoded ASCII PIN string (e.g. '325533').
    """
    key = DIGEST_SECRETS.get(auth_version, DIGEST_SECRETS[1])
    cipher = Cipher(algorithms.AES(key), modes.CBC(iv[:16]))
    decryptor = cipher.decryptor()
    padded = decryptor.update(encrypted_pin) + decryptor.finalize()

    # The decrypted text contains hex ASCII representations of the digits
    clean_text = padded.rstrip(b"\x00").decode("utf-8", errors="ignore")
    return clean_text.strip()


def derive_psk_first_auth(pin: str, seed: bytes) -> bytes:
    """Derive the Pre-Shared Key (PSK) for first authentication.

    PSK = HMAC-SHA256(SHA256(hex(pin).encode('utf-8')), seed)
    """
    pin_hex_utf8 = pin.encode("utf-8").hex().encode("utf-8")
    pin_hash = hashlib.sha256(pin_hex_utf8).digest()
    return hmac.new(pin_hash, seed, hashlib.sha256).digest()


def derive_psk_reconnect(secret_key: bytes, seed: bytes) -> bytes:
    """Derive the Pre-Shared Key (PSK) for subsequent reconnects."""
    return hmac.new(secret_key, seed, hashlib.sha256).digest()


def derive_session_key(psk: bytes, rand_self: bytes, rand_peer: bytes) -> bytes:
    """Derive the 32-byte session key using HKDF-SHA256."""
    salt = rand_self + rand_peer
    hkdf = HKDF(
        algorithm=hashes.SHA256(),
        length=32,
        salt=salt,
        info=b"hichain_iso_session_key",
    )
    return hkdf.derive(psk)


def compute_hmac(key: bytes, data: bytes) -> str:
    """Compute uppercase hex HMAC-SHA256 string."""
    return hmac.new(key, data, hashlib.sha256).hexdigest().upper()


def aes_gcm_encrypt(data: bytes, key: bytes, nonce: bytes, aad: bytes) -> bytes:
    """Encrypt payload using AES-256-GCM with associated data."""
    cipher = Cipher(algorithms.AES(key), modes.GCM(nonce))
    encryptor = cipher.encryptor()
    encryptor.authenticate_additional_data(aad)
    ciphertext = encryptor.update(data) + encryptor.finalize()
    return ciphertext + encryptor.tag


def aes_gcm_decrypt(data_and_tag: bytes, key: bytes, nonce: bytes, aad: bytes) -> bytes:
    """Decrypt payload and verify authentication tag using AES-256-GCM."""
    tag = data_and_tag[-16:]
    ciphertext = data_and_tag[:-16]
    cipher = Cipher(algorithms.AES(key), modes.GCM(nonce, tag))
    decryptor = cipher.decryptor()
    decryptor.authenticate_additional_data(aad)
    return decryptor.update(ciphertext) + decryptor.finalize()
