# Easy Paper — design system

Status: **draft v2, waiting for approval** (task 3). Direction set by mykhailo: look and behave like
a native Apple app, following Apple's Human Interface Guidelines (HIG). Nothing in `app/` may use
colors, fonts or sizes that are not defined here. Implementation: `EasyPaperTheme` in
`app/shared/src/commonMain`.

## Subject

- **Patient** (phone, Android; also Desktop): often unwell, maybe older, writes in their own
  language. A few short entries a day for 3–14 days; today's entries in under two minutes.
- **Doctor** (Web/Desktop, 15-minute visit): reads the summary in seconds, opens the original behind
  any line, confirms a pre-filled clinic form field by field.
- The app collects and structures facts. It never diagnoses or advises.

## Why it looks like this

Apple Health is the reference people already trust for personal health data: calm grouped lists,
one category color per kind of data, big readable numbers, the system's own typography. Easy Paper
borrows that grammar so a patient feels at home in seconds, and borrows macOS Settings / Notes for
the doctor: a sidebar, a grouped form, nothing decorative.

Apple's own assets are not used: SF Pro and SF Symbols may only ship in apps for Apple platforms,
and this app runs on Android, Desktop and Web. We use open equivalents (below).

## Color

System-style semantic tokens, light and dark. Values follow iOS; where Apple's default fails WCAG AA
for small text we take Apple's own "Increase Contrast" value.

| Token | Light | Dark | Role (HIG name) |
|---|---|---|---|
| `background` | `#F2F2F7` | `#000000` | Screen behind grouped sections (systemGroupedBackground) |
| `cell` | `#FFFFFF` | `#1C1C1E` | Grouped sections, cards, sidebar rows (secondarySystemGroupedBackground) |
| `cellRaised` | `#FFFFFF` | `#2C2C2E` | Sheets, popovers, nested groups (tertiary) |
| `label` | `#000000` | `#FFFFFF` | Primary text |
| `secondaryLabel` | `#6C6C70` | `#98989F` | Dates, units, footers (Apple default `#8A8A8E` is 3.4:1, too low) |
| `separator` | `#C6C6C8` | `#38383A` | Hairlines between rows (opaqueSeparator), decoration only |
| `fill` | `#E5E5EA` | `#2C2C2E` | Gray buttons, badges, steppers (systemGray5) |
| `tint` | `#0066CC` | `#0070E0` | The one accent as a fill: prominent buttons, selection |
| `link` | `#0066CC` | `#409CFF` | The accent as text: plain and tinted buttons, source links |
| `confirmed` | `#1F7A35` | `#30DB5B` | "Confirmed" text next to a check icon |
| `destructive` | `#D70015` | `#FF6961` | Delete, errors (with an icon and a sentence) |
| `review` | `#FFF4CC` | `#3A3000` | Background of a field the doctor still has to confirm |

Category accents, like Apple Health, are used as the background of 29 dp icon tiles (white glyph)
and for the category name above a value. Pastel widget surfaces are the exception described below.
Accent colors never carry body text and are never the only signal:

| Category | Color | Health equivalent |
|---|---|---|
| Blood pressure | `#FF2D55` pink | Heart |
| Water | `#00C0E8` cyan | Nutrition / water |
| Food | `#34C759` green | Nutrition |
| Medicines | `#00C3D0` teal | Medications |
| Questionnaire | `#6155F5` indigo | Symptoms |
| How you feel (diary) | `#FF8D28` orange | State of mind |
| Question for the doctor | `#8E8E93` gray | — |

Category names drawn as text use the accessible darker shade in light mode (e.g. pink text
`#C9184A`); the implementation keeps both values per category.

Measured contrast: `label`, `secondaryLabel`, `link`, `confirmed`, `destructive` on `cell` and on
`background` ≥ 4.5:1 in both themes. White on `tint`: 5.57:1 light, 4.78:1 dark, including small
selected-tab labels. Rule from HIG: one accent color, color never the only signal.

### Patient v2.1 widget surfaces

