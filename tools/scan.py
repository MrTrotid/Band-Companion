#!/usr/bin/env python3
"""BLE Device Scanner for Huawei Band 10.

Scans for nearby Bluetooth Low Energy devices, highlights Huawei wearables,
and displays MAC address, device name, and signal strength (RSSI).
"""

import asyncio
import sys
from bleak import BleakScanner


async def scan_for_wearables(duration: float = 8.0):
    print(f"Scanning for BLE devices ({duration}s)...")
    print("Ensure Bluetooth is enabled on your computer and the Huawei Band 10 is nearby.\n")

    devices = await BleakScanner.discover(timeout=duration, return_adv=True)

    found_huawei = []
    other_devices = []

    for device, adv in devices.values():
        name = device.name or adv.local_name or "Unknown"
        is_huawei = "HUAWEI" in name.upper() or "BAND 10" in name.upper() or "HONOR" in name.upper()

        info = {
            "address": device.address,
            "name": name,
            "rssi": adv.rssi,
            "uuids": adv.service_uuids,
        }

        if is_huawei:
            found_huawei.append(info)
        else:
            other_devices.append(info)

    if found_huawei:
        print("🎯 FOUND HUAWEI WEARABLES:")
        print("=" * 70)
        for d in found_huawei:
            print(f"  Name:    {d['name']}")
            print(f"  Address: {d['address']}")
            print(f"  RSSI:    {d['rssi']} dBm")
            if d['uuids']:
                print(f"  UUIDs:   {', '.join(d['uuids'])}")
            print("-" * 70)
        print("\nUse the address above with `tools/probe.py` to test connection.")
    else:
        print("No Huawei wearables detected in this scan.")
        if other_devices:
            print(f"\nDiscovered {len(other_devices)} other BLE device(s):")
            for d in other_devices[:10]:
                print(f"  - {d['name']} ({d['address']}, {d['rssi']} dBm)")
            if len(other_devices) > 10:
                print(f"  ... and {len(other_devices) - 10} more.")
        print("\nTip: Make sure the Band is awake (screen on) and Bluetooth is enabled.")


def main():
    duration = float(sys.argv[1]) if len(sys.argv) > 1 else 8.0
    try:
        asyncio.run(scan_for_wearables(duration))
    except Exception as e:
        print(f"Error during BLE scan: {e}")
        print("Note: On Linux, ensure the BlueZ service is running: `sudo systemctl status bluetooth`")


if __name__ == "__main__":
    main()
