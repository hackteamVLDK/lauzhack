# Easy Paper: three-minute LauzHack demo

Checked against `main` at `434f2a4b`. A operates the doctor app (Desktop/Web); B operates Android. Prepare the diary, sign-in and invitation before timing the run.

## 1. Pitch

Easy Paper helps a doctor use a short appointment: patients record facts beforehand in their own language; the doctor opens a sourced summary and reviews a clinic form field by field. The diary also works without a request or an account. No diagnoses or medical advice.

**IMChange Tech for Good:** a volunteer clinic with little time and patients speaking different languages. **JetBrains KMP:** shared Kotlin logic and Compose UI on Android, Desktop and Web. **AXA Frugal Multimodal AI:** deterministic facts and medicine matching, selective vision calls, measured cost.

## 2. Timeline: 3:00

Code prefixes: `C` = `core/src/commonMain/kotlin/org/easypaper`, `U` = `app/shared/src/commonMain/kotlin/org/easypaper`, `S` = `server/src/main/kotlin/org/easypaper`.

| Time | Speaker and line | On screen | Code |
|---|---|---|---|
| 0:00–0:10 | A: "A short appointment starts better when the facts are already collected." | Doctor Desktop/Web beside the Android diary. | `U/ui/shell/DoctorApp.kt` |
| 0:10–0:30 | B: "Welcome, a four-page tour, then Google, an email code, or no account. Apple says coming soon." | Prepared Welcome → tour → sign-in choices; profile health details and language. | `U/ui/patient/v2/Onboarding.kt`, `U/ui/account/AccountSignIn.kt`, `U/ui/patient/v2/ProfileSheet.kt` |
| 0:30–0:50 | B: "Summary shows my latest records. I tap a card for statistics and use the plus to add data." | Apple Health-style date/greeting, once-a-day streak card, pinned mini-charts → category → top-right `+`. Trends when history allows, tips; Browse search and bright tiles. | `U/ui/patient/v2/SummaryTab.kt`, `U/ui/patient/v2/SummaryCards.kt`, `U/ui/patient/v2/CategoryDetail.kt`, `U/ui/patient/v2/BrowseTab.kt` |
| 0:50–1:10 | A: "I choose what to record and for how long, then add the patient's details to an invitation." B: "Is this you? I confirm by email code." | Prepared request: widgets, period, name/phone/email → invitation QR/link. Sharing: "Share with your doctor", requests; prepared identity view with masked contacts. | `U/ui/doctor/RequestBuilderScreen.kt`, `U/ui/doctor/RequestBuilderState.kt`, `U/ui/account/PatientInviteForm.kt`, `U/ui/patient/v2/SharingTab.kt` |
| 1:10–1:30 | B: "I record pressure, water, a food photo and medicine. I confirm the registry card and what I actually took." | Prepared forms; one rehearsed package scan or typed label → strength → amount/time. Side-effect questions for a medicine supported by the app's table. | `U/ui/patient/EntryForms.kt`, `U/ui/patient/medications/Recognition.kt`, `U/ui/patient/medications/MedicationsScreen.kt`, `S/catalog/CatalogRoutes.kt` |
| 1:30–1:50 | B: "I choose the period, categories and photos, then share." A: "QR works once; its key never goes to the relay. Code and encrypted file are alternatives." | Sharing → selection → QR / 6-digit code / file. Demonstrate QR once; doctor opens it. | `U/ui/handover/Handover.kt`, `U/ui/doctor/ReceiveReport.kt`, `C/report/Transfer.kt`, `C/report/Handoff.kt`, `C/report/ReportFile.kt`, `S/Relay.kt`, `S/Handoff.kt` |
| 1:50–2:20 | A: "I follow a summary's source to the original, then confirm or edit each field. Documents use the clinic module." | Summary → source → original photo or medicine brief; confirm/edit one field. Prepared document fact and verbatim quote → "Add to form"; the field becomes a draft again. | `U/ui/doctor/VisitScreen.kt`, `U/ui/doctor/VisitState.kt`, `U/ui/doctor/MedicineBrief.kt`, `U/ui/doctor/DocumentsSection.kt`, `C/ai/DocumentExtraction.kt` |
| 2:20–2:45 | A: "Facts and medicine matching need no LLM. A small model labels food photos without text; one larger-model call writes the summary. Here is the cost." | AI cost: selected report, calls, tokens, cache, server budget, cloud/local comparison; measured table below. | `U/ui/cost/AiCostScreen.kt`, `C/ai/SummaryPipeline.kt`, `C/ai/AiConfig.kt`, `S/Ai.kt` |
| 2:45–3:00 | A: "About 92 percent of our client Kotlin is shared. Team Hub held plans and reviews; Codex agents worked in parallel worktrees." | Shared modules; one Team Hub task with plan, review and test result. | `core/src/commonMain`, `app/shared/src/commonMain`, `teamhub.json` |

