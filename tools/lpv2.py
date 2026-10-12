"""Huawei LPv2 Protocol Framing & TLV Codec.

Implements the reverse-engineered Huawei LPv2 framing over BLE:
- Magic byte 0x5A
- 2-byte big-endian length: len(svcId + cmdId + TLVs) + 1
- Null slice byte (0x00) for unsliced packets, or slice indicator (0x01/0x02/0x03) + sequence flag
- CRC16-HQXQ (polynomial 0x1021, init 0x0000)
- Huawei 7-bit big-endian VarInt TLV encoding
"""

import binascii
from typing import Dict, List, Tuple, Union


def encode_varint(value: int) -> bytes:
    """Encode an integer as a Huawei 7-bit big-endian VarInt.

    The high bit (0x80) indicates continuation.
    Values 0..127: single byte (0x00..0x7F)
    Values >= 128: multi-byte big-endian
    Examples:
      126 -> b'\\x7e'
      127 -> b'\\x7f'
      128 -> b'\\x81\\x00'
      671 -> b'\\x85\\x1f'
    """
    if value < 0:
        raise ValueError("VarInt value cannot be negative")
    if value < 128:
        return bytes([value])

    chunks = []
    v = value
    while v > 0:
        chunks.append(v & 0x7F)
        v >>= 7
    chunks.reverse()

    # Set continuation bit on all bytes except the last
    result = bytearray()
    for i, c in enumerate(chunks):
        if i < len(chunks) - 1:
            result.append(c | 0x80)
        else:
            result.append(c)
    return bytes(result)


def decode_varint(data: bytes, offset: int = 0) -> Tuple[int, int]:
    """Decode a Huawei 7-bit VarInt starting at offset.

    Returns: (decoded_value, next_offset)
    """
    value = 0
    pos = offset
    while pos < len(data):
        b = data[pos]
        pos += 1
        value = (value << 7) | (b & 0x7F)
        if (b & 0x80) == 0:
            return value, pos
    raise ValueError("Truncated VarInt in data")


def tlv_encode(tag: int, value: Union[bytes, int, str] = b"") -> bytes:
    """Encode a single TLV entry: [tag: 1 byte][length: VarInt][value]."""
    if isinstance(value, int):
        val_bytes = bytes([value])
    elif isinstance(value, str):
        val_bytes = value.encode("utf-8")
    else:
        val_bytes = bytes(value)

    len_bytes = encode_varint(len(val_bytes))
    return bytes([tag]) + len_bytes + val_bytes


def tlv_decode_all(data: bytes) -> Dict[int, bytes]:
    """Decode all TLVs in a byte sequence into a dict of tag -> value bytes."""
    tlvs: Dict[int, bytes] = {}
    pos = 0
    while pos < len(data):
        tag = data[pos]
        pos += 1
        if pos >= len(data):
            break
        length, pos = decode_varint(data, pos)
        val = data[pos : pos + length]
        pos += length
        tlvs[tag] = val
    return tlvs


def calculate_crc16(data: bytes) -> bytes:
    """Compute 2-byte big-endian CRC16-HQXQ (XMODEM) over data."""
    crc = binascii.crc_hqx(data, 0x0000)
    return crc.to_bytes(2, byteorder="big")


def make_frame(svc_id: int, cmd_id: int, payload: bytes = b"") -> bytes:
    """Construct an unsliced LPv2 packet frame:

    [0x5A][len+1 (2B)][0x00][svcId][cmdId][payload][CRC16 (2B)]
    """
    body = bytes([svc_id, cmd_id]) + payload
    length = len(body) + 1  # includes null slice byte
    header = bytes([0x5A]) + length.to_bytes(2, byteorder="big") + bytes([0x00])
    frame_without_crc = header + body
    crc = calculate_crc16(frame_without_crc)
    return frame_without_crc + crc


def parse_frame(frame: bytes) -> Tuple[int, int, Dict[int, bytes]]:
    """Validate and parse an unsliced LPv2 frame.

    Returns: (svc_id, cmd_id, tlvs_dict)
    """
    if len(frame) < 7:
        raise ValueError(f"Frame too short: {len(frame)} bytes")
    if frame[0] != 0x5A:
        raise ValueError(f"Invalid magic byte: {frame[0]:#x}, expected 0x5A")

    length = int.from_bytes(frame[1:3], byteorder="big")
    expected_crc = frame[-2:]
    computed_crc = calculate_crc16(frame[:-2])
    if expected_crc != computed_crc:
        raise ValueError(f"CRC mismatch: expected {expected_crc.hex()}, got {computed_crc.hex()}")

    slice_byte = frame[3]
    if slice_byte != 0x00:
        raise ValueError(f"Encountered sliced frame ({slice_byte:#x}) in unsliced parser")

    svc_id = frame[4]
    cmd_id = frame[5]
    tlv_data = frame[6:-2]
    tlvs = tlv_decode_all(tlv_data)
    return svc_id, cmd_id, tlvs
