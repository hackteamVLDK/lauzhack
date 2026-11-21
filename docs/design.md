# Easy Paper — design system

Status: **draft, waiting for approval** (task 3). Nothing in `app/` may use colors, fonts or sizes
that are not defined here. Implementation: `EasyPaperTheme` in `app/shared/src/commonMain`.

## Subject

- **Patient**: phone, often unwell, maybe older, writes in their own language. Fills a few short
  entries a day for 3–14 days. Must finish today's entries in under two minutes and feel safe.
- **Doctor**: big screen, 15-minute visit. Reads the summary in seconds, opens the original behind
  any line, confirms a pre-filled clinic form field by field.
- The app collects and structures facts. It never diagnoses or advises.

## Concept: form and ink

German medical forms (Muster 16, the AU slip, referral forms) print their grid in *dropout ink*,
a red-rose color that scanners ignore, so that only what a person writes is read. Easy Paper takes
that literally:

| Who wrote it | Looks like | Why |
|---|---|---|
| The doctor's request: field names, structure | **Rose**, small, printed | It is the form, the frame around the facts |
| The patient: values, notes, questions | **Ink** blue; own words in a serif | Ballpoint on the form, the part that matters |
| The AI draft for the doctor | **Pencil** grey, marked "Draft" | Can be erased; not a fact until confirmed |
| Waiting for the doctor's confirmation | **Marker** yellow behind the text | A highlighter over what still needs a look |
| Confirmed by the doctor | Graphite + check mark + time | Final, printed |

Provenance is never color-only: drafts carry the word "Draft", confirmed fields a check icon and
time, patient quotes are in quotation marks and set in the serif.

## Color

| Token | Light | Dark | Role |
|---|---|---|---|
| `paper` | `#F6F7F4` | `#1D2024` | Background. Cool form paper, not cream |
| `sheet` | `#FFFFFF` | `#262A2F` | Raised surfaces only: bottom sheets, dialogs, the clinic-form panel |
| `graphite` | `#24272B` | `#E6E4DE` | Printed text, titles, confirmed values |
| `ink` | `#2A3B9E` | `#A8B6FF` | Patient values and the one primary action per screen |
| `rose` | `#B03A55` | `#F09AAC` | Form captions, input baselines, the doctor's request |
| `pencil` | `#5F646C` | `#A3A8B0` | Secondary text, timestamps, AI draft |
| `marker` | `#FFE45C` | `#5A5118` | Background only: "needs confirmation". Text on it is always `graphite` |
| `rule` | `#E3B3BE` | `#6B3A46` | Decorative separators (not input boundaries) |
| `error` | `#A8321A` | `#FF9A80` | Errors, always with an icon and a sentence |

Measured contrast (WCAG 2.1): every text token on `paper` and `sheet` ≥ 5.4:1 in light and ≥ 6.0:1
in dark; `graphite` on `marker` 11.8:1 / 6.3:1; `paper` on `ink` (button text) 8.8:1 / 8.4:1.
Input baselines use `rose` (≥ 5.4:1, meets 3:1 for components); `rule` is decoration only.

Material 3 mapping (components only; screens read `EasyPaperTheme.colors`):
`primary=ink`, `onPrimary=paper`, `background/surface=paper`, `surfaceContainer*=sheet`,
`onSurface=graphite`, `onSurfaceVariant=pencil`, `outline=rose`, `outlineVariant=rule`,
`tertiary=rose`, `error=error`. No baseline purple anywhere, no dynamic color.

## Type

Two families, two voices:

- **IBM Plex Sans** (OFL) — the form: UI, captions, buttons, numbers. Engineered, printed feel;
  Cyrillic incl. Ukrainian ґ є і ї, German ä ö ü ß. Numbers always with tabular figures (`tnum`),
  so `128 / 84` columns line up.
- **Literata** (OFL) — a person's own words: diary notes, food notes, questions to the doctor, and
  patient quotes inside the doctor's summary. A reading serif made for screens, never used for
  headings or UI.

Weights: Plex 400, 500, 600; Literata 400 and 400 italic. No other weights, no ALL CAPS, sentence
case everywhere. Line length ≤ 70 characters (doctor columns), body never below 16 sp on a phone.

