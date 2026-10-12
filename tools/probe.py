#!/usr/bin/env python3
"""Hardware Verification & Protocol Probe for Huawei Band 10.

Forwards directly into the complete Huawhoop protocol engine (tools/connect.py)
with BlueZ pairing agent, correct 16-byte AES-GCM IVs, CMD_PHONE_INFO handlers,
post-auth bootstrap, and active keepalive.
"""

import asyncio
import configparser
import sys
from pathlib import Path

# Ensure project root and tools are in sys.path
_project_root = Path(__file__).resolve().parent.parent
if str(_project_root) not in sys.path:
    sys.path.insert(0, str(_project_root))
_tools_dir = Path(__file__).resolve().parent
if str(_tools_dir) not in sys.path:
    sys.path.insert(0, str(_tools_dir))

from tools.connect import run

if __name__ == "__main__":
    cfg_file = Path("band.ini")
    cfg = configparser.ConfigParser()
    if cfg_file.exists():
        cfg.read(cfg_file)
    if "band10" not in cfg:
        cfg["band10"] = {}

    if len(sys.argv) > 1:
        target_mac = sys.argv[1].strip()
        cfg["band10"]["device_mac"] = target_mac

    # Reset keys for clean pairing pass if not yet active
    if not cfg["band10"].get("secret_key"):
        cfg["band10"]["auth_key"] = ""
        cfg["band10"]["secret_key"] = ""

    with open(cfg_file, "w") as f:
        cfg.write(f)

    asyncio.run(run())