Handover: **QR** uses a fresh AES-256-GCM key carried only in the QR; ciphertext is deleted after retrieval. The **6-digit code lasts 5 minutes**: claiming requires the clinic token and the doctor's ephemeral P-256 public key; ECDH + HKDF derives the encryption key. The **file** uses PBKDF2-SHA256 + AES-256-GCM, a password of at least 8 characters, and opens locally.

## 3. KMP

- `core/commonMain`: models, diary validation, invitations, registry matching, reports, encryption, clinic-form decisions and the Koog AI pipeline. The Ktor server reuses this logic.
- `app/shared/commonMain`: Compose screens, navigation, charts, EN/DE/UK/FR/RU, iOS-style springs, sheets and transitions with Reduce Motion support.
- Platform source sets: camera, barcode/OCR readers, file pickers, key storage, networking and entry points. Patient: Android; doctor: Desktop/Web. No iOS app in this main.

At the audited commit: **10,805 shared / 11,796 client Kotlin lines = 91.6%** (991 platform lines). Approximate count of tracked production `.kt` under `core` and `app`; excludes tests, blank lines, block comments and lines starting with `//`, `import` or `package`. Server code and resources are outside the denominator. Recalculate from the repository root in PowerShell, without building:

```powershell
@'
from pathlib import Path
import re, subprocess
counts = dict(shared=0, platform=0)
for name in subprocess.check_output(['git', 'ls-files'], text=True).splitlines():
    if not name.startswith(('core/', 'app/')) or '/src/' not in name or not name.endswith('.kt'):
        continue
    source_set = name.split('/src/', 1)[1].split('/')[0]
    if 'test' in source_set.lower():
        continue
    code = re.sub(r'/\*.*?\*/', '', Path(name).read_text(encoding='utf-8'), flags=re.S)
    lines = sum(bool(s.strip()) and not s.lstrip().startswith(('//', 'import ', 'package '))
                for s in code.splitlines())
    counts['shared' if source_set == 'commonMain' else 'platform'] += lines
total = sum(counts.values())
print(f"Shared client Kotlin: {counts['shared']}/{total} = {100*counts['shared']/total:.1f}%")
'@ | python -
```

## 4. AXA: frugal multimodal AI

1. Compute ranges, totals, medicines and entry references without AI (`C/report/Digest.kt`). Barcode/DataMatrix and on-device OCR feed deterministic matching against official server registries.
2. Use a small vision model only for food photos without text: up to 6 attempts per report, at most 200,000 bytes per photo. The app shrinks photos and strips identifying metadata.
3. Make one larger-model summary call per uncached input. Check entry references, remove detected advice/diagnosis lines, and mark unsupported lines "No source". These checks do not prove clinical correctness.
4. Cache calls by SHA-256 input hash; a repeat uses zero new model tokens. Apply per-report and server summary budgets; show tokens, cost and estimated-price flags. Without AI, keep computed facts and source links.

Recorded measurements, preserved from the previous script; **not rerun for task #89**:

| Run (demo record, 35 entries) | Tokens in / out | Time | Cost |
|---|---|---|---|
| Cloud OpenRouter, Claude Haiku 4.5 | 1116 / 505 | 3.3 s | $0.0036 |
| Cloud Together, MiniMax-M3.1-Flash | 1137 / 3214 (mostly reasoning) | 23.0 s | $0.0021 |
| Clinic module, Ollama gemma4:12b (laptop simulates it) | 1279 / 443 | 8.7 s | $0 cloud |

Local $0 excludes hardware and power. `/ai/documents` uses a local model in clinic mode; cloud mode refuses documents.

Medicine importers cover **CH / FR / EU / UA / TR / BY**, about **220,000 registry records/versions** in the documented imports: Swissmedic/Refdata, French BDPM, EMA Article 57, Ukraine's state register, TİTCK and rceth.by. "Any country" is the default: search spans loaded sources without choosing a country; an optional filter remains. Confirm the card and strength, then record an intake separately. Three daily yes/no side-effect questions exist only for medicines matched to the app's local table.

