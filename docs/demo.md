# Easy Paper: three-minute LauzHack demo draft

## 1. Pitch and problem

Easy Paper collects a patient's diary before a visit and gives the doctor a short, source-linked draft to review.

Scenario: a family doctor has 15–20 minutes and cannot collect the full history during the appointment. Our intended user is a free or volunteer clinic serving migrants. Patients enter facts in their own language (EN/DE/UK); clinic staff review the originals. This targets community support and inclusion for IMChange Tech for Good. Clinic adoption and time saved are not yet validated.

## 2. Timeline: 3:00

Roles: A presents and operates the doctor's Desktop/Web app; B operates the Android patient app. Paths below use `C` = `core/src/commonMain/kotlin/org/easypaper` and `U` = `app/shared/src/commonMain/kotlin/org/easypaper`.

This is the target rehearsal flow. **TODO UI** means the underlying logic exists, but the current screen or device integration is unfinished. Show it as a planned step or a clearly labelled prepared capture until wired; do not imply a live interaction works.

| Time | Speaker and spoken cue | On screen / action | Implementation |
|---|---|---|---|
| 0:00–0:15 | A: “A volunteer clinic has a short appointment and patients speaking different languages. We collect their history beforehand.” | Doctor's Desktop/Web app beside Android Today. | `U/ui/shell/Shell.kt`, `DoctorApp.kt`, `PatientApp.kt` |
| 0:15–0:35 | A: “For a headache, the doctor chooses five days of questions, pressure, medicines, diary and questions for the doctor.” | Assemble a request from widgets. **TODO UI:** builder is a placeholder. | `C/invite/RequestDraft.kt`; `U/ui/shell/DoctorApp.kt` |
| 0:35–0:45 | A: “The patient receives a link or QR containing the request.” | Open invitation on Android. **TODO UI:** link handling and QR rendering/scanning. Long requests use a link. | `C/invite/Invite.kt`: `inviteLink`, `parseInvite`, `inviteFitsQr` |
| 0:45–1:00 | B: “I fill in Today in my own language.” | Save an answer and pressure reading; show EN/DE/UK. Show an optional food photo only if capture is wired. | `U/ui/shell/PatientApp.kt`; `U/ui/patient/EntryForms.kt`; photo capture **TODO** |
| 1:00–1:15 | B: “The medicine code is looked up locally. No language model is involved. I check the result.” | Scan a package DataMatrix, inspect and confirm the medicine. **TODO UI/camera:** use a labelled decoded-code example if unavailable. | `C/scan/Scan.kt`: `parseScanCode`, `MedicationDb`; `MedicationData.kt` |
| 1:15–1:35 | B: “After starting a new medicine, I record what changed and leave a question for the doctor.” | Save diary text and a doctor question; open History. The new-medicine note is an added scenario entry, not a fact already in `Demo.record`. | `U/ui/patient/EntryForms.kt`, `HistoryScreen.kt`; `C/demo/Demo.kt` |
| 1:35–1:55 | A: “At the visit, the patient shares a report. AES-256-GCM encrypts it; the blind relay gets ciphertext, and the key travels in the QR.” | Show Share QR, then open report on doctor device. **TODO UI:** Share and receiving flow. | `C/report/Transfer.kt`, `RelayClient.kt`; `server/src/main/kotlin/org/easypaper/Relay.kt` |
| 1:55–2:25 | A: “This draft links to the original entries. The doctor checks each clinic-form field before saving.” | Open a source, inspect an unconfirmed medicine, edit and confirm fields. **TODO UI:** visit currently shows entry counts. | `C/visit/Summary.kt`, `ClinicForm.kt`, `DemoVisit.kt`; `U/ui/shell/DoctorApp.kt` |
| 2:25–2:45 | A: “Facts need no AI. Photos use a small model when needed; the larger model writes one summary. Here are the tokens and cost.” | Cloud versus clinic cost screen; distinguish measured usage from estimates. **TODO wiring:** supply actual server status and report usage. | `U/ui/cost/AiCostScreen.kt`; `C/ai/SummaryPipeline.kt`, `CostComparison.kt` |
| 2:45–3:00 | A: “Android, Desktop and Web share the core and Compose UI. Here is our task and review workflow.” | Shared-code figure, Team Hub task with plan and review; Air capture when available. | `core/src/commonMain`, `app/shared/src/commonMain`, `teamhub.json`; Air screenshots **TODO** |

## 3. Kotlin Multiplatform: shared and platform code

- `core/src/commonMain/kotlin/org/easypaper/`: models, invitations, diary checks, medication lookup, reports, encryption, clinic-form logic and Koog-based AI pipeline; reused by clients and server.
- `app/shared/src/commonMain/`: Compose screens, navigation, theme and EN/DE/UK resources. Entry points: `app/androidApp/src/main/`, `app/desktopApp/src/main/`, `app/webApp/src/webMain/`.
- Camera capture and barcode decoding belong in platform adapters; no camera scanner is implemented yet. Shared `scan/Scan.kt` consumes decoded text.
- Key storage: `core/src/androidMain/kotlin/org/easypaper/storage/AndroidStorage.kt` wraps keys with Android Keystore; `core/src/jvmMain/kotlin/org/easypaper/storage/DesktopStorage.kt` uses an owner-only key file. File I/O is in `core/src/jvmAndAndroidMain/`. The current shell still uses `ui/shell/MemoryStorage.kt`.
- Wasm/JS targets are configured in `core/build.gradle.kts`, `app/shared/build.gradle.kts` and `app/webApp/build.gradle.kts`. Platform implementations live in `app/shared/src/wasmJsMain/` and `jsMain/`; the browser entry point is in `webMain`, rather than duplicating screens.

