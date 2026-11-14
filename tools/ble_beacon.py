"""Windows BLE Test Beacon using Python WinRT bindings.

Prerequisites:
    pip install winrt-Windows.Devices.Bluetooth winrt-Windows.Devices.Bluetooth.Advertisement winrt-Windows.Devices.Bluetooth.GenericAttributeProfile winrt-Windows.Storage.Streams winrt-Windows.Foundation

Usage:
    python tools/ble_beacon.py                  # Interactive menu
    python tools/ble_beacon.py meta_rayban      # Broadcast 0x058E + META_RB_GLASS until Enter
    python tools/ble_beacon.py heycyan --duration 10
"""

from __future__ import annotations

import argparse
import asyncio
import sys
import time
from dataclasses import dataclass
from typing import Optional
from uuid import UUID

try:
    from winrt.windows.devices.bluetooth import BluetoothError
    from winrt.windows.devices.bluetooth.advertisement import (
        BluetoothLEAdvertisementDataSection,
        BluetoothLEAdvertisementPublisher,
        BluetoothLEManufacturerData,
    )
    from winrt.windows.devices.bluetooth.genericattributeprofile import (
        GattServiceProvider,
        GattServiceProviderAdvertisingParameters,
    )
    from winrt.windows.storage.streams import DataWriter
except ImportError:
    print(
        "Missing WinRT Python packages. Install with:\n"
        "  pip install winrt-Windows.Devices.Bluetooth "
        "winrt-Windows.Devices.Bluetooth.Advertisement "
        "winrt-Windows.Devices.Bluetooth.GenericAttributeProfile "
        "winrt-Windows.Storage.Streams winrt-Windows.Foundation",
        file=sys.stderr,
    )
    sys.exit(1)


@dataclass(frozen=True)
class Scenario:
    key: str
    alias: str
    description: str
    company_id: Optional[int] = None
    payload: bytes = b""
    service_uuid: Optional[str] = None
    raw_section_type: Optional[int] = None
    raw_section_data: bytes = b""
    local_name: Optional[str] = None


SCENARIOS: tuple[Scenario, ...] = (
    Scenario(
        key="1",
        alias="meta_rayban",
        description="Ray-Ban Meta (0x058E + META_RB_GLASS) -> HIGH / ALERT",
        company_id=0x058E,
        payload=b"META_RB_GLASS",
    ),
    Scenario(
        key="2",
        alias="meta_quest",
        description="Quest VR False-Positive Check (0x058E + META_QUEST_3) -> IGNORED",
        company_id=0x058E,
        payload=b"META_QUEST_3",
    ),
    Scenario(
        key="3",
        alias="luxottica",
        description="Luxottica Frame (0x0D53) -> HIGH / ALERT",
        company_id=0x0D53,
        payload=b"\x01\x02",
    ),
    Scenario(
        key="4",
        alias="snap",
        description="Snap Spectacles (0x03C2) -> MEDIUM (2 obs) / ALERT",
        company_id=0x03C2,
        payload=b"\x01",
    ),
    Scenario(
        key="5",
        alias="meta_general",
        description="Meta Platforms General (0x01AB) -> LOW (3 obs) / CAUTION",
        company_id=0x01AB,
        payload=b"\x01",
    ),
    Scenario(
        key="6",
        alias="jieli",
        description="Zhuhai Jieli Chipset (0x05D6) -> LOW (3 obs) / CAUTION",
        company_id=0x05D6,
        payload=b"\x01",
    ),
    Scenario(
        key="7",
        alias="heycyan",
        description="HeyCyan SDK Service UUID (7905FFF0-B5CE-4E99-A40F-4B1E122D00D0) -> HIGH / ALERT",
        service_uuid="7905FFF0-B5CE-4E99-A40F-4B1E122D00D0",
    ),
    Scenario(
        key="8",
        alias="rayneo",
        description="TCL / RayNeo (0x0BC6) -> MEDIUM (2 obs) / ALERT",
        company_id=0x0BC6,
        payload=b"\x01",
    ),
    Scenario(
        key="9",
        alias="meizu",
        description="Meizu MYVU AR Glasses (0x03AB) -> MEDIUM (2 obs) / DISPLAY_AR",
        company_id=0x03AB,
        payload=b"\x01",
    ),
    Scenario(
        key="10",
        alias="rokid",
        description="Rokid AI Glasses Service UUID (00009100-0000-1000-8000-00805F9B34FB) -> HIGH / ALERT",
        service_uuid="00009100-0000-1000-8000-00805F9B34FB",
    ),
    Scenario(
        key="11",
        alias="vuzix",
        description="Vuzix Smart Glasses (0x060C) -> HIGH / ALERT",
        company_id=0x060C,
        payload=b"\x01",
    ),
    Scenario(
        key="12",
        alias="da_echo",
        description="Da Echo Glasses (0xFEA8 + ServiceData 41 31 49 01 00 00) -> HIGH / ALERT",
        raw_section_type=0x16,
        raw_section_data=b"\xA8\xFE\x41\x31\x49\x01\x00\x00",
    ),
    Scenario(
        key="13",
        alias="even_g1",
        description="Even Realities G1 (LocalName 'Even G1_TEST') -> MEDIUM (2 obs) / DISPLAY_AR",
        local_name="Even G1_TEST",
    ),
    Scenario(
        key="14",
        alias="heycyan_name",
        description="HeyCyan Name Prefix (LocalName 'HeyCyan-TEST') -> MEDIUM (2 obs) / CAMERA",
        local_name="HeyCyan-TEST",
    ),
)


