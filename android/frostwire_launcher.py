#!/usr/bin/env python3
"""
FrostWire Android Launcher TUI

Interactive command-line launcher to build, install and run the FrostWire app
on Android devices (physical or emulator) without Android Studio.

Usage:
    python3 frostwire_launcher.py

Requirements:
    - Python 3.7+
    - rich (pip install rich)
    - adb in PATH
    - emulator in PATH (for AVD launching)
    - Android SDK at ANDROID_HOME or ANDROID_SDK_ROOT
"""

import os
import re
import stat
import subprocess
import sys
import threading
import time
from dataclasses import dataclass
from typing import Optional

from rich.console import Console
from rich.live import Live
from rich.panel import Panel
from rich.prompt import Prompt, Confirm
from rich.table import Table
from rich.text import Text
from rich import box

console = Console()

FW_PACKAGE = "com.frostwire.android"


def run(cmd, capture=True, check=True, env=None):
    merged_env = {**os.environ}
    if env:
        merged_env.update(env)
    if "ANDROID_SDK_ROOT" not in merged_env and "ANDROID_HOME" in merged_env:
        merged_env["ANDROID_SDK_ROOT"] = merged_env["ANDROID_HOME"]
    try:
        result = subprocess.run(
            cmd,
            capture_output=capture,
            text=True,
            check=check,
            env=merged_env
        )
        return result
    except FileNotFoundError as e:
        console.print(f"[red]Error: '{cmd[0]}' not found in PATH. Is the Android SDK installed?[/red]")
        sys.exit(1)
    except subprocess.CalledProcessError as e:
        return e


@dataclass
class Device:
    serial: str
    state: str
    product: str
    model: str
    device: str
    transport_id: str

    @property
    def is_emulator(self) -> bool:
        return self.serial.startswith("emulator-")


@dataclass
class AvdImage:
    name: str
    path: str
    api: int
    abi: str


def get_adb_devices() -> list[Device]:
    result = run(["adb", "devices"])
    if result.returncode != 0:
        return []
    devices = []
    for line in result.stdout.strip().split("\n")[1:]:
        if not line.strip():
            continue
        parts = line.split("\t")
        if len(parts) < 2:
            continue
        serial = parts[0].strip()
        state = parts[1].strip()
        product = _get_prop(serial, "ro.build.product")
        model = _get_prop(serial, "ro.product.model")
        device_name = _get_prop(serial, "ro.product.device")
        transport_id = _get_prop(serial, "ro.adb.transport_id")
        devices.append(Device(serial, state, product, model, device_name, transport_id))
    return devices


def _get_prop(serial: str, key: str) -> str:
    result = run(["adb", "-s", serial, "shell", "getprop", key], capture=True, check=False)
    if result.returncode == 0 and result.stdout.strip():
        return result.stdout.strip()
    return "?"


def get_avds() -> list[str]:
    result = run(["emulator", "-list-avds"], capture=True, check=False)
    if result.returncode != 0:
        return []
    return [line.strip() for line in result.stdout.strip().split("\n") if line.strip()]


def get_sdk_images() -> list[AvdImage]:
    """Get available system images from the SDK."""
    android_sdk = os.environ.get("ANDROID_SDK_ROOT") or os.environ.get("ANDROID_HOME", "")
    if not android_sdk:
        return []
    images_dir = os.path.join(android_sdk, "platforms")
    images = []
    if os.path.isdir(images_dir):
        for platform in os.listdir(images_dir):
            platform_path = os.path.join(images_dir, platform)
            if os.path.isdir(platform_path):
                match = re.match(r"android-(\d+)", platform)
                if match:
                    api = int(match.group(1))
                    # Check for ABI subdirectory
                    system_images = os.path.join(android_sdk, "system-images", platform)
                    if os.path.isdir(system_images):
                        for abi in os.listdir(system_images):
                            abi_path = os.path.join(system_images, abi)
                            if os.path.isdir(abi_path):
                                images.append(AvdImage(name=platform, path=abi_path, api=api, abi=abi))
    return images


