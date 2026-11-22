#!/usr/bin/env python3
"""Patient app end-to-end on an Android emulator, no hands (task 84).

Needs a running emulator or phone (adb devices) with the debug APK installed:
    ./gradlew :app:androidApp:installDebug
    python tools/android_smoke.py [--out docs/screenshots/android]

Use an AOSP image (system-images;android-37.0;default;x86_64): Play Store images ask to authorize adb on screen.
The script clears the app's data, walks onboarding -> entries -> Sharing -> QR, restarts the app and checks the
entries are still there. It finds controls by their English text in the accessibility tree (uiautomator dump), taps
their centre and saves a screenshot per step. The first step that does not appear in time stops the run (exit 1)
and prints every text on the screen.
"""
import argparse
import html
import os
import re
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PACKAGE = "org.easypaper"
SDK = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT") or str(Path.home() / "Android/Sdk")
ADB = str(Path(SDK) / "platform-tools" / ("adb.exe" if os.name == "nt" else "adb"))
NODE = re.compile(r'<node [^>]*?text="([^"]*)"[^>]*?content-desc="([^"]*)"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')


def adb(*args, binary=False):
    result = subprocess.run([ADB, *args], capture_output=True, timeout=60)
    if result.returncode:
        raise SystemExit(f"adb {' '.join(args)} failed: {result.stderr.decode(errors='replace').strip()}")
    return result.stdout if binary else result.stdout.decode(errors="replace")


def screen():
    """[(label, x, y)] of every node with a text or content description."""
    adb("shell", "uiautomator", "dump", "/sdcard/easypaper-ui.xml")
    xml = adb("exec-out", "cat", "/sdcard/easypaper-ui.xml")
    nodes = []
    for text, desc, x1, y1, x2, y2 in NODE.findall(xml):
        label = html.unescape(text or desc)
        if label:
            nodes.append((label, (int(x1) + int(x2)) // 2, (int(y1) + int(y2)) // 2))
    return nodes


class Smoke:
    def __init__(self, out):
        self.out = out
        self.step = 0
        out.mkdir(parents=True, exist_ok=True)

    def find(self, text, timeout=15, prefix=False, last=False):
        """Centre of the node labelled [text] (or starting with it); [last]: the lowest of several, e.g. a button
        that repeats the sheet title, or a text field after its label."""
        deadline = time.time() + timeout
        while True:
            nodes = screen()
            hits = [(x, y) for label, x, y in nodes if label == text or (prefix and label.startswith(text))]
            if hits:
                return hits[-1] if last else hits[0]
            if time.time() > deadline:
                print(f"FAILED at step {self.step + 1}: '{text}' not on screen. Texts there:", file=sys.stderr)
                for label, _, _ in nodes:
                    print(f"  {label!r}", file=sys.stderr)
                self.shot("FAILED")
                raise SystemExit(1)
            time.sleep(0.7)

    def tap(self, text, prefix=False, last=False):
        x, y = self.find(text, prefix=prefix, last=last)
        adb("shell", "input", "tap", str(x), str(y))
        time.sleep(1.0)

    def fill(self, field, value):
        self.tap(field, last=True)  # the field follows its label
        self.type(value)
        adb("shell", "input", "keyevent", "111")  # ESC: hide the keyboard
        time.sleep(0.5)

    def scroll_to(self, text, tries=5):
        for _ in range(tries):
            if any(label == text for label, _, _ in screen()):
                return
            self.scroll_down()

    def type(self, text):
        adb("shell", "input", "text", text.replace(" ", "%s"))
        time.sleep(0.4)

    def scroll_down(self):
        adb("shell", "input", "swipe", "540", "2000", "540", "900", "300")
        time.sleep(0.6)

    def shot(self, name):
        self.step += 1
        path = self.out / f"{self.step:02d}-{name}.png"
        path.write_bytes(adb("exec-out", "screencap", "-p", binary=True))
        print(f"  {path.relative_to(ROOT) if path.is_relative_to(ROOT) else path}")

    def launch(self):
        adb("shell", "am", "start", "-W", "-n", f"{PACKAGE}/.MainActivity")
        time.sleep(2)


PHOTO = ROOT / "core/src/jvmTest/resources/photos/baseline.jpg"  # stripes with made-up phone metadata and GPS


def run(s: Smoke):
    adb("shell", "pm", "clear", PACKAGE)
    adb("push", str(PHOTO), "/sdcard/Pictures/easypaper-meal.jpg")
    adb("shell", "am", "broadcast", "-a", "android.intent.action.MEDIA_SCANNER_SCAN_FILE", "-d",
        "file:///sdcard/Pictures/easypaper-meal.jpg")
    s.launch()

    print("onboarding")
    s.find("Get started")
    s.shot("welcome")
    s.tap("Get started")
    for _ in range(3):
        s.tap("Next")
    s.shot("onboarding-private")
    s.tap("Continue")
    s.fill("Name (optional)", "Anna")
    s.shot("profile-name")
    s.tap("Continue")
    s.find("Privacy & data")
    s.tap("Continue")
    s.find("Summary")
    s.shot("summary-empty")

    print("entries")
    s.tap("Browse")
    s.shot("browse")
    s.tap("Blood pressure")
    s.tap("Add Data")
    s.fill("Upper value", "128")
    s.fill("Lower value", "84")
    s.fill("Pulse (optional)", "72")
    s.shot("pressure-form")
    s.tap("Save", last=True)
    s.find("128/84 mmHg, pulse 72")
    s.shot("pressure-saved")
    s.tap("Back")
    s.tap("Water")
    s.tap("Add Data")
    s.tap("Add Data", last=True)  # the sheet's button repeats the toolbar action; 250 ml is pre-filled
    s.find("250\u00a0ml")
    s.shot("water-saved")
    s.tap("Back")
    s.tap("Food")
    s.tap("Add Data")
    s.fill("What did you eat or drink?", "Soup and tea")
    s.tap("Choose photo")
    s.tap("Photo taken on", prefix=True)  # the system photo picker
    s.find("Remove photo")
    s.shot("food-form-photo")
    s.tap("Save", last=True)
    s.find("Soup and tea (Photo)")
    s.shot("food-saved")

    print("sharing")
    s.tap("Sharing")
    s.shot("sharing-tab")
    s.tap("Share with your doctor", last=True)
    s.find("What to share")
    s.shot("what-to-share")
    s.scroll_to("Next")
    s.find("3 entries, 1 photo", prefix=True)  # blood pressure, water, food with its photo
    s.shot("what-to-share-total")
    s.tap("Next", last=True)
    s.find("How to share")
    s.shot("how-to-share")
    s.tap("Show QR code")
    s.tap("Create code for the doctor")
    s.find("QR code for the doctor", timeout=60)  # encrypted upload to the relay
    s.shot("qr-for-the-doctor")

    print("restart")
    adb("shell", "am", "force-stop", PACKAGE)
    s.launch()
    s.find("Summary")
    s.tap("Browse")
    s.tap("Blood pressure")
    s.find("128/84 mmHg, pulse 72")
    s.tap("Back")
    s.tap("Food")
    s.find("Soup and tea (Photo)")
    s.shot("after-restart-food")


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--out", default=str(ROOT / "docs/screenshots/android"), help="folder for the screenshots")
    args = parser.parse_args()
    devices = [l for l in adb("devices").splitlines()[1:] if l.strip().endswith("device")]
    if not devices:
        raise SystemExit("No authorized device: start an emulator (AOSP image) or connect a phone")
    run(Smoke(Path(args.out)))
    print("PASSED: patient flow on the device")


if __name__ == "__main__":
    main()