| Style | Family | Size / line (sp) | Weight | M3 slot | Use |
|---|---|---|---|---|---|
| Screen title | Plex | 28 / 34 | 600 | `headlineMedium` | "Today", patient name on the doctor screen |
| Section | Plex | 20 / 26 | 600 | `titleLarge` | Widget / section names on desktop |
| Value | Plex `tnum` | 22 / 28 | 500 | `titleMedium`* | Entered numbers: `128 / 84 mmHg` |
| Body | Plex | 16 / 24 | 400 | `bodyLarge` | Default text |
| Words | Literata | 17 / 27 | 400 | (custom `words`) | Patient's own text; +1 sp and looser leading than Plex |
| Caption | Plex | 14 / 20 | 500 | `labelLarge` | Rose field names, buttons |
| Small | Plex `tnum` | 13 / 18 | 400 | `bodySmall` | Times, units, source links, counts |

\* overridden size; the slot is only used so M3 components inherit it.

## Space, layout, shape

- Spacing scale (dp): `4, 8, 12, 16, 24, 32, 48`. Phone gutter 16, desktop gutter 24. Between
  form fields: 24. Touch targets ≥ 48 dp.
- Patient (phone): one column, left aligned, the whole day is **one ruled form page**, scrolled
  top to bottom in a fixed order chosen by the doctor. No cards, no grid.
- Doctor (desktop/web ≥ 1000 dp): two columns, summary 60 % / originals 40 %; the clinic form is
  the only `sheet` panel, below. Under 1000 dp the originals move below the summary.
- Shapes by hierarchy, not one radius for everything: fields have **no box**, only a rose baseline;
  small things (source links, thumbnails, marker) 4 dp; buttons and text inputs 12 dp; sheets and
  dialogs 20 dp top corners. Shadows only on sheets and dialogs (elevation 1).
- Icons: Material Symbols Outlined, weight 300, 24 dp, only where they carry meaning (add, camera,
  scan, check, warning). Imported as vector resources, no icon fonts.

## Motion

One moment only: when the patient saves an entry, its value turns from pencil to ink (150 ms color
change). Sheets slide up because the user opened them. Nothing moves on its own. With reduced
motion the change is instant.

## Principles

1. **The day is one page.** The patient sees a single form in the doctor's order, fills what they
   can, skips what they want; reaching the bottom is being done. No dashboards, streaks or scores.
2. **Who wrote it is always visible.** Form (rose), patient (ink, serif for words), AI draft
   (pencil, "Draft"), confirmed (graphite, check, time). The doctor never has to guess.
3. **Every sentence has a source.** Each line of the summary ends with links to the original
   entries (day and time); every pre-filled clinic field stays under the marker until confirmed.

## Wireframes

Patient, "Today" (phone, 360 dp):

```
┌──────────────────────────────────────┐
│ Today                                │  Screen title, graphite
│ Saturday, 21 November                │  Body, pencil
│ Day 3 of 7, for Dr. Weber            │  Body, pencil
│                                      │
│ Blood pressure, morning              │  Caption, rose
│ 128 / 84 mmHg      pulse 72          │  Value, ink, tnum
│ ──────────────────────────────────── │  rose baseline (the field)
│ 08:10                                │  Small, pencil
│                                      │
│ Blood pressure, evening              │
│ + Add blood pressure                 │  text button, ink, 48 dp
│ ──────────────────────────────────── │
│                                      │
│ Water                                │
│ ▮▮▮▮▮▯▯▯   1.25 of 2 l               │  ink / rule
│ [ − ]  [ + 250 ml ]                  │
│ ──────────────────────────────────── │
│                                      │
│ Food                                 │
│ “Oatmeal with banana, black tea”     │  Words (Literata), ink
│ ┌────┐                               │  photo, 4 dp radius, optional
│ └────┘  + Add food                   │
│ ──────────────────────────────────── │
│                                      │
│ Medicines                            │
│ Ramipril 5 mg, 1 tablet in the       │  Body, ink
│ morning              ✓ Taken 08:15   │
│ + Scan medicine box                  │
│ ──────────────────────────────────── │
│                                      │
│ How you feel                         │
│ “Headache again after lunch, less    │  Words, ink
│ strong than yesterday.”              │
│ + Write how you feel                 │
│ ──────────────────────────────────── │
│                                      │
│ Question for the doctor              │
│ + Ask a question                     │
│ ──────────────────────────────────── │
│                                      │
│ Saved on this phone. Nothing leaves  │  Small, pencil: the end of the page
│ it until you show the QR at your     │
│ visit on Tuesday, 24 November.       │
└──────────────────────────────────────┘
```

