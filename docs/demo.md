# Easy Paper: three-minute LauzHack demo

## 1. Pitch

Easy Paper helps doctors get the most out of a short visit. Before the appointment the doctor sends a
request built from widgets; the patient records facts for a few days in their own language (EN/DE/UK/FR/RU); at
the visit the patient shows a QR and the doctor reads a short draft in which every line links to the
original entry, then confirms a pre-filled clinic form field by field. No diagnosis, no advice.

## 2. Timeline: 3:00

A operates the doctor's Desktop (or Web) app, B the Android patient app. `C` = `core/src/commonMain/kotlin/org/easypaper`,
`U` = `app/shared/src/commonMain/kotlin/org/easypaper`.

| Time | Spoken cue | On screen | Code |
|---|---|---|---|
| 0:00–0:15 | A: "A family doctor has 15 minutes. We collect the facts before the visit." | Doctor app beside Android "Today". | `U/ui/shell/*` |
| 0:15–0:35 | A: "For a headache I pick pressure twice a day, water, food, medicines, a diary and questions, for five days." | "New request": widgets and period, then the invite QR/link. | `U/ui/doctor/RequestBuilderScreen.kt`, `C/invite/*` |
| 0:35–0:50 | B: "I open the invite and see exactly what my doctor asked for." | Invite preview, "Start". | `U/ui/patient/*`, `C/invite/Invite.kt` |
| 0:50–1:15 | B: "I add my pressure, water, a food photo without typing, and scan my medicine box." | Today: pressure sheet, +250 ml, food photo, package scan (barcode/DataMatrix + OCR, local list, no AI) -> confirm. | `U/ui/patient/EntryForms.kt`, `U/ui/patient/medications/*`, `C/scan/*` |
| 1:15–1:30 | B: "After the new medicine I write how I feel and ask my doctor a question." | Diary + question, History. | `U/ui/patient/HistoryScreen.kt` |
| 1:30–1:50 | A: "At the visit the patient shows a QR. The report is encrypted; the relay only sees ciphertext, the key is in the QR, and the link works once." | Share -> QR -> doctor opens it. | `U/ui/handover/*`, `C/report/Transfer.kt`, `server/.../Relay.kt` |
| 1:50–2:25 | A: "Every line of the summary links to the original. I confirm field by field; nothing is saved without me." | Summary -> tap a source -> original (with photo); clinic form: confirm/edit; documents section (clinic module). | `U/ui/doctor/VisitScreen.kt`, `C/visit/*`, `C/ai/DocumentExtraction.kt` |
| 2:25–2:45 | A: "Facts need no AI. A small model only looks at an undescribed food photo, a big model writes one summary. Here is the price: cloud vs. the clinic's own module." | AI cost screen: tokens, $ per report, cloud vs local. | `U/ui/cost/AiCostScreen.kt`, `C/ai/*` |
| 2:45–3:00 | A: "One Kotlin codebase for Android, Desktop and Web: 92 % of the client code is shared. Built by three people and their agents on a shared task board with plans and cross-review." | Shared-code number, Team Hub board, a review note. | `core`, `app/shared`, Team Hub |

## 3. Kotlin Multiplatform

- `core/commonMain`: model, invites, diary checks, medicine lookup, reports, encryption, clinic form, Koog AI pipeline — used by all clients and the server.
- `app/shared/commonMain`: all Compose screens, navigation, Apple-HIG theme, motion, EN/DE/UK/FR/RU strings.
- Platform code: camera and recognition (Android CameraX/ML Kit, Desktop/Web file + ZXing/BarcodeDetector), key storage (Android Keystore, Desktop owner-only key file), Web storage, entry points.
- Measured (production `.kt`, without tests, blank lines, comments and imports): **commonMain 6,052 of 6,569 client lines = 92 %**; platform source sets 517 lines; Ktor server 264 lines.

## 4. AXA: frugal multimodal AI

Pipeline `C/ai/SummaryPipeline.kt` (Koog `PromptExecutorAPI`, same code for cloud and clinic):

1. Facts without an LLM (`factDigest`): ranges, totals, medicines, answers, notes with entry ids. Medicine boxes: barcode/DataMatrix + OCR against a local list.
2. Small vision model only for food photos without text; photos shrunk and EXIF-stripped on the phone; at most 6 attempts.
3. Big model once per report; every line must cite entries; advice/diagnosis lines removed; uncited lines marked.
4. SHA-256 cache (repeat = 0 tokens), per-report and server budget caps, per-call ledger (tokens, $, estimated flag), output language EN/DE/UK/FR/RU.

| Run (demo record, 35 entries) | Tokens in / out | Time | Cost |
|---|---|---|---|
| Cloud OpenRouter, Claude Haiku 4.5 | 1116 / 505 | 3.3 s | $0.0036 |
| Cloud Together, MiniMax-M3.1-Flash | 1137 / 3214 (mostly reasoning) | 23.0 s | $0.0021 |
| Clinic module, Ollama gemma4:12b (laptop simulates it) | 1279 / 443 | 8.7 s | $0 cloud |

Local $0 excludes hardware and power. In the clinic module documents never leave the clinic (`/ai/documents` is clinic-only).

## 5. Team and agents (JetBrains)

Team Hub board: every task has a plan before edits, a review by another member at the exact commit, Codex (ChatGPT)
as second reviewer; agents built most code; humans reviewed and merged. Show one task's plan + review note.

## 6. Before the demo

- [ ] `git pull --ff-only hub main`; `python test.py --all` green.
- [ ] Cloud server (default in all apps): `https://74-148-56-1.sslip.io` on the hub VM - relay, AI (OpenRouter, Claude Haiku, budget $3), the doctor's web app. systemd `easypaper` (127.0.0.1:8090, secrets only in `/etc/easypaper/env`, root 600) behind Caddy with a Let's Encrypt certificate; Azure NSG opens 80/443 only. The clinic token for the doctor app is in `~/.config/easypaper/clinic-token.txt` on mykhailo's laptop (never in Git). Update: `:server:installDist` + web dist -> `/opt/easypaper/{server,web}` -> `sudo systemctl restart easypaper`.
- [ ] Clinic module instead (daytime only): run the server locally with `EASYPAPER_AI=clinic` and Ollama `gemma4:12b` warmed up; on mykhailo's laptop 8080 is Apache, so `$env:PORT = "8090"`; set `http://<laptop-ip>:8090` (emulator: `http://10.0.2.2:8090`) in the developer menu. Android 17 then asks for local network access when the patient creates a code - tap Allow.
- [ ] Desktop: `.\gradlew.bat :app:desktopApp:run` starts with demo data; Android: "Load demo diary" on an empty Today.
- [ ] Prepare one food photo without text and one medicine box from the local list.
- [ ] Offline fallback: AI off still gives the facts with links; the relay needs the network.

## 7. Honest limits

- No iOS. Not a medical device: collects and structures facts only; the doctor confirms every field.
- Cloud AI receives de-identified facts in plain form; only the relay hand-off is end-to-end encrypted.
- Relay storage is in memory (72 h TTL); medicine list is a small Swiss subset; FHIR-like export is groundwork, not an EHR integration.