def get_package_manager() -> str:
    """Detect the package manager on the system for creating AVDs."""
    # Check for sdkmanager
    android_sdk = os.environ.get("ANDROID_SDK_ROOT") or os.environ.get("ANDROID_HOME", "")
    if android_sdk:
        sdkmanager = os.path.join(android_sdk, "cmdline-tools", "latest", "bin", "sdkmanager")
        if not os.path.exists(sdkmanager):
            sdkmanager = os.path.join(android_sdk, "tools", "bin", "sdkmanager")
        if os.path.exists(sdkmanager):
            return sdkmanager
    # Check in PATH
    result = run(["which", "sdkmanager"], capture=True, check=False)
    if result.returncode == 0 and result.stdout.strip():
        return result.stdout.strip()
    return ""


def create_avd(name: str, api: int, abi: str, device: str = "pixel_5") -> bool:
    """Create a new AVD from available system images."""
    sdkmanager = get_package_manager()
    android_sdk = os.environ.get("ANDROID_SDK_ROOT") or os.environ.get("ANDROID_HOME", "")

    console.print(f"[dim]Creating AVD '{name}' (API {api}, {abi})...[/dim]")

    # Use avdmanager to create the AVD
    cmd = [
        "avdmanager", "create", "avd",
        "-n", name,
        "-k", f"system-images;android-{api};{abi}",
        "-d", device
    ]

    result = run(cmd, capture=True, check=False)
    if result.returncode != 0:
        console.print(f"[yellow]avdmanager not available, trying echo approach...[/yellow]")
        console.print(f"[dim]To create AVD manually, run:[/dim]")
        console.print(f"  echo 'y' | avdmanager create avd -n {name} -k 'system-images;android-{api};{abi}' -d {device}")
        return False

    console.print(f"[green]AVD '{name}' created successfully.[/green]")
    return True


def delete_avd(name: str) -> bool:
    """Delete an existing AVD."""
    console.print(f"[dim]Deleting AVD '{name}'...[/dim]")
    result = run(["avdmanager", "delete", "avd", "-n", name, "-v"],
                 capture=True, check=False)
    if result.returncode != 0:
        # Try with echo y
        result = run(["bash", "-c", f"echo 'y' | avdmanager delete avd -n {name}"],
                     capture=True, check=False)
    if result.returncode != 0:
        console.print(f"[red]Failed to delete AVD: {result.stderr if result.stderr else result.stdout}[/red]")
        return False
    console.print(f"[green]AVD '{name}' deleted.[/green]")
    return True


def get_apk_path() -> Optional[str]:
    os.chdir(os.path.dirname(os.path.abspath(__file__)))
    result = run(
        ["find", ".", "-name", "*.apk", "-path", "*/build/outputs/apk/*", "-not", "-path", "*/lint/*"],
        capture=True,
        check=False
    )
    if result.returncode == 0 and result.stdout.strip():
        apks = result.stdout.strip().split("\n")
        for apk in apks:
            if "plus1Debug" in apk:
                return apk.strip()
        for apk in apks:
            if "plusDebug" in apk:
                return apk.strip()
        if apks:
            return apks[-1].strip()
    return None


def build_apk() -> Optional[str]:
    console.print()
    console.print("[bold cyan]Building APK...[/bold cyan]")
    os.chdir(os.path.dirname(os.path.abspath(__file__)))
    result = run(["./gradlew", "assemblePlus1Debug"], capture=True, check=False)

    if result.returncode != 0:
        console.print("[red]Build failed:[/red]")
        if result.stdout:
            console.print(result.stdout[-3000:])
        if result.stderr:
            console.print(result.stderr[-1000:])
        return None

    apk = get_apk_path()
    if not apk:
        console.print("[red]Could not find APK after build.[/red]")
        return None

    console.print(f"[green]Build successful: {apk}[/green]")
    return apk


def wait_for_device(serial: str, timeout: int = 90) -> bool:
    console.print(f"[dim]Waiting for device {serial} to boot...[/dim]")
    for _ in range(timeout):
        result = run(["adb", "-s", serial, "shell", "getprop", "sys.boot_completed"],
                     capture=True, check=False)
        if result.returncode == 0 and result.stdout.strip() == "1":
            console.print("[green]Device booted.[/green]")
            return True
        time.sleep(1)
    console.print("[yellow]Boot wait timeout.[/yellow]")
    return False


def install_apk(apk: str, serial: str) -> bool:
    console.print(f"[dim]Installing on {serial}...[/dim]")
    result = run(["adb", "-s", serial, "install", "-r", "-d", apk],
                 capture=True, check=False)
    if result.returncode != 0:
        console.print(f"[red]Install failed: {result.stderr}[/red]")
        return False
    console.print("[green]APK installed.[/green]")
    return True