Doctor, visit summary (desktop, 1280 dp):

```
┌───────────────────────────────────────────────────────────────────────────────┐
│ Anna Kovalenko, 54                                       Visit today, 10:20   │ title / small
│ Headaches since new medication. 14–20 November, 7 days                        │ body, rose
│ 41 entries, 6 photos, 2 questions                                             │ small, pencil
│ ───────────────────────────────────────────────────────────────────────────── │ rule
│ Summary                         Draft         │ Originals       Wed 18 Nov ▾  │
│                                               │                               │
│ Morning blood pressure 128–142 / 82–90 mmHg.  │ 08:10  BP 138 / 88, pulse 76  │ pencil text = draft
│ Mon 08:10, Thu 07:55                          │ 13:00  Food: soup, bread ┌──┐ │ links, ink, small
│ Headache on 5 of 7 days, mostly after 14:00.  │                          └──┘ │
│ Mon 15:40, Tue 16:05, +3                      │ 15:40  “Headache again after  │ Words
│ Ramipril 5 mg since 15 Nov. Dry cough noted   │        lunch, less strong…”   │
│ since 17 Nov. Tue 20:10                       │                               │
│ Asks: “Can I take ibuprofen with it?”         │ Blood pressure, 7 days        │ chart: 2 lines,
│ Fri 21:02                                     │ 150 ┤      ╭─╮                │ sys / dia, mmHg,
│                                               │ 120 ┤ ─────╯ ╰───  sys        │ no fill, no 3D
│ [ Confirm summary ]   Edit                    │  90 ┤ ───────────  dia        │
│                                               │      14  15  16  17  18 Nov   │
├───────────────────────────────────────────────┴───────────────────────────────┤
│ Clinic form, pre-filled                               3 of 8 fields confirmed │ sheet panel
│                                                                               │
│ Current medication  ▓Ramipril 5 mg 1-0-0▓                  [ Confirm ] Edit   │ marker = to check
│ Blood pressure      ▓138 / 88 mmHg, mean of 7 mornings▓    [ Confirm ] Edit   │
│ Allergies           Not reported                                       Edit   │
│ Smoking             No ✓ Confirmed 10:24                               Edit   │ graphite + check
└───────────────────────────────────────────────────────────────────────────────┘
```

## Copy

Patient: plain, short, active verbs, no advice. One action keeps its name through the flow.

| Key | EN | DE | UK |
|---|---|---|---|
| add_bp | Add blood pressure | Blutdruck eintragen | Додати тиск |
| scan_medicine | Scan medicine box | Packung scannen | Сканувати упаковку |
| write_feeling | Write how you feel | Schreiben, wie es Ihnen geht | Написати, як почуваєтеся |
| ask_question | Ask a question | Frage stellen | Поставити запитання |
| show_qr | Show QR to the doctor | QR-Code dem Arzt zeigen | Показати QR лікарю |
| shown_qr | Shown to the doctor | Dem Arzt gezeigt | Показано лікарю |
| draft | Draft | Entwurf | Чернетка |
| confirm | Confirm | Bestätigen | Підтвердити |

Doctor: compact clinical register, units and times, every AI sentence followed by its sources.
Errors say what happened and what to do, without apologizing: "Photo not saved: storage full.
Delete old photos or skip the photo."

## Checked against the generic AI looks

| Generic look | Here | Decision |
|---|---|---|
| Cream background, serif display, terracotta accent | Cool `#F6F7F4` paper, sans headings, ballpoint blue | First idea was a warm "paper" cream; replaced by form paper so it does not read as the template |
| Near-black + one acid accent | Dark mode is graphite `#1D2024` with soft ink and rose | Same four roles as light, no neon |
| Broadsheet hairlines, zero radius | Lines exist only as input baselines (functional), 12–20 dp radii, wide spacing | Rules are never used just to separate blocks on the phone |
| Identical rounded cards with soft shadow | No cards for the patient; one sheet panel for the doctor's clinic form | Hierarchy from type and spacing |
| ALL-CAPS eyebrows, `A · B · C` meta, `WORD — fragment`, mono data labels, `→` in buttons | Sentence case, comma-separated meta, tabular Plex for data, plain button verbs | The easy-paper skill's own example "14 entries · 3 photos" becomes "14 entries, 3 photos" |
| Hospital blue + white | Blue is ballpoint ink and only means "the patient wrote this / act" | Rose and pencil carry the rest |
