# Easy Paper

A doctor builds a short intake request from widgets (questionnaire, blood pressure, water, food,
medication, diary, questions for the doctor). The patient fills it in over a few days with text,
numbers and optional photos. At the appointment the patient hands over an encrypted report by QR,
and the doctor reads a short summary in which every line links back to the original entry.

Easy Paper collects and structures facts. It does not diagnose and does not give medical advice;
anything the AI drafts is confirmed field by field by the doctor.

Kotlin Multiplatform project generated with [kmp.jetbrains.com](https://kmp.jetbrains.com)
(Android, Desktop JVM, Web Wasm/JS, Ktor server).

## Modules

| Path | What lives there |
|---|---|
| [core](core/src/commonMain/kotlin) | Platform-free model and logic shared by every app and the server (no UI) |
| [app/shared](app/shared/src/commonMain/kotlin) | Compose Multiplatform UI and app logic shared by Android, Desktop and Web |
| [app/androidApp](app/androidApp) | Android entry point (patient app) |
| [app/desktopApp](app/desktopApp) | Desktop JVM entry point (patient and doctor) |
| [app/webApp](app/webApp) | Web entry point, Wasm and JS (doctor) |
| [server](server/src/main/kotlin) | Ktor server: cloud proxy / blind relay, or clinic module with a local model |

## Run

- Android: `./gradlew :app:androidApp:installDebug` (emulator or device)
- Desktop: `./gradlew :app:desktopApp:run`
- Web (Wasm): `./gradlew :app:webApp:wasmJsBrowserDevelopmentRun`
- Server: `./gradlew :server:run`

On Windows use `gradlew.bat`. The Android SDK is taken from `ANDROID_HOME` or `local.properties`.

## Tests

```
python test.py          # core + app/shared JVM tests, server tests, desktop compile
python test.py --all    # also builds the Android APK and the Wasm web app
```

`test.py` picks `gradlew.bat` or `./gradlew` for the OS and writes `local.properties` from
`ANDROID_HOME` / `ANDROID_SDK_ROOT` / the default SDK path when it is missing. Team Hub runs it
through `teamhub.json`.

## Stay in sync

```
python tools/autopull.py      # every 15 s: fetch hub + origin, fast-forward your branch and main
```

It only fast-forwards. Local commits, conflicting edits or a merge in progress are left alone and
reported once. On Windows without a console window:
`pythonw tools/autopull.py --log .git/autopull.log`.

## Secrets

API keys (OpenRouter and others) live only in environment variables or untracked local files,
never in Git or logs. The cloud key stays on the server; clients never see it.