def launch_app(serial: str) -> None:
    console.print("[dim]Launching FrostWire...[/dim]")
    run(["adb", "-s", serial, "shell", "am", "start", "-n",
         "com.frostwire.android/.MainActivity"], capture=True, check=False)
    run(["adb", "-s", serial, "shell", "monkey", "-p", "com.frostwire.android", "-c",
         "android.intent.category.LAUNCHER", "1"], capture=True, check=False)
    console.print("[green]App launched.[/green]")


def run_emulator(avd: str, wipe_data: bool = False) -> Optional[str]:
    console.print(f"[dim]Booting emulator: {avd}[/dim]")
    
    # Find emulator path - the one in sdk/tools is a wrapper, we need the actual binary
    sdk_emulator = os.path.expanduser("~/Library/Android/sdk/emulator/emulator")
    if not os.path.exists(sdk_emulator):
        # Fall back to PATH
        sdk_emulator = "emulator"
    
    cmd = [sdk_emulator, "-avd", avd, "-no-snapshot-load", "-no-snapshot-save", "-no-audio", "-gpu", "swiftshader_indirect"]
    if wipe_data:
        cmd.append("-wipe-data")

    console.print(f"[dim]Running: {' '.join(cmd)}[/dim]")
    proc = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)

    # Wait for boot (up to 3 minutes for cold boot)
    for i in range(180):
        devices = get_adb_devices()
        emulator_found = False
        for d in devices:
            if d.is_emulator:
                emulator_found = True
                result = run(["adb", "-s", d.serial, "shell", "getprop", "sys.boot_completed"],
                            capture=True, check=False)
                if result.returncode == 0 and result.stdout.strip() == "1":
                    console.print(f"[green]Emulator {avd} booted as {d.serial}.[/green]")
                    return d.serial
        if i % 10 == 0:
            adb_result = run(["adb", "devices"], capture=True, check=False)
            console.print(f"[dim]Waiting for emulator... ({i}s) adb devices: {adb_result.stdout.strip()}[/dim]")
            # Check if emulator process is still alive
            if proc.poll() is not None:
                stdout, stderr = proc.communicate()
                console.print(f"[red]Emulator process died![/red]")
                console.print(f"[dim]stdout: {stdout}[/dim]")
                console.print(f"[dim]stderr: {stderr}[/dim]")
                return None
        time.sleep(1)

    proc.terminate()
    stdout, stderr = proc.communicate(timeout=5)
    console.print("[yellow]Emulator boot timeout.[/yellow]")
    console.print(f"[dim]Emulator stderr: {stderr[:500]}[/dim]")
    return None


def print_devices_table(devices: list[Device], avds: list[str], sdk_images: list[AvdImage]) -> tuple[Table, list]:
    table = Table(title="[bold]Android Devices & Emulators[/bold]", box=box.ROUNDED)
    table.add_column("#", style="cyan", width=3)
    table.add_column("Identifier", style="magenta")
    table.add_column("Type", style="yellow")
    table.add_column("State", style="green")
    table.add_column("Details", style="white")

    options = []
    idx = 1

    # Running devices
    for d in devices:
        state = "[green]device[/green]" if d.state == "device" else f"[yellow]{d.state}[/yellow]"
        dtype = "[cyan]emulator[/cyan]" if d.is_emulator else "[green]physical[/green]"
        table.add_row(str(idx), d.serial, dtype, state, f"{d.model} / {d.product}")
        options.append(("device", d.serial))
        idx += 1

    # Available AVDs
    for avd in avds:
        running = any(d.serial == avd for d in devices)
        if not running:
            table.add_row(str(idx), avd, "[yellow]avd[/yellow]", "[dim]stopped[/dim]", avd)
            options.append(("avd_stopped", avd))
            idx += 1

    # Logcat option for each running device
    for d in devices:
        if d.state == "device":
            table.add_row(str(idx), d.serial, "[cyan]logcat[/cyan]", "[dim]--[/dim]",
                         f"View logcat for {d.model}")
            options.append(("logcat", d.serial))
            idx += 1

    # AVD creation option
    if sdk_images:
        table.add_row(str(idx), "+new", "[bold green]create AVD[/bold green]", "[dim]--[/dim]",
                     "Create a new emulator from SDK system images")
        options.append(("create_avd", ""))
        idx += 1

    return table, options


