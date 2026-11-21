# Patient app v2 — a personal health diary first, doctor requests second

Status: **approved by mykhailo** (tasks on the board). Visual rules stay in `docs/design.md` (Apple HIG look).
Inspiration: Apple Health (Summary / Sharing / Browse, "Add Data", Medications, Health Details, Medical ID) and
the HIG chapters Onboarding, Managing accounts, Charts, Activity views (share), Privacy.

## Why change

Today the app only makes sense with a doctor's request, starts on an empty "Today", and "Share" is a QR
generator. v2 makes it a useful diary on its own; a doctor's request becomes a plan on top of the same data;
sharing is a deliberate action with options.

## Principles

1. **Useful without a doctor.** Anyone can log pressure, water, food, medicines, symptoms, diary. A doctor's
   request only adds a plan (what, how often, until when) and reminders.
2. **Data and plans are separate.** Entries belong to the person, not to a request. A report = a selection of
   categories and a period (pre-filled from a request) — shared on purpose.
3. **Share is a verb, not a QR button** (HIG: Activity views). "Share with a doctor…" opens a sheet: what, which
   period, then *how*: QR now; nearby (Bluetooth: network name + one-time password) and file later.
4. **No sign-up wall** (HIG: Managing accounts — delay sign-in). The first launch creates a local profile; an
   account (passkey) is offered only when it brings something: backup, a second device.
5. **Privacy as a feature** (HIG: Privacy): encrypted on the phone, nothing leaves without Share, delete all.

## Structure (phone)

Bottom tab bar, three tabs like Health; the profile avatar sits top-right on every tab.

| Tab | Purpose |
|---|---|
| **Summary** | Today at a glance: active doctor plans (progress), due reminders, pinned categories with latest value, highlights ("Pressure this week 128–142/82–90 mmHg") |
| **Browse** | All categories (search on top) -> category detail: chart, "Add Data", all entries |
| **Sharing** | Doctor requests (active / finished), "Share with a doctor…", sharing history |

Profile (sheet from the avatar): Health details (name, date of birth, height, weight, allergies — all optional),
Medical ID (later), Reminders, Language, Privacy & data (encryption, export backup with password, delete all),
Account (optional: sign in with a passkey for backup — later), About.

## Screens

Summary (first launch after onboarding, no doctor yet):
```
┌──────────────────────────────────────┐
│ Summary                         (MT) │ large title, avatar -> profile
│ ╭──────────────────────────────────╮ │
│ │ ▣ Blood pressure          08:10 ›│ │ pinned categories, latest value
│ │   128/84 mmHg                    │ │
│ │ ▣ Water today       1.25 of 2 l  │ │ quick +250 ml right in the row
│ ╰──────────────────────────────────╯ │
│ Highlights                            │
│ ╭──────────────────────────────────╮ │
│ │ Pressure this week 128–142/82–90 │ │ facts, never advice
│ ╰──────────────────────────────────╯ │
│ [ Edit pinned ]                       │
│   Summary      Sharing      Browse    │
└──────────────────────────────────────┘
```

Summary with an active doctor request — the plan card comes first:
```
│ ╭──────────────────────────────────╮ │
│ │ Dr. Weber · headache       Day 3/7│ │ progress ring/bar, due items
│ │ Left today: evening pressure,    │ │
│ │ how you feel                   › │ │ -> plan checklist for today
│ ╰──────────────────────────────────╯ │
```

Browse:
```
│ Browse                          (MT) │
│ [ Search ]                           │
│ ▣ Blood pressure                   › │ category tiles as in design.md
│ ▣ Water                            › │
│ ▣ Food                             › │
│ ▣ Medicines                        › │
│ ▣ Symptoms (questionnaires)        › │
│ ▣ How you feel (diary)             › │
│ ▣ Questions for the doctor         › │
```

Category detail (Health pattern):
```
│ ‹ Browse   Blood pressure   Add Data │ "Add Data" top-right opens the entry sheet
│ [ D | W | M | 6M ]                   │ segmented control
│ 128–142 / 82–90 mmHg   17–21 Nov     │ range for the visible period
│ ╭ chart: sys ● / dia ◆, dated axis ╮ │
│ Show all data                      › │ list, swipe to delete, tap to edit
│ About blood pressure records       › │ plain explanation, no advice
```

Sharing:
```
│ Sharing                         (MT) │
│ Requests from doctors                │
│ ╭──────────────────────────────────╮ │
│ │ Dr. Weber · headache  17–23 Nov ›│ │ active, progress
│ ╰──────────────────────────────────╯ │
│ [ Add a request from a doctor ]      │ scan invite QR / paste code / link
│ [ Share with a doctor… ]             │ prominent
│ Shared                               │
│   Dr. Weber, 21 Nov 10:20, 41 entries│ history, nothing stored on a server
```

Share with a doctor — step 1 (sheet "What to share"):
```
│ Cancel     Share with a doctor  Next │
│ Period          17–23 Nov          › │ pre-filled from a request
│ Blood pressure                [on]   │
│ Water                         [on]   │
│ Food (6 photos)               [on]   │
│ Medicines                     [on]   │
│ Diary                         [on]   │
│ Include photos                [on]   │
│ 41 entries, 6 photos will be shared  │ footer
```
Step 2 (share options sheet, like an action sheet):
```
│ Share 41 entries with your doctor    │
│ ▣ Show QR code                     › │ now: encrypted, one-time, key only in the QR
│ ▣ Nearby (Bluetooth)          Later  │ future: phone shows network name + one-time
│                                      │ password, the doctor's device connects
│ ▣ Save encrypted file         Later  │ future
│ Cancel                               │
```

## Onboarding (first launch, under a minute; HIG: Onboarding)

1. **Welcome** — what Easy Paper does in three lines with icons (record, see trends, share with your doctor) -> Continue.
2. **Language** — preselected from the system (EN/DE/UK).
3. **About you** — name, date of birth, sex: all optional, "only on this phone and in what you share".
4. **Privacy** — encrypted on the phone; nothing leaves without Share; delete everything any time.
5. **Reminders** — explain before the system permission: "Allow reminders" / "Not now".
6. **Choose what to track** — preselected common categories, pinned to Summary; "Have a request from your doctor?" -> scan/paste, or "Later".

No account step. Account = later, optional, from Profile (passkey; backup and second device).

## What changes vs. today

| Today | v2 |
|---|---|
| Tabs Today / History / Share | Summary / Sharing / Browse |
| Needs a doctor's request | Works alone; requests are plans |
| Share = QR generator | Share = "what + how" sheets; QR is one option |
| No onboarding, no profile | Onboarding + Profile (health details, privacy, export, delete) |
| History list | Category detail with chart, ranges, all data |

## Proposed tasks (after approval)

- **Onboarding + Profile** (welcome, language, about you, privacy, reminders, choose categories; profile sheet with health details, privacy & data, export, delete all) — pavlo.
- **Summary + Browse + category detail** (pinned categories, highlights, plan card; category list with search; chart D/W/M/6M, Add Data, all data) — pavlo.
- **Data independent of requests** (core: entries without a request, plans = requests on top, report = categories + period) — vlad.
- **Sharing tab + share flow** (requests list, add request, "What to share" sheet, options sheet with QR; Bluetooth/file shown as "Later") — vlad.
- **Account (later)**: passkey sign-in for backup — not for the demo.
