# GramSetu design system

Rules for the web app in `gramsetu-app/`. Tokens live in `tailwind.config.js` and `src/index.css`;
shared components in `src/components/ui.jsx`; the illustration kit in `src/components/art/`.

**Audience:** farmers, often on low-end phones, with low reading confidence, who speak regional Tamil.
**Feel:** warm and trustworthy, like a village neighbour who knows farming. Never corporate.

## 1. Voice and copy
Tamil first, English second. Same words for the same things everywhere.

| Trait | Means | Do | Don't |
|---|---|---|---|
| Neighbourly | Warm, plain, respectful | "பேசி கேளுங்கள்" | Slang or over-familiar forms |
| Practical | Say what to do next | "Check your internet and try again." | "An error occurred." |
| Honest | Admit limits | "Confirm with your local agriculture office." | Promise certainty |
| Brief | Fewest words that work | Verb-first buttons ("Find schemes") | Noun buttons ("Submission") |

- Errors say what happened and how to fix it. Raw technical messages never reach the screen.
- Empty states say what the space is for and how to fill it.
- Sentence case in English. One name per screen: the nav name is the page title ("Ask").
- AI answers are guidance. Advice samples carry no doses and end by pointing to the local agriculture office.

## 2. Colour
Deep green, paddy gold, terracotta, cream. Every text pair below is WCAG AA.

| Token | Hex | Use | Verified |
|---|---|---|---|
| `ink` | `#14281D` | body text | 14.3:1 on cream |
| `muted` | `#465A4D` | secondary text, placeholders | 6.8:1 on cream |
| `cream` / `cream-deep` | `#FBF5E6` / `#F3E8CC` | page and warm surfaces | |
| `paddy` | `#165C38` | primary buttons | white on it 8.0:1 |
| `paddy-deep` | `#0E3F27` | hero, sidebar, footer | cream on it 11.0:1 |
| `paddy-soft` / `paddy-leaf` | `#DCEBD9` / `#2F7D4F` | tints, decorative | |
| `turmeric` (gold) | `#F5B82E` | sun, CTA fill, accents | ink on it 8.7:1 |
| `turmeric-ink` | `#8A5A00` | gold as **text** | 5.45:1 on cream |
| `soil` (terracotta) | `#A8432A` | crop tile, accents | white on it 6.0:1 |
| `edge` | `#6F8A76` | **control borders** | 3.77:1 on white (WCAG 1.4.11) |
| `line` | `#E2D6B2` | decorative outlines only | not for controls |
| `danger` | `#B42318` | errors | on `danger-soft` 5.6:1 |

Rules: gold is a fill, never text on cream (use `turmeric-ink`). Control boundaries use `edge`, never `line`.
Meaning is never carried by colour alone: every state also has an icon or text.

## 3. Type
Noto Sans Tamil only (it covers Tamil and Latin). The scale is **fluid**: phones keep the large sizes farmers need;
from 640px each token eases down to its desktop size, reached at 1280px and held beyond. Tokens are built by the
`fluid()` helper in `tailwind.config.js` (clamp-based, so there are no breakpoint jumps and phone sizes are unchanged).
Tamil line-height: 1.6-1.65 for reading text, never below 1.3 for any heading.

| Token | Phone (360-640) | Desktop (>=1280) | Line-height | Use |
|---|---|---|---|---|
| `text-small` | 16px | 13px | 1.6 | captions, hints (16px minimum on phones) |
| `text-body` | 17-18px | 15.5px | 1.65 | body, buttons (md) |
| `text-lead` | 19-20px | 16px | 1.6 | intro text, hero sub-text |
| `text-btnlg` | 19-20px | 15.5px | 1.4 | large buttons |
| `text-h3` | 19-20px | 18px | 1.45 | card titles, sidebar logo text |
| `text-h2` | 22-24px | 22.5px | 1.4 | panel headings |
| `text-section` | 28px | 26px | 1.35 | landing section headings |
| `text-h1` | 28-32px | 30px | 1.35 | page titles |
| `text-display` | 32-41px | 40px | 1.3 | hero below 1024px |
| `text-hero` | n/a | 36px at 1024, 40px from 1280 | 1.3 | hero heading from 1024px (`lg:text-hero`) |