def find_scenario(token: str) -> Optional[Scenario]:
    normalized = token.strip().lower()
    for scenario in SCENARIOS:
        if normalized in (scenario.key, scenario.alias):
            return scenario
    return None


def wait_stop(duration: int) -> None:
    if duration > 0:
        print(f"Advertising for {duration}s...")
        time.sleep(duration)
    else:
        input("Advertising active. Press [Enter] to stop...\n")


async def run_gatt_uuid(service_uuid: str, duration: int) -> None:
    result = await GattServiceProvider.create_async(UUID(service_uuid))
    if result.error != BluetoothError.SUCCESS:
        print(f"GattServiceProvider failed: {result.error}", file=sys.stderr)
        return

    provider = result.service_provider
    provider.add_advertisement_status_changed(
        lambda _s, args: print(f"  -> GATT Adv Status: {args.status} (Error={args.error})")
    )
    params = GattServiceProviderAdvertisingParameters()
    params.is_discoverable = True
    params.is_connectable = True
    provider.start_advertising(params)
    try:
        wait_stop(duration)
    finally:
        provider.stop_advertising()
        print("Stopped.")


def run_scenario(scenario: Scenario, duration: int = 0) -> None:
    print(f"\nStarting: {scenario.description}")
    if scenario.service_uuid is not None:
        asyncio.run(run_gatt_uuid(scenario.service_uuid, duration))
        return

    publisher = BluetoothLEAdvertisementPublisher()
    if scenario.company_id is not None:
        writer = DataWriter()
        writer.write_bytes(scenario.payload)
        mfg = BluetoothLEManufacturerData(scenario.company_id, writer.detach_buffer())
        publisher.advertisement.manufacturer_data.append(mfg)
    if scenario.raw_section_type is not None and scenario.raw_section_data:
        writer = DataWriter()
        writer.write_bytes(scenario.raw_section_data)
        publisher.advertisement.data_sections.append(
            BluetoothLEAdvertisementDataSection(scenario.raw_section_type, writer.detach_buffer())
        )
    if scenario.local_name:
        publisher.advertisement.local_name = scenario.local_name

    publisher.add_status_changed(
        lambda _s, args: print(f"  -> Publisher Status: {args.status} (Error={args.error})")
    )
    try:
        publisher.start()
        wait_stop(duration)
    except Exception as exc:
        if scenario.local_name and isinstance(exc, OSError):
            print(
                f"Windows WinRT blocked custom LocalName ('{scenario.local_name}'): {exc}\n"
                "Test name-only rules using nRF Connect on another phone or by renaming the Windows Bluetooth adapter.",
                file=sys.stderr,
            )
        else:
            print(f"Publisher error: {exc}", file=sys.stderr)
    finally:
        try:
            publisher.stop()
        except Exception:
            pass
        print("Stopped.")


def print_menu() -> None:
    print("\n=== SmartGlass Scanner - Python WinRT BLE Beacon ===")
    for s in SCENARIOS:
        print(f"  [{s.key}] {s.description} (alias: {s.alias})")


def main() -> int:
    parser = argparse.ArgumentParser(description="Windows Python WinRT BLE test beacon")
    parser.add_argument("scenario", nargs="?", help="Scenario number (1-14) or alias")
    parser.add_argument("--duration", type=int, default=0, help="Auto-stop seconds (0 = wait for Enter)")
    args = parser.parse_args()

    if args.scenario:
        selected = find_scenario(args.scenario)
        if not selected:
            print(f"Unknown scenario: {args.scenario}", file=sys.stderr)
            print_menu()
            return 1
        run_scenario(selected, args.duration)
        return 0

    while True:
        print_menu()
        choice = input("Select scenario (1-14, or q to quit): ").strip()
        if choice.lower() == "q":
            return 0
        selected = find_scenario(choice)
        if not selected:
            print("Invalid selection.")
            continue
        run_scenario(selected, 0)


if __name__ == "__main__":
    raise SystemExit(main())