Measured on this checkout: **6,044 commonMain lines / 6,755 total = 89.47% shared**; other production source sets: **711 lines**. Counts include comments, blank lines and the checked-in medication dataset; exclude tests, resources, build output and `.kts`. The denominator includes server and app entry points, not only KMP libraries.

Reproduce from the repository root in PowerShell with `rg` installed:

```powershell
$shared = 0
$other = 0
rg --files core/src app server/src -g '*.kt' -g '!**/build/**' |
    Where-Object { $_ -match '[\\/]src[\\/](?:main|[^\\/]*Main)[\\/]' } |
    ForEach-Object {
        $lines = [System.IO.File]::ReadAllLines((Join-Path (Get-Location) $_)).Length
        if ($_ -match '[\\/]commonMain[\\/]') { $shared += $lines }
        else { $other += $lines }
    }
$total = $shared + $other
'commonMain={0}; otherMain={1}; total={2}; shared={3:F2}%' -f `
    $shared, $other, $total, (100.0 * $shared / $total)
```

## 4. AXA: frugal multimodal AI

`org.easypaper.ai.SummaryPipeline` in `C/ai/SummaryPipeline.kt`:

1. Compute `factDigest` without LLMs: pressure ranges, water totals, medicines, answers and original text with entry IDs. DataMatrix parsing and local medication lookup also need no LLM.
2. Describe only food photos without patient text using the small vision model. Cloud defaults: `google/gemini-2.5-flash-lite`, at most six photos, 200,000 bytes each, 40 output tokens per description. Oversized photos are skipped; capture/resizing integration remains TODO.
3. Call the larger model once per report on the compact facts, not the entire photo collection. Cloud default: `anthropic/claude-haiku-4.5`, 700 output tokens. Clinic mode uses the same local `gemma4:12b` for both roles, with different token limits.
4. Check reference IDs, flag unsupported lines and remove lines matching advice/diagnosis filters. These checks do not prove factual correctness; the doctor reviews the draft.
5. Reuse an in-memory SHA-256 cache keyed by model, prompts and image. Cache hits record zero new tokens and cost. Cache is lost on server restart.
6. Check estimated cost before calls: cloud default $0.05/report and $9 server-session budget. If over budget or AI is unavailable, return deterministic facts. `ModelCaller.kt` and `Usage.kt` record step, provider/model, input/output tokens, USD, cache status and elapsed milliseconds. Missing provider token counts use an estimate.

| Run | Input / output tokens | Latency | Token charge |
|---|---|---|---|
| Local Ollama `gemma4:12b`, already measured | 1279 / 443 | 8.7 s | $0 |
| Cloud OpenRouter | TODO after obtaining the key | TODO | TODO |

The local measurement does not establish a live image-model run: `Demo.record` has food text and no actual photo bytes. Prepare an undescribed food photo to exercise the vision step. `CostComparison.kt` reprices recorded token counts at configured cloud rates; that estimate is not a measured cloud run. Local $0 excludes hardware and electricity. The demo laptop simulates the clinic module.

## 5. JetBrains: agent-assisted development

Show Team Hub's task board and one task's evidence: owner, plan before edits, review by another team member at the exact commit, and Codex as a second reviewer. `teamhub.json` configures `python test.py`; show an existing result if available. **TODO:** select the concrete task/review evidence and capture JetBrains Air screenshots. Do not claim Air evidence is already prepared.

## 6. Before the demo

- [ ] Rehearse the ten steps within 3:00; resolve every **TODO UI** or label its prepared fallback.
- [ ] Check phone-to-server network access, screen mirroring and the Web build; pre-open apps and relevant files.
- [ ] Keep `OPENROUTER_API_KEY` only in the server environment; set `EASYPAPER_AI=cloud` for the cloud run. Check `/ai/status` without displaying the key; fill the cloud measurement row.
- [ ] Start Ollama during the day, ensure `gemma4:12b` is downloaded and warm it up. For the clinic server set `EASYPAPER_AI=clinic`; verify its Ollama URL and model settings.
- [ ] Load `Demo.record` via long-press title → developer menu → fill demo data. It covers 17–21 November 2026, 35 entries and 10 pressure readings. It contains no stored photo bytes; prepare a separate image and the new-medicine diary example.
- [ ] Use a fresh `newReportId()` for each real relay transfer; objects are downloaded once. Keep QR keys off recordings.
- [ ] Supply `AiCostScreen` with real usage and status: `DoctorApp.kt` currently passes an empty list and `null`, so zero totals there are not a measurement.
- [ ] Offline fallback: use loaded records, Today and History; explain deterministic `factDigest` and form logic. Use labelled prepared captures for unfinished screens. `EASYPAPER_AI=off` works if the local server is reachable; no network means no relay handoff. Do not wait for cloud AI.

## 7. Risks and honest limits

- No iOS target. Demonstrate Android and Desktop; verify Web/Wasm before presenting it as working.
- Not a medical device; intended only to collect and structure facts, with no diagnoses or medical advice. This is not a regulatory certification claim. AI filters are limited, and doctors must review every field; `ClinicForm` saves only confirmed fields after all are decided.
- EN/DE/UK UI resources and patient text are supported; validated medical translation is not demonstrated, and the AI summary prompt currently requests English.
- The blind relay never receives the QR key, but AI endpoints receive readable submitted data. Encryption of the handoff does not make cloud inference blind. Relay storage is in memory and lost on restart; default expiry is 72 hours.
- Medication lookup uses a small local demo dataset. Scanning identifies a package; it does not verify intake or prescribe a dose.
- `report/Report.kt` exports a **FHIR-like** Bundle as groundwork, not a validated FHIR integration or a connection to a clinic's medical-record system.