**Label-reading fallback:** a small server model reading a package only after the phone fails would be a fallback, but it is **not implemented in this main**. `/catalog/resolve` accepts codes and text, not images. Typed Cyrillic search works; Android's Latin OCR does not read Cyrillic. Type an unreadable label during this demo.

## 5. Team and agents

Team Hub holds task plans and reviews. Codex agents worked on independent tasks in parallel Git worktrees; humans reviewed and merged the results. Show one task's plan, review at its commit, and test result. `teamhub.json` configures `python test.py` as the project check.

## 6. Before the demo

- [ ] Sync: `git pull --ff-only hub main`; run `python test.py --all` and check the result. These are rehearsal steps; the test script invokes Gradle and builds Android/Web as well as running checks.
- [ ] Open the doctor app at <https://74-148-56-1.sslip.io>. Deployment: VM + Caddy + Let's Encrypt HTTPS. Check from the demo network: `curl.exe --fail --silent --show-error https://74-148-56-1.sslip.io/` (use `curl` on Unix).
- [ ] Enter the private clinic token in the doctor's developer settings; long-press "Requests" to open them. Required for AI, person-bound invitations and claiming 6-digit handovers. The Web setting is in memory: re-enter after reload. Keep secrets off the projected screen and out of Git.
- [ ] In a private PowerShell terminal, check AI: `curl.exe --fail --silent --show-error -H "X-EasyPaper-Clinic-Token: $env:EASYPAPER_CLINIC_TOKEN" https://74-148-56-1.sslip.io/ai/status`. For documents, prepare the local clinic module with `EASYPAPER_AI=clinic` and warmed Ollama; otherwise show its UI and explain the cloud restriction.
- [ ] Install the Android APK: <https://74-148-56-1.sslip.io/download/easypaper.apk>. Verify Google sign-in on the actual phone with this APK/signing certificate and the server's allowed Google client ID. Prepare Welcome/tour for a brief replay.
- [ ] For email codes and "Is this you?", configure server mail delivery and a sender: Resend needs `RESEND_API_KEY`; SMTP needs its credentials. Confirm delivery to the demo mailbox. Prepare patient name/phone/email in the doctor's request.
- [ ] Check `curl.exe --fail --silent --show-error https://74-148-56-1.sslip.io/catalog/coverage`: require loaded CH/FR/EU/UA/TR/BY snapshots, dates and counts. README at the audited main warns the public catalog needs a newer deploy/import. Recognition is conditional until this check passes.
- [ ] Rehearse packages: **ARVELES / Bilaxten (TR)**, **Стрепсілс / Едем / Фервекс (UA)**, **Dafalgan (CH)**. Use one in the timed run; keep the others for questions. Leave "Any country" selected; type Ukrainian labels when OCR fails. Confirm the strength printed on the box.
- [ ] Load the five-day demo diary through the developer menu; pin Summary cards and prepare a food photo without text. Trends need 14 days of history with samples in both seven-day windows; the five-day fixture alone will not show them. The streak card appears on the first Summary visit of the day only.
- [ ] Rehearse a fresh QR (retrieval consumes it), a 5-minute code and an encrypted file/password. Test browser camera or Desktop QR image picker. QR/code need network; file opening is local. AI-off fallback still shows facts with links.

## 7. Honest limits

- Email sign-in and invitation verification need mail delivery; without configuration the server returns 503. Google needs the correct OAuth/signing setup. Apple is coming soon. Accounts do not sync diary records.
- Summary's header has an avatar: open a category and use its top-right `+` to add data. The `+ Add widgets` tile pins cards. There is no iOS app despite the iOS-style UI.
- Cyrillic camera OCR and server model label reading are absent; typed Cyrillic lookup works. A Russian registry importer exists, but the registry is unavailable from the demo server; exclude it from demo coverage. Loaded coverage depends on deployment and imports.
- Handover is end-to-end encrypted; AI summarization sees decrypted facts and undescribed food photos. In cloud mode the server proxy and model provider receive them. Free text may contain identifying information; do not promise full anonymization. In clinic mode inference stays with the local module.
- QR relay data is in memory, expires after 72 hours by default and is lost on restart; code slots expire after 5 minutes. The confirmed form is copied out; there is no live EHR integration.
- Not a medical device. Easy Paper collects and structures facts; AI drafts and source links need the doctor's review. It provides no diagnosis or medical advice.