def handle_create_avd(sdk_images: list[AvdImage]):
    """Interactively create a new AVD."""
    if not sdk_images:
        console.print("[yellow]No SDK system images found. Install via sdkmanager.[/yellow]")
        return

    # Group by API level
    by_api = {}
    for img in sdk_images:
        if img.api not in by_api:
            by_api[img.api] = []
        by_api[img.api].append(img)

    console.print()
    console.print("[bold]Available API levels:[/bold]")
    api_levels = sorted(by_api.keys(), reverse=True)
    for api in api_levels:
        count = len(by_api[api])
        console.print(f"  API {api}: {count} image(s)")

    api_choice = Prompt.ask(
        "[dim]Select API level (or 'q' to cancel)[/dim]",
        choices=[str(a) for a in api_levels] + ["q"],
        default="q"
    )
    if api_choice == "q":
        return

    api = int(api_choice)
    abis = sorted(set(img.abi for img in by_api[api]))
    abi_choice = Prompt.ask(
        "[dim]Select ABI[/dim]",
        choices=abis + ["q"],
        default="q"
    )
    if abi_choice == "q":
        return

    avd_name = Prompt.ask("[dim]AVD name (e.g. FrostWire_15_Pixel7)[/dim]")

    # Check if name already exists
    existing = get_avds()
    if avd_name in existing:
        if not Confirm.ask(f"[yellow]AVD '{avd_name}' already exists. Delete it first?[/yellow]", default=False):
            return
        delete_avd(avd_name)

    create_avd(avd_name, api, abi_choice)


def handle_avd_options(avd_name: str) -> str:
    """Show options for a stopped AVD and return the choice."""
    console.print()
    prompt = Prompt.ask(
        f"[bold]AVD '{avd_name}' is stopped. Select action:[/bold]\n"
        f"  [bright_cyan]\\[l]aunch[/bright_cyan] - Boot the emulator and install the APK\n"
        f"  [bright_red]\\[d]elete[/bright_red] - Delete this AVD permanently\n"
        f"  [bright_white]\\[c]ancel[/bright_white] - Return to device selection",
        choices=["l", "d", "c"],
        default="l"
    )
    if prompt == "l":
        return "launch"
    elif prompt == "d":
        return "delete"
    else:
        return "cancel"


class LogcatReader:
    """Reads adb logcat for a specific package and feeds it to a Rich display."""

    def __init__(self, serial: str, package: str = FW_PACKAGE, buffer_lines: int = 500):
        self.serial = serial
        self.package = package
        self.buffer_lines = buffer_lines
        self._lines: list[str] = []
        self._stop_event = threading.Event()
        self._thread: threading.Thread | None = None

    def _filter_tag(self, line: str) -> bool:
        """Return True if the logcat line is from our package or related system services."""
        if self.package in line:
            return True
        # Also include common FrostWire-related tags
        related_tags = (
            "FrostWire",
            "SystemUtils",
            "MusicUtils",
            "TransferManager",
            "Engine",
            "Telluride",
            "AudioPlayer",
            "SearchPerformer",
        )
        for tag in related_tags:
            if f"/{tag}" in line or f"[{tag}]" in line:
                return True
        return False

    def _read_loop(self, process: subprocess.Popen):
        """Read stdout from the logcat process line by line."""
        try:
            for line in iter(process.stdout.readline, ""):
                if self._stop_event.is_set():
                    break
                if not line:
                    break
                decoded = line.decode("utf-8", errors="replace").rstrip()
                if decoded and self._filter_tag(decoded):
                    self._lines.append(decoded)
                    if len(self._lines) > self.buffer_lines:
                        self._lines.pop(0)
        except Exception:
            pass

    def start(self) -> "LogcatReader":
        """Start the logcat reader thread. Returns self for chaining."""
        # Clear logcat first to get fresh output
        subprocess.run(["adb", "-s", self.serial, "logcat", "-c"],
                       capture_output=True, check=False)
        time.sleep(0.5)

        cmd = [
            "adb", "-s", self.serial, "logcat",
            "-v", "threadtime",   # timestamp | pid | tid | tag: message
            "--pid=" + str(subprocess.run(
                ["adb", "-s", self.serial, "shell", "pidof", self.package],
                capture_output=True, text=True, check=False
            ).stdout.strip() or "0")
        ]

        proc = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE)

        self._thread = threading.Thread(target=self._read_loop, args=(proc,), daemon=True)
        self._thread.start()
        return self

    def stop(self):
        """Stop the logcat reader."""
        self._stop_event.set()
        if self._thread:
            self._thread.join(timeout=3)
        self._thread = None

    def get_lines(self) -> list[str]:
        """Return the collected log lines."""
        return list(self._lines)


