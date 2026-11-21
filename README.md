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
- Web development (Wasm): `./gradlew :app:webApp:wasmJsBrowserDevelopmentRun`
- Web development (JS): `./gradlew :app:webApp:jsBrowserDevelopmentRun`
- Server: `./gradlew :server:run`

On Windows use `gradlew.bat`. The Android SDK is taken from `ANDROID_HOME` or `local.properties`.

### Server and AI access

Configure these environment variables in the server process, then run `./gradlew :server:run`
(HTTP on `http://localhost:8080` by default):

| Variable | Purpose |
|---|---|
| `EASYPAPER_AI` | `cloud`, `clinic` or `off`. Defaults to `cloud` when either `TOGETHER_API_KEY` or `OPENROUTER_API_KEY` is set, otherwise `off`. |
| `EASYPAPER_CLOUD` | `together` or `openrouter`. Defaults to `together` when `TOGETHER_API_KEY` is set, otherwise `openrouter`. Only the selected provider's key is used. |
| `TOGETHER_API_KEY` | Together AI API key for cloud mode with `EASYPAPER_CLOUD=together`; stays on the server. |
| `OPENROUTER_API_KEY` | OpenRouter API key for cloud mode with `EASYPAPER_CLOUD=openrouter`; stays on the server. Cloud keys are not needed for `clinic` or `off`. |
| `EASYPAPER_CLINIC_TOKEN` | Private clinic access token. Clients send the matching value in `X-EasyPaper-Clinic-Token` for every `/ai/*` request. |
| `EASYPAPER_BUDGET_USD` | Server AI spending cap in USD (cloud default: `9.00`). |

For example, set `EASYPAPER_AI=cloud`, `EASYPAPER_CLOUD=together`, `TOGETHER_API_KEY`, `EASYPAPER_CLINIC_TOKEN` and
`EASYPAPER_BUDGET_USD=9.00` in your local environment before starting the server. Do not put real
credentials in commands saved to Git, frontend bundles or logs. Clinic mode uses local Ollama;
`EASYPAPER_OLLAMA_URL` defaults to `http://localhost:11434` and `EASYPAPER_OLLAMA_MODEL` to `gemma4:12b`.

Without a configured clinic token (unset or empty), AI routes return `503 AI access token not configured`.
The exception is `GET /ai/status` in `off` mode, which remains available without a configured token.
With a configured token, a missing or incorrect header returns `401`, including in `off` mode.
All `/ai/*` requests share a sliding limit of 30 requests per minute per connection IP, including
failed authentication and status requests; further requests return `429`. Forwarded IP headers
are not trusted, so clients behind the same reverse proxy share its connection IP limit.
The clinic token and cloud provider keys are never logged or included in responses.

**Cloud privacy:** de-identified facts sent for AI processing leave the clinic and are sent to
Together AI or OpenRouter according to `EASYPAPER_CLOUD` in cloud mode. Easy Paper only collects and
structures facts; it does not diagnose or give medical advice. Documents are processed only in clinic mode.

For deployment, put Ktor behind a reverse proxy that terminates **HTTPS** and forwards requests
to the server on a private interface. Preserve `X-EasyPaper-Clinic-Token` and exclude it and
provider authorization headers from proxy access logs. Use HTTPS for the doctor's app and API.

### Doctor's web app: development or served by Ktor

1. **Development with live reload:** start Ktor with `./gradlew :server:run`, then in another terminal
   run `./gradlew :app:webApp:wasmJsBrowserDevelopmentRun` (Wasm) or
   `./gradlew :app:webApp:jsBrowserDevelopmentRun` (JS). Open `http://localhost:8081` (webpack).
   Both development servers proxy `/ai` and `/reports` to `http://localhost:8080`, preserving paths
   and request headers. The app uses the page's origin, so API calls use the same origin as the page.
2. **Serve the built app from Ktor:** run `./gradlew :app:webApp:wasmJsBrowserDistribution`, then
   `./gradlew :server:run` and open `http://localhost:8080`. Ktor serves the Wasm production build
   from `app/webApp/build/dist/wasmJs/productionExecutable`. To serve the JS build instead, run
   `./gradlew :app:webApp:jsBrowserDistribution` and set `EASYPAPER_WEB_DIR` to the absolute path of
   `app/webApp/build/dist/js/productionExecutable` before starting Ktor. `EASYPAPER_WEB_DIR` also
   supports any other directory containing the built web app.

For authenticated AI, supply the private clinic token at runtime through `ShellSettings(aiToken = ...)`
when creating `EasyPaperApp`; `serverUrl` can override the default page origin. Both the summary
connection and the AI cost/status screen pass this token to `AiClient`. Its default is `null`,
so an unconfigured client can only access the public off-mode status. The server environment
variable is not automatically exposed to the browser; never embed the token in a public web build.

## Tests

```
python test.py          # core + app/shared JVM tests, server tests, desktop compile
python test.py --all    # also builds the Android APK and the Wasm web app
```

`test.py` picks `gradlew.bat` or `./gradlew` for the OS and writes `local.properties` from
`ANDROID_HOME` / `ANDROID_SDK_ROOT` / the default SDK path when it is missing. The default run
works without an Android SDK; only `--all` needs it. It also compiles `core` for JS and Wasm, so a
JVM-only call in shared code fails the run instead of breaking the web app later. Team Hub runs it through `teamhub.json`.

Live AI tests are disabled by default. Set `EASYPAPER_LIVE=together` and `TOGETHER_API_KEY` to use
Together AI, `EASYPAPER_LIVE=openrouter` and `OPENROUTER_API_KEY` for OpenRouter, or
`EASYPAPER_LIVE=ollama` for local Ollama. The live server test also requires `EASYPAPER_CLINIC_TOKEN`.
Keys are read from the environment and never printed.

## Stay in sync

```
python tools/autopull.py      # every 15 s: fetch hub + origin, fast-forward your branch and main
```

It only fast-forwards. Local commits, conflicting edits or a merge in progress are left alone and
reported once. On Windows without a console window:
`pythonw tools/autopull.py --log .git/autopull.log`.

## Secrets

API keys (Together AI, OpenRouter and others) live only in environment variables or untracked local files,
never in Git or logs. The cloud key stays on the server; clients never see it.
