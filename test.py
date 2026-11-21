#!/usr/bin/env python3
"""Project tests for Easy Paper. Run from anywhere: `python test.py` (Team Hub calls it the same way).

Default: JVM tests of the shared code (`core`, `app/shared` jvm = desktop target), Ktor server tests
and a compile of the desktop app; no Android SDK needed. `--all` also builds the Android debug APK
(needs the SDK) and the Wasm web app.
Extra arguments after `--` go to Gradle unchanged, e.g. `python test.py -- --rerun-tasks`.
"""
import argparse
import os
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent
QUICK = [":core:jvmTest", ":app:shared:jvmTest", ":server:test", ":app:desktopApp:compileKotlin"]
FULL = [":app:androidApp:assembleDebug", ":app:webApp:wasmJsBrowserDistribution"]
RESULT_DIRS = ["core", "app/shared", "server"]


def find_android_sdk():
    props = ROOT / "local.properties"
    if props.exists():
        for line in props.read_text(encoding="utf-8", errors="replace").splitlines():
            if line.strip().startswith("sdk.dir=") and line.split("=", 1)[1].strip():
                return None  # already configured, keep the developer's file
    candidates = [os.environ.get("ANDROID_HOME"), os.environ.get("ANDROID_SDK_ROOT")]
    home = Path.home()
    if os.name == "nt":
        candidates.append(os.path.join(os.environ.get("LOCALAPPDATA", str(home / "AppData/Local")), "Android", "Sdk"))
    else:
        candidates += [str(home / "Android/Sdk"), str(home / "Library/Android/sdk"), "/opt/android-sdk", "/usr/lib/android-sdk"]
    for path in filter(None, candidates):
        if (Path(path) / "platforms").is_dir():
            return Path(path)
    return False


def junit_summary(started):
    tests = failures = skipped = 0
    failed = []
    for module in RESULT_DIRS:
        for report in (ROOT / module / "build" / "test-results").glob("**/TEST-*.xml"):
            if report.stat().st_mtime < started:
                continue  # written by an earlier run (task was up to date): not part of this run
            suite = ET.parse(report).getroot()
            tests += int(suite.get("tests", 0))
            skipped += int(suite.get("skipped", 0))
            for case in suite.iter("testcase"):
                if case.find("failure") is not None or case.find("error") is not None:
                    failures += 1
                    failed.append(f"{module}: {case.get('classname')}.{case.get('name')}")
    return tests, failures, skipped, failed


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--all", action="store_true", help="also build Android APK and the Wasm web app")
    parser.add_argument("gradle_args", nargs="*", help="extra Gradle arguments (after --)")
    args = parser.parse_args()

    sdk = find_android_sdk()
    if sdk is False:
        hint = "Android SDK not found: set ANDROID_HOME or add sdk.dir=<path> to local.properties"
        if args.all:  # only the Android build needs the SDK; the default JVM/server checks run without it
            print(hint, file=sys.stderr)
            return 2
        print(hint + " (not needed without --all, continuing)")
    if sdk:
        (ROOT / "local.properties").write_text(f"sdk.dir={sdk.as_posix()}\n", encoding="utf-8")
        print(f"local.properties: sdk.dir={sdk.as_posix()}")

    gradlew = [str(ROOT / "gradlew.bat")] if os.name == "nt" else ["sh", str(ROOT / "gradlew")]
    tasks = QUICK + (FULL if args.all else [])
    command = gradlew + tasks + ["--console=plain", "--continue"] + args.gradle_args
    print("$ " + " ".join(command), flush=True)
    started = time.time() - 1
    code = subprocess.call(command, cwd=ROOT)

    tests, failures, skipped, failed = junit_summary(started)
    for name in failed:
        print("FAILED " + name)
    state = "PASSED" if code == 0 else "FAILED"
    print(f"{state}: gradle exit {code}; tests run now {tests}, failed {failures}, skipped {skipped}"
          f" (up-to-date tasks are not re-run); tasks: {' '.join(tasks)}")
    return code


if __name__ == "__main__":
    sys.exit(main())