Desktop sizes are medium on purpose (about 12% below the first fluid pass). Tablets (640-1024px) ease between the two columns.
Desktop spacing is tighter to match: sidebar 14rem (224px) from 1280px, main padding 32px, hero panel padding 36px, section gap 56px.
Buttons from 1280px: md 44px tall, lg 48px tall, with smaller padding; tap targets never go below 44px.

Do not hand-write `text-[..px]` on screens; add or reuse a token. Verified by script at 390/640/768/1024/1280/1440/1920.

## 4. Space, radii, elevation, motion
- **Space:** 4px base. Section rhythm 64px (phone), 80px (desktop). Cards pad 20-24px.
- **Radii:** `rounded-control` 14px (buttons, inputs, chips), `rounded-card` 20px, `rounded-panel` 32px (hero, strips, footer), `rounded-full` (pills, icon buttons).
- **Elevation:** `shadow-soft` (resting card), `shadow-lift` (hover, hero, phone frame), `shadow-glow` (gold halo).
- **Motion:** press 150ms, hover 200ms, reveal 480ms (once), ambient 6-80s loops. Ease `out`.
  All motion respects `prefers-reduced-motion`: loops stop and revealed content is simply visible.
  Ambient SVG animation pauses when off-screen.
- **Glass:** `Card tone="glass"` blurs only from 1024px up; phones get a plain translucent fill.

## 5. Components (`ui.jsx`)
| Component | Variants | States and rules |
|---|---|---|
| `Button` | primary, secondary, accent, whatsapp, onDark, danger, ghost; size md/lg | min 48px tall on phones, 44px (md) / 48px (lg) with tighter padding from 1280px; hover, press (scale .97), focus ring, disabled (50%). Renders as `<a>` or `<Link>` via `as`. |
| `IconButton` | one | 48px circle; needs `aria-label`. |
| `Chip` | selected / unselected | 48px; `aria-pressed` **only** when it is a real toggle (pass `selected`). Navigating chips pass nothing. |
| `Card` | plain, soft, warm, dark, glass | `plain` = white, 1px `line`, `shadow-soft`. |
| `StatePanel` / `ErrorState` / `WakeNotice` | neutral, error, wake | `role=status` (error: `alert`); always icon + text; error has a retry button. |
| `Skeleton` | - | shimmer; decorative (`aria-hidden`). |
| `TypingDots` | - | three bouncing dots; the label is read by screen readers. |
| `Reveal` | delay | one fade-and-rise when scrolled into view; visible immediately with reduced motion. |
| `PageHeader` | - | the single `h1` of each page. |

## 6. Illustration and motif
Original inline SVG only: no photos, no stock assets, no external images.
- `HeroScene`: paddy field at sunrise, farmer in veshti and thundu (generic silhouette), floating chat bubbles.
- `Illustrations`: step scenes, duotone feature icons, line icons, the **kolam divider** (the recurring Tamil motif).
- Decorative SVG is `aria-hidden`; the hero scene has a text alternative.

## 7. Patterns
- **Shell:** under 1024px a top bar plus bottom nav (five items, 64px); from 1024px a sidebar (15rem = 240px, 14rem = 224px from 1280px). Content max width 1200px, centred on wide screens.
- **Home order:** hero, example questions, How it works, WhatsApp chat sample, features, Why GramSetu, footer. Everything after the example questions is a lazy chunk, mounted when it nears the viewport.
- **Waiting:** typing dots plus a skeleton; after 8 seconds the "waking up the server" banner appears. A silent warm-up request is sent on app load.

## 8. Accessibility checklist (all verified by script on every screen)
Text contrast at least AA; control boundaries at least 3:1; every Tab stop shows the focus ring; skip link first; one `h1`, no skipped heading levels;
every control has a name; targets at least 44px (buttons 48px); no horizontal scroll at 320px or 200% zoom; reduced motion honoured; tabs follow the ARIA keyboard pattern.

## 9. Performance rules
No raster images. No new runtime dependencies. Below-the-fold sections are code-split. Ambient animation uses transforms only.
Budget: keep the initial JS close to the first release (see the pull request notes) and the lazy chunk under 30 kB.