Category `soft` tokens in `Color.kt` provide light pastels and muted dark surfaces. Text and outline
icons use `softText` (the primary `label`), tested at ≥ 4.5:1 on every surface in both themes.
Use these on onboarding collage tiles, gallery cards, the horizontal category strip and Summary
widgets; ordinary grouped lists keep `cell`. No photos, emoji, gradients or shadows on widget cards.

| Category | Soft light | Soft dark |
|---|---|---|
| Blood pressure | `#FFDAD8` | `#3D252B` |
| Water | `#CEEDF3` | `#18343D` |
| Food | `#DFEDD2` | `#263724` |
| Medicines | `#CDEAE4` | `#1B3633` |
| Symptoms | `#E2DFF7` | `#2D2942` |
| Diary | `#FFDFC1` | `#3D3023` |
| Doctor questions | `#E4E7EB` | `#303338` |
| Measurements | `#EEDDF2` | `#37293C` |

Use continuous 24 dp corners for large pastel tiles, `largeTitle` for greetings and large readings,
and capsules for onboarding actions. New widgets spring into place via EasyPaperMotion; Reduce
Motion fades. The gallery and grid grow with text rather than clipping labels.

`FloatingTabBar` uses a `cellRaised` capsule, a light 6 dp shadow and a `tint` capsule with white
`onTint` text/icon for the selected tab. Its three equal touch targets grow with text. The shell
reserves its measured height and navigation safe area so the last action remains reachable.
Patient layout tokens: content width 520 dp for onboarding, category strip width 156 dp, gallery
minimum height 176 dp, widget minimum height 208 dp, collage tiles 164/120 dp, hero icons 64 dp,
empty-state + 80 dp and tab-bar maximum width 440 dp. All dimensions live in the theme.

## Type

**Inter** (OFL) stands in for SF Pro: same neo-grotesque construction, made for screens, with
**Inter Display** for sizes ≥ 20 (like SF Pro Display vs Text). Full Cyrillic incl. ґ є і ї, German
ä ö ü ß. Numbers use tabular figures (`tnum`). Tracking follows Inter's dynamic metrics (slightly
negative, more for large sizes). Weights: Regular 400, Medium 500, Semibold 600, Bold 700 only;
never Light or Thin (HIG). Sentence case everywhere, no ALL CAPS.

Apple's text styles at the default size (sp = pt), mapped to Material 3 slots so components inherit:

| Style | Size / line | Weight | M3 slot | Use |
|---|---|---|---|---|
| Large Title | 34 / 41 | Bold | `displaySmall` | "Today", patient name on the doctor screen |
| Title 1 | 28 / 34 | Semibold | `headlineLarge` | Big values: `128/84` |
| Title 2 | 22 / 28 | Bold | `headlineMedium` | Doctor section titles |
| Title 3 | 20 / 25 | Semibold | `headlineSmall` | Sheet titles |
| Headline | 17 / 22 | Semibold | `titleMedium` | Row titles, button labels |
| Body | 17 / 22 | Regular | `bodyLarge` | Default text, patient's own words |
| Callout | 16 / 21 | Regular | `bodyMedium` | Doctor summary text |
| Subheadline | 15 / 20 | Regular | `titleSmall` | Category names (semibold), secondary lines |
| Footnote | 13 / 18 | Regular | `bodySmall` | Section footers, timestamps, source links |
| Caption 1 | 12 / 16 | Regular | `labelMedium` | Chart axes, badges |

Minimum size 12 sp for anything people must read (HIG minimum is 11). Everything scales with the
system font size up to 200 %; rows grow, text never truncates at large sizes.

## Layout and shape

- Spacing (dp): `4, 8, 12, 16, 20, 24, 32`. Screen margin 16 (phone) / 20 (wide). Between grouped
  sections 24 plus the header. Padding between bordered controls 12, around plain ones 24 (HIG).
