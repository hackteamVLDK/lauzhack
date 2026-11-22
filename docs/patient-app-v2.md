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

Floating capsule tab bar: Summary / Sharing / Browse. The selected tab is a tint capsule with white
text and icon. Content reserves the measured bar height, including safe area and large text.
The profile avatar sits top-right; an unnamed profile shows a person glyph.

| Tab | Purpose |
|---|---|
| **Summary** | Ordered widgets with latest values and quick actions; an active doctor plan comes first. No widgets are pinned by default. |
| **Browse** | All categories (search on top) -> category detail: chart, "Add Data", all entries |
| **Sharing** | Doctor requests (active / finished), "Share with a doctor…", sharing history |

Profile (sheet from the avatar): Health details (name, date of birth, height, weight, allergies — all optional),
Medical ID (later), Reminders, Language, Privacy & data (encryption, export backup with password, delete all),
Account (optional: sign in with a passkey for backup — later), About.

## Screens

Summary:

- Header: "Hi, <name>" (or "Hi"), a Large Title greeting for the local time of day, avatar and circular +.
- Two large counters with separate labels: widgets, entries today (all records, local calendar day).
- New profiles have no pinned widgets: "What do you want to track?" and a large circular +.
- + opens the widget gallery: large pastel cards with outline icons and descriptions for every
  `Category.entries` category, including Measurements. Tap to add; already added cards are marked.
- Pinned widgets form an ordered two-column grid. Pressure and water use large numeric values;
  other categories show the latest entry or "No data yet". Each has a quick Add Data action
  (water: Add 250 ml); tapping the card opens the existing category detail.
- Edit or long press enters editing: remove with minus, move left/right in reading order, then Done.
  Removing a widget never deletes entries. The existing `p2_pinned` setting persists the ordered list;
  old saved lists keep their order. A missing or empty setting means no widgets.
- A horizontal strip of pastel category cards offers quick widget additions below the grid.
- The active doctor plan is the first card above the widgets, when present; it never auto-pins categories.
- New widgets appear with an EasyPaperMotion spring; Reduce Motion uses a fade.

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

1. **Welcome** — four large rounded pastel tiles (pressure, water, food, medicines), bundled outline
   icons only; "Your health diary", a short one-line subtitle and a Get started capsule.
2. **Local profile / sign-in choice** — "Welcome", optional name, vertical capsules. Continue works
   locally; Continue with Google and Continue with email are disabled with an explicit Soon label.
   Privacy copy explains that an account is unnecessary and sharing is deliberate.
3. **Privacy & reminders** — local encryption, explicit sharing, deletion; optional reminder preference
   (system notifications are not enabled yet). Language follows the system and can be changed here
   or in Profile. Continue creates the local diary and opens an empty Summary.

No category-selection step. Widgets are added from Summary. Birth date, sex and other health
information remain optional in Profile. Invites opened during onboarding are presented afterwards.
Account = later, optional, from Profile (passkey; backup and second device).

## What changes vs. today

| Today | v2 |
|---|---|
| Tabs Today / History / Share | Summary / Sharing / Browse |
| Needs a doctor's request | Works alone; requests are plans |
| Share = QR generator | Share = "what + how" sheets; QR is one option |
| No onboarding, no profile | Onboarding + Profile (health details, privacy, export, delete) |
| History list | Category detail with chart, ranges, all data |

## Proposed tasks (after approval)

- **Onboarding + Profile** (welcome collage, local profile, privacy/reminders; profile sheet with health details, privacy & data, export, delete all) — pavlo.
- **Summary + Browse + category detail** (ordered widgets, gallery, plan card; category list with search; chart D/W/M/6M, Add Data, all data) — pavlo.
- **Data independent of requests** (core: entries without a request, plans = requests on top, report = categories + period) — vlad.
- **Sharing tab + share flow** (requests list, add request, "What to share" sheet, options sheet with QR; Bluetooth/file shown as "Later") — vlad.
- **Account (later)**: passkey sign-in for backup — not for the demo.
