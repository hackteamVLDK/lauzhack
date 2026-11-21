---
name: easy-paper-design
description: Visual and UX design rules for the Easy Paper app (Kotlin/Compose Multiplatform — patient app on Android/Desktop, doctor view on Web/Desktop). Use before creating or changing any screen, theme, color, typography, component, icon, copy or string resource in Easy Paper, and when reviewing UI work. Works together with the frontend-design skill; this one translates it to Compose and to a medical-intake product.
---

# Easy Paper — design rules

Load `frontend-design` too: its anti-template rules (calibration list of AI-generated looks,
two-pass plan → critique → build, restraint, writing) apply here. This file adds the product
context and the Compose specifics.

## Who and what

- **Patient** (phone, Android; also Desktop): often unwell, anxious, may be older, may read the
  app in a language that is not the doctor's. Fills short daily entries for a few days. The app
  must feel calm, trustworthy and quick — finishing today's entries in under two minutes.
- **Doctor** (Web/Desktop, big screen, 15-minute visit): needs the summary in seconds, then the
  originals behind every line, then a pre-filled clinic form to confirm field by field.
- The app never diagnoses or advises. Copy must not sound like advice ("you should…", "this
  means…"). It collects and structures facts.

## The design system lives in one place

- First UI step of the project: write `docs/design.md` with the plan from `frontend-design`
  (4–6 named colors for light AND dark, typefaces and roles, type scale, spacing, shapes,
  2–3 principles, ASCII wireframes of the patient "Today" screen and the doctor summary).
  Review it against the calibration list, show it to the user, build only after approval.
- Implement it once in `commonMain` as `EasyPaperTheme`: custom `ColorScheme` (light + dark),
  `Typography`, `Shapes`, spacing tokens. Screens use only theme tokens — no hex colors,
  no `dp`/`sp` literals for design values, no `Color(0xFF…)` inside screens.
- **Visual direction (set by mykhailo): a native Apple look, following Apple's Human Interface
  Guidelines** — iOS text styles (Large Title 34, Body 17, Footnote 13…), semantic system colors,
  one tint, inset grouped lists, large titles, tab bar on the phone, sidebar + grouped form on
  desktop. `docs/design.md` has the exact tokens; it wins over anything below.
- Fonts: Inter / Inter Display (open stand-in for SF Pro), bundled via Compose resources
  (`composeResources/font`), with Cyrillic and German glyphs. Never ship SF Pro or SF Symbols:
  their license allows Apple platforms only. Icons: Material Symbols Rounded.
- Material 3 is the component base, but not its look: no default purple/baseline scheme,
  no Material elevation tints, no FAB; components are restyled to the iOS shapes in the theme.

## Anti-slop rules specific to this app

- No gradients, glassmorphism, glowing blobs, sparkles, robot or brain icons, "AI ✨" badges.
  AI work is shown as plain facts: "Written from 14 entries, 3 photos" with a link to each original.
- No emoji in UI. Icons from one icon set, one weight.
- Group with iOS inset grouped sections only where iOS would group; no shadows on sections,
  no Material cards, no gradients. Hierarchy comes from the text styles and spacing.
- Colors only from `docs/design.md`: semantic tokens + one tint. Category colors (blood pressure
  pink, water cyan…) only on 29 dp icon tiles and category names, never as the only signal.
- Charts (blood pressure) are simple: one line per series, real units (mmHg), no 3D, no decoration.
- Real content everywhere: real-looking complaints, medicine names, dates — never lorem ipsum.

## Accessibility floor (do not announce, just meet it)

- Text contrast WCAG AA in light and dark; body text ≥ 16sp on phone; touch targets ≥ 48dp.
- Everything works with system font scaling up to 200% — no clipped text, no fixed heights on text.
- Every icon-only control has a `contentDescription`; color is never the only signal.
- Respect reduced motion; motion only to show what changed after the user's action.

## Localization

- Every visible string in `composeResources/values*/strings.xml` (EN, DE, UK at least); no
  hard-coded text in composables. German strings are ~30% longer — layouts must survive them.
- Dates, numbers and units through locale-aware formatting.

## Copy

- Patient voice: plain, short, sentence case, active verbs. "Add blood pressure", "Scan medicine
  box", "Write how you feel". Errors say what happened and what to do; they don't apologize.
- Doctor voice: compact medical register, units, timestamps; every AI sentence links to its source.
- One action keeps one name through the flow (button "Share with doctor" → screen "Shared with doctor").

## Self-critique before submit

- Run the Desktop app, take screenshots of each changed screen in light and dark, in EN and DE,
  and at large font scale. Compare with `docs/design.md`; remove one unnecessary element.
- For UI review use the checklist above plus `frontend-design`'s calibration list.