- Touch targets ≥ 48 dp (Android minimum, above Apple's 44 pt). Rows at least 48 dp tall, grow with text.
- **Inset grouped lists** are the main structure: `cell` sections on `background`, 12 dp continuous
  corners ("squircle", not plain circular arcs), hairline separators inset to the text start.
  Section header: Subheadline semibold `label`; footer: Footnote `secondaryLabel`.
- Navigation: patient app has a large title that collapses into a 17 sp semibold inline title on
  scroll, and a floating capsule tab bar (Summary, Sharing, Browse). Doctor app: sidebar 260 dp (requests), content
  column max 720 dp, the clinic form as a grouped form (label left, value right) like macOS Settings.
- Shapes: icon tiles 29 dp / 7 dp radius; grouped sections 12; buttons in vertical stacks rounded
  rectangle 12, buttons in a row capsule; sheets 16 top corners. Shadows: none on sections; sheets,
  popovers and the floating patient tab bar only.
- Buttons (HIG roles): **prominent** (filled `tint`, white semibold label, 50 dp high) at most one per
  view; **tinted** (`tint` 15 % fill, `tint` label); **gray** (`fill`, `label`); **plain** (`tint` text).
  Rank by style, not size. Delayed actions show a progress indicator in the button and change the
  label ("Encrypting…").
- Icons: Material Symbols Rounded, weight 400, the closest open match to SF Symbols geometry; 22 dp
  in rows, white on category tiles. Chevron `›` for rows that drill in. Every icon-only control has a
  content description.

## Motion

Apple-like springs (damping 0.85), only in answer to an action: a sheet rising, a large title
collapsing, a value changing after save, a check appearing on confirm. Nothing moves on its own.
With reduced motion, movement becomes a short fade.

## Principles

1. **Feel like the phone's own health app.** Grouped sections, category tiles, big numbers with
   small units. No dashboards, scores, streaks, gradients, mascots or AI sparkles.
2. **Who wrote it is always visible.** Patient entries are plain facts with time. AI text sits under
   a gray "Draft" badge with "Written from 41 entries". A field waiting for the doctor has the
   `review` background and a Confirm button; once confirmed it shows a green check and the time.
3. **Every sentence has a source.** Each line of the summary ends with links to the original entries
   (weekday and time); tapping opens that entry.

## Wireframes

Patient, "Today" (phone, 360 dp, light):

```
┌──────────────────────────────────────┐
│ Today                                │  Large Title
│ Saturday, 21 November                │  Subheadline, secondaryLabel
│ Day 3 of 7 for Dr. Weber             │
│                                      │
│ ╭──────────────────────────────────╮ │  grouped section (cell, 12 dp)
│ │ ▣ Blood pressure          08:10 ›│ │  pink tile + name, footnote time
│ │   128/84 mmHg   pulse 72         │ │  Title 1 tnum + Subheadline unit
│ ├──────────────────────────────────┤ │  hairline, inset to text
│ │ ▣ Evening reading      + Add     │ │  tinted capsule button
│ ╰──────────────────────────────────╯ │
│                                      │
│ ╭──────────────────────────────────╮ │
│ │ ▣ Water             1.25 of 2 l  │ │
│ │   ●●●●●○○○      [ − ]  [ + ]     │ │  gray capsule stepper
│ ╰──────────────────────────────────╯ │
│                                      │
│ ╭──────────────────────────────────╮ │
│ │ ▣ Food                    13:00 ›│ │
│ │   Oatmeal with banana, tea       │ │  Body
│ │ ▣ Medicines                      │ │
│ │   Ramipril 5 mg, 1 in the        │ │
│ │   morning        ✓ Taken 08:15   │ │
│ │ ▣ How you feel                   │ │
│ │   + Write how you feel           │ │  plain button
│ │ ▣ Question for the doctor        │ │
│ │   + Ask a question               │ │
│ ╰──────────────────────────────────╯ │
│ Saved on this phone. Shared only     │  Footnote footer
│ when you show the QR at your visit   │
│ on Tuesday, 24 November.             │
│──────────────────────────────────────│
│   Today      History      Share      │  tab bar, tint = selected
└──────────────────────────────────────┘
```

Doctor, visit (desktop, 1280 dp, light):

```
┌──────────────┬────────────────────────────────────────────────────────────────┐
│ Requests     │ Anna Kovalenko, 54                          Visit today, 10:20 │ Large Title
│              │ Headaches since new medication, 14–20 November                 │ Subheadline
│ ● Kovalenko  │                                                                │
│   today 10:20│ Summary  [Draft]   Written from 41 entries, 6 photos           │ Title 2 + badge
│   Schulz     │ ╭────────────────────────────────────────────────────────────╮ │
│   Mon 09:00  │ │ Morning blood pressure 128–142/82–90 mmHg.                 │ │ Callout
│              │ │ Mon 08:10  Thu 07:55                                       │ │ Footnote links
│ + New request│ │ Headache on 5 of 7 days, mostly after 14:00.               │ │
│              │ │ Mon 15:40  Tue 16:05  3 more                               │ │
│              │ │ Asks: "Can I take ibuprofen with it?"     Fri 21:02        │ │
│              │ ╰────────────────────────────────────────────────────────────╯ │
│              │ [ Confirm summary ]   Edit                                     │ prominent + plain
│              │                                                                │
│              │ Blood pressure, 7 mornings                                     │
│              │ ╭────────────────────────────────────────────────────────────╮ │
│              │ │ 150 ┤      ●                                    ● sys      │ │ circles = systolic
│              │ │ 120 ┤ ●──●─╯ ●──●──●                            ◆ dia      │ │ diamonds = diastolic
│              │ │  90 ┤ ◆──◆──◆──◆──◆──◆                                     │ │
│              │ │      14  15  16  17  18  19  20 Nov                       │ │
│              │ ╰────────────────────────────────────────────────────────────╯ │
│              │                                                                │
│              │ Clinic form                          3 of 8 fields confirmed   │
│              │ ╭────────────────────────────────────────────────────────────╮ │
│              │ │ Current medication   Ramipril 5 mg 1-0-0       (Confirm)   │ │ review bg
│              │ │ Blood pressure       138/88 mmHg, mean         (Confirm)   │ │ review bg
│              │ │ Smoking              No           ✓ Confirmed 10:24        │ │
│              │ ╰────────────────────────────────────────────────────────────╯ │
└──────────────┴────────────────────────────────────────────────────────────────┘
```

## Copy

Patient: plain, short, sentence case, active verbs, no advice. One action keeps its name.

| Key | EN | DE | UK |
|---|---|---|---|
| add_bp | Add blood pressure | Blutdruck eintragen | Додати тиск |
| scan_medicine | Scan medicine box | Packung scannen | Сканувати упаковку |
| write_feeling | Write how you feel | Schreiben, wie es Ihnen geht | Написати, як почуваєтеся |
| ask_question | Ask a question | Frage stellen | Поставити запитання |
| show_qr | Show QR to the doctor | QR-Code dem Arzt zeigen | Показати QR лікарю |
| draft | Draft | Entwurf | Чернетка |
| confirm | Confirm | Bestätigen | Підтвердити |

Doctor: compact clinical register, units and times, every AI sentence followed by its sources.
Errors say what happened and what to do: "Photo not saved: storage full. Delete old photos or skip
the photo."

## Checked against generic looks

| Risk | Decision |
|---|---|
| Copying Apple pixel for pixel (SF Pro, SF Symbols, Health icon) | Not allowed off Apple platforms; Inter + Material Symbols Rounded, own icon |
| "SaaS card kit" (same card everywhere, soft shadows, gradients) | Grouped sections only where iOS would group; no shadows on sections, no gradients |
| ALL CAPS eyebrows, `A · B · C` meta, `→` in buttons | Sentence-case headers, comma-separated meta, plain verbs |
| Low-contrast iOS gray for small text | Increase-Contrast grays (`#6C6C70`) |
| AI shown as magic | AI text is a gray "Draft" with sources, never a sparkle or robot |
