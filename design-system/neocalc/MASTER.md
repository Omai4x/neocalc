# Design System Master File

> **LOGIC:** When building a specific page, first check `design-system/pages/[page-name].md`.
> If that file exists, its rules **override** this Master file.
> If not, strictly follow the rules below.

---

**Project:** NeoCalc
**Generated:** 2026-08-12 21:36:16
**Category:** Calculator & Unit Converter

---

## Global Rules

### Color Palette

| Role | Hex | CSS Variable |
|------|-----|--------------|
| Primary | `#EA580C` | `--color-primary` |
| On Primary | `#FFFFFF` | `--color-on-primary` |
| Secondary | `#F97316` | `--color-secondary` |
| Accent/CTA | `#2563EB` | `--color-accent` |
| Background | `#1C1917` | `--color-background` |
| Foreground | `#FFFFFF` | `--color-foreground` |
| Muted | `#2C1E16` | `--color-muted` |
| Border | `rgba(255,255,255,0.08)` | `--color-border` |
| Destructive | `#DC2626` | `--color-destructive` |
| Ring | `#EA580C` | `--color-ring` |

**Color Notes:** Operation orange on dark

### Typography

- **Heading Font:** Inter
- **Body Font:** Inter
- **Mood:** dark, cinematic, technical, precision, clean, premium, developer, professional, high-end utility
- **Google Fonts:** [Inter + Inter](https://fonts.google.com/share?selection.family=Inter:wght@300;400;500;600;700)

**CSS Import:**
```css
@import url('https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&display=swap');
```

### Spacing Variables

| Token | Value | Usage |
|-------|-------|-------|
| `--space-xs` | `4px` / `0.25rem` | Tight gaps |
| `--space-sm` | `8px` / `0.5rem` | Icon gaps, inline spacing |
| `--space-md` | `16px` / `1rem` | Standard padding |
| `--space-lg` | `24px` / `1.5rem` | Section padding |
| `--space-xl` | `32px` / `2rem` | Large gaps |
| `--space-2xl` | `48px` / `3rem` | Section margins |
| `--space-3xl` | `64px` / `4rem` | Hero padding |

### Shadow Depths

| Level | Value | Usage |
|-------|-------|-------|
| `--shadow-sm` | `0 1px 2px rgba(0,0,0,0.05)` | Subtle lift |
| `--shadow-md` | `0 4px 6px rgba(0,0,0,0.1)` | Cards, buttons |
| `--shadow-lg` | `0 10px 15px rgba(0,0,0,0.1)` | Modals, dropdowns |
| `--shadow-xl` | `0 20px 25px rgba(0,0,0,0.15)` | Hero images, featured cards |

---

## Component Specs

### Buttons

```css
/* Primary Button */
.btn-primary {
  background: #2563EB;
  color: white;
  padding: 12px 24px;
  border-radius: 8px;
  font-weight: 600;
  transition: all 200ms ease;
  cursor: pointer;
}

.btn-primary:hover {
  opacity: 0.9;
  transform: translateY(-1px);
}

/* Secondary Button */
.btn-secondary {
  background: transparent;
  color: #EA580C;
  border: 2px solid #EA580C;
  padding: 12px 24px;
  border-radius: 8px;
  font-weight: 600;
  transition: all 200ms ease;
  cursor: pointer;
}
```

### Cards

```css
.card {
  background: #1C1917;
  border-radius: 12px;
  padding: 24px;
  box-shadow: var(--shadow-md);
  transition: all 200ms ease;
  cursor: pointer;
}

.card:hover {
  box-shadow: var(--shadow-lg);
  transform: translateY(-2px);
}
```

### Inputs

```css
.input {
  padding: 12px 16px;
  border: 1px solid #E2E8F0;
  border-radius: 8px;
  font-size: 16px;
  transition: border-color 200ms ease;
}

.input:focus {
  border-color: #EA580C;
  outline: none;
  box-shadow: 0 0 0 3px #EA580C20;
}
```

### Modals

```css
.modal-overlay {
  background: rgba(0, 0, 0, 0.5);
  backdrop-filter: blur(4px);
}

.modal {
  background: white;
  border-radius: 16px;
  padding: 32px;
  box-shadow: var(--shadow-xl);
  max-width: 500px;
  width: 90%;
}
```

---

## Style Guidelines

**Style:** Neumorphism (Mobile)

**Keywords:** neumorphism, soft ui, dual shadow, extruded, inset, clay surface, monochromatic, cool grey, haptic, ceramic, physical, depth

**Best For:** Minimal hardware controls, smart home apps, aesthetic utility tools, health monitors, brand showcase pages

**Key Effects:** Full-screen #E0E5EC base, dual-layer shadow via nested View (light top-left + dark bottom-right), extruded convex resting state, inset concave pressed/input state, Reanimated scale 0.97 on press, shadow opacity interpolates 1→0.4 on press, Haptics Light on every interaction, 8pt grid, no blur shadows (no shadowRadius blend), nested depth (extruded card contains inset icon slot)

### Page Pattern

**Pattern Name:** Minimal & Direct

- **CTA Placement:** Above fold
- **Section Order:** Hero > Features > CTA

---

## Anti-Patterns (Do NOT Use)

- ❌ Excessive decoration

### Additional Forbidden Patterns

- ❌ **Emojis as icons** — Use SVG icons (Heroicons, Lucide, Simple Icons)
- ❌ **Missing cursor:pointer** — All clickable elements must have cursor:pointer
- ❌ **Layout-shifting hovers** — Avoid scale transforms that shift layout
- ❌ **Low contrast text** — Maintain 4.5:1 minimum contrast ratio
- ❌ **Instant state changes** — Always use transitions (150-300ms)
- ❌ **Invisible focus states** — Focus states must be visible for a11y

---

## Pre-Delivery Checklist

Before delivering any UI code, verify:

- [ ] No emojis used as icons (use SVG instead)
- [ ] All icons from consistent icon set (Heroicons/Lucide)
- [ ] `cursor-pointer` on all clickable elements
- [ ] Hover states with smooth transitions (150-300ms)
- [ ] Light mode: text contrast 4.5:1 minimum
- [ ] Focus states visible for keyboard navigation
- [ ] `prefers-reduced-motion` respected
- [ ] Responsive: 375px, 768px, 1024px, 1440px
- [ ] No content hidden behind fixed navbars
- [ ] No horizontal scroll on mobile

---

# NeoCalc: as actually implemented

The generated system above is the reference. This section records where the
shipped code deviates from it, and why. Where the two disagree, this section is
what the code does.

## Stack

The generator assumes React Native. This app is **Jetpack Compose on Android
native**. Colour, type, spacing and the UX rules transfer unchanged; the
component guidance does not. Tokens live in
`app/src/main/java/com/omai/neocalc/ui/theme/`.

## Deviations, with reasons

| Reference | Shipped | Why |
| --- | --- | --- |
| Style: Dark Mode (OLED), **dark only** | Light **and** dark, following the system | The product decision is that the theme follows the OS with no in-app control. A dark-only app is wrong on a phone set to light. The dark ramp is the designed direction; the light ramp is derived from it, not inverted. |
| `On Primary: #FFFFFF` | `#1C1917` (dark ink) | White on `#EA580C` measures **3.56:1**. That passes for the 26sp `=` glyph as large text but fails for the 14sp labels that sit on the same token. Dark ink is **4.91:1** and passes at every size. |
| Light primary `#C2410C` | `#9A3412` | `#C2410C` is 4.92:1 on the background but **4.48:1** on the muted keypad fill, which is exactly where operators are drawn. `#9A3412` clears both (6.94 / 6.32). |
| Accent `#2563EB` | `#2563EB` light, `#60A5FA` dark | `#2563EB` on `#1C1917` is 2.6:1 and unreadable. |
| Typography: Inter (downloadable) | Platform sans + `fontFeatureSettings = "tnum"` | A downloadable font costs a Play Services dependency and a visible reflow on cold start, on a screen whose entire job is one number. Tabular figures solve the actual problem, which is digits changing width as a result updates. |
| Pattern: Minimal Single Column (hero → bullets → CTA → footer) | Not used | That is a landing-page pattern. This is a utility app with bottom-tab navigation. |

## Measured contrast (WCAG AA, 4.5:1)

Every foreground/background pair in the app was checked; all 14 pass. Lowest
margins, worth watching if a token is ever nudged:

- Operator on muted keypad, dark: **4.52:1**
- Danger on paper, light: **4.59:1**
- On-primary and operator on ground, dark: **4.91:1**

## Rules the code follows

- **Orange means operation.** `primary` is used for operators, the active tab
  and the focus ring, and nowhere else. Anything informational uses `tertiary`.
  If orange starts appearing on non-operations it stops carrying meaning.
- **One filled control per screen.** `=` on the calculator, `PLAY` in the
  arcade, `Get started` in onboarding.
- **Tabular figures on anything that changes.** Display, results, board rows,
  scores.
- **Touch targets ≥48dp with ≥8dp gaps.** The keypad computes key size from the
  window and floors it at 48dp.
- **Reduced motion is honoured.** `LocalReducedMotion` reads the system animator
  scale; animations collapse to zero rather than slowing down.
- **Spacing is 4/8dp**, scaled by window size through `WindowSize.gutter` and
  `WindowSize.spacing`.