def run_logcat_tui(serial: str):
    """Interactive TUI showing live FrostWire logcat output."""
    console.print()
    console.print(f"[bold cyan]FrostWire Logcat — {serial}[/bold cyan]")
    console.print("[dim]Press Ctrl+C to stop logging and return to launcher.[/dim]")
    console.print()

    reader = LogcatReader(serial).start()
    lines: list[str] = []
    stopped = threading.Event()

    def refresh_loop():
        while not stopped.is_set():
            lines[:] = reader.get_lines()
            time.sleep(0.5)

    refresh_thread = threading.Thread(target=refresh_loop, daemon=True)
    refresh_thread.start()

    try:
        while not stopped.is_set():
            current_lines = reader.get_lines()
            if current_lines != lines[:len(current_lines)]:
                lines[:] = current_lines
            if lines:
                console.print("\n".join(f"[dim]{l}[/dim]" for l in lines[-50:]), end="")
                console.print("", end="\r")
            time.sleep(0.3)
    except KeyboardInterrupt:
        stopped.set()
        reader.stop()
        console.print()
        console.print("[yellow]Logcat stopped.[/yellow]")
        console.print()
        prompt = Prompt.ask(
            "[bold]Select action:[/bold] [r]efresh list  [q]uit",
            choices=["r", "q"],
            default="r"
        )
        if prompt == "q":
            console.print("[dim]Exiting.[/dim]")
            sys.exit(0)


def main():
    os.chdir(os.path.dirname(os.path.abspath(__file__)))

    console.print(Panel.fit(
        "[bold cyan]FrostWire Android Launcher[/bold cyan]\n"
        "Build · Install · Run · Manage Emulators",
        box=box.DOUBLE,
        style="cyan"
    ))
    console.print()

    devices = get_adb_devices()
    avds = get_avds()
    sdk_images = get_sdk_images()
    sdkmanager = get_package_manager()

    if sdkmanager:
        console.print(f"[dim]SDK manager: {sdkmanager}[/dim]")
    if sdk_images:
        apis = sorted(set(img.api for img in sdk_images), reverse=True)
        console.print(f"[dim]Available SDK images: API {apis}[/dim]")
    console.print()

    table, options = print_devices_table(devices, avds, sdk_images)
    console.print(table)
    console.print()

    choices = [str(i + 1) for i in range(len(options))]
    choice = Prompt.ask(
        "[bold]Select device # (or 'q' to quit)[/bold]",
        choices=choices + ["q"],
        default="q"
    )

    if choice == "q":
        console.print("[dim]Exiting.[/dim]")
        sys.exit(0)

    try:
        idx = int(choice) - 1
        kind, ident = options[idx]
    except (ValueError, IndexError):
        console.print("[red]Invalid selection.[/red]")
        sys.exit(1)

    # Handle AVD creation
    if kind == "create_avd":
        handle_create_avd(sdk_images)
        console.print()
        console.print("[dim]Returning to device list...[/dim]")
        main()  # Restart
        return

    # Handle AVD options (if stopped)
    if kind == "avd_stopped":
        action = handle_avd_options(ident)
        if action == "delete":
            handle_delete_avd(ident)
            console.print()
            console.print("[dim]Returning to device list...[/dim]")
            main()
            return
        elif action == "cancel":
            console.print()
            console.print("[dim]Returning to device list...[/dim]")
            main()
            return
        # else action == "launch": proceed to boot the emulator

    # Build APK
    apk = build_apk()
    if not apk:
        sys.exit(1)

    # Handle AVD boot
    if kind == "avd_stopped":
        serial = run_emulator(ident)
        if not serial:
            sys.exit(1)
    else:
        serial = ident
        d = next((d for d in devices if d.serial == serial), None)
        if d and d.state != "device":
            wait_for_device(serial)

    # Install and launch
    if not install_apk(apk, serial):
        sys.exit(1)

    launch_app(serial)

    console.print()
    console.print(f"[bold green]Done! FrostWire running on {serial}[/bold green]")

    run_logcat_tui(serial)


if __name__ == "__main__":
    main()
