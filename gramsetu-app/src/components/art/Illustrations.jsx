// Original duotone illustrations and icons (inline SVG, decorative: the text beside each one
// carries the meaning, so they are hidden from screen readers).
const deco = { 'aria-hidden': true, focusable: 'false' }

// ── How it works: three scenes, 120x120 ──────────────────────────────────────
export function StepSpeak(props) {
  return (
    <svg viewBox="0 0 120 120" {...deco} {...props}>
      <circle cx="60" cy="60" r="56" fill="#FCEFC9" />
      <circle cx="60" cy="60" r="40" fill="#FBE3A0" opacity="0.7" />
      <rect x="48" y="26" width="24" height="42" rx="12" fill="#165C38" />
      <rect x="54" y="32" width="5" height="18" rx="2.5" fill="#DCEBD9" opacity="0.6" />
      <path d="M36 60 a24 24 0 0 0 48 0" fill="none" stroke="#165C38" strokeWidth="5" strokeLinecap="round" />
      <path d="M60 84 V96 M48 96 H72" stroke="#165C38" strokeWidth="5" strokeLinecap="round" />
      <path d="M22 46 q-6 14 0 28 M14 40 q-10 20 0 40" fill="none" stroke="#A8432A" strokeWidth="4" strokeLinecap="round" />
      <path d="M98 46 q6 14 0 28 M106 40 q10 20 0 40" fill="none" stroke="#A8432A" strokeWidth="4" strokeLinecap="round" />
    </svg>
  )
}

export function StepUnderstand(props) {
  return (
    <svg viewBox="0 0 120 120" {...deco} {...props}>
      <circle cx="60" cy="60" r="56" fill="#DCEBD9" />
      <circle cx="60" cy="60" r="40" fill="#C6DFC2" opacity="0.8" />
      <path d="M24 34 a12 12 0 0 1 12 -12 h40 a12 12 0 0 1 12 12 v28 a12 12 0 0 1 -12 12 h-22 l-16 14 v-14 h-2 a12 12 0 0 1 -12 -12Z" fill="#FFFFFF" stroke="#165C38" strokeWidth="4" strokeLinejoin="round" />
      <text x="56" y="62" textAnchor="middle" fontSize="34" fontWeight="800" fill="#165C38" fontFamily="'Noto Sans Tamil', sans-serif">அ</text>
      <g fill="#A8432A">
        <circle cx="30" cy="94" r="6" />
        <circle cx="52" cy="102" r="6" />
        <circle cx="74" cy="102" r="6" />
        <circle cx="96" cy="94" r="6" />
      </g>
      <path d="M30 94 L52 102 L74 102 L96 94" fill="none" stroke="#A8432A" strokeWidth="2.5" strokeDasharray="3 5" strokeLinecap="round" />
      <circle cx="96" cy="94" r="11" fill="none" stroke="#F5B82E" strokeWidth="4" />
    </svg>
  )
}

export function StepAdvise(props) {
  return (
    <svg viewBox="0 0 120 120" {...deco} {...props}>
      <circle cx="60" cy="60" r="56" fill="#F6E3DC" />
      <circle cx="60" cy="60" r="40" fill="#F0CFC3" opacity="0.7" />
      <rect x="26" y="62" width="68" height="34" rx="10" fill="#FFFFFF" stroke="#A8432A" strokeWidth="4" />
      <path d="M38 76 H82 M38 86 H68" stroke="#A8432A" strokeWidth="4" strokeLinecap="round" opacity="0.55" />
      <path d="M60 62 V38" stroke="#165C38" strokeWidth="5" strokeLinecap="round" />
      <path d="M60 46 Q40 44 38 26 Q58 24 60 46Z" fill="#2F7D4F" />
      <path d="M60 40 Q80 40 84 22 Q62 20 60 40Z" fill="#165C38" />
      <path d="M92 40 q6 8 0 16 M100 34 q10 14 0 28" fill="none" stroke="#F5B82E" strokeWidth="4" strokeLinecap="round" />
    </svg>
  )
}

// ── Feature icons: duotone, 72x72 ────────────────────────────────────────────
export function FeatVoice(props) {
  return (
    <svg viewBox="0 0 72 72" {...deco} {...props}>
      <circle cx="36" cy="36" r="34" fill="currentColor" opacity="0.14" />
      <rect x="28" y="14" width="16" height="28" rx="8" fill="currentColor" />
      <path d="M20 36 a16 16 0 0 0 32 0" fill="none" stroke="currentColor" strokeWidth="4" strokeLinecap="round" />
      <path d="M36 52 V60 M28 60 H44" stroke="currentColor" strokeWidth="4" strokeLinecap="round" />
    </svg>
  )
}

export function FeatCrop(props) {
  return (
    <svg viewBox="0 0 72 72" {...deco} {...props}>
      <circle cx="36" cy="36" r="34" fill="currentColor" opacity="0.14" />
      <path d="M54 16 C30 16 18 28 18 44 c0 3 1 5 2 7 3 2 6 3 9 3 C46 54 54 40 54 16Z" fill="currentColor" />
      <path d="M20 52 C28 40 36 34 46 30" fill="none" stroke="#FFFFFF" strokeWidth="3.5" strokeLinecap="round" />
      <circle cx="40" cy="40" r="3.2" fill="#FFFFFF" opacity="0.85" />
      <circle cx="31" cy="30" r="2.4" fill="#FFFFFF" opacity="0.85" />
    </svg>
  )
}

export function FeatScheme(props) {
  return (
    <svg viewBox="0 0 72 72" {...deco} {...props}>
      <circle cx="36" cy="36" r="34" fill="currentColor" opacity="0.14" />
      <path d="M14 29 L36 15 L58 29Z" fill="currentColor" />
      <path d="M20 32 V48 M30 32 V48 M42 32 V48 M52 32 V48" stroke="currentColor" strokeWidth="5" strokeLinecap="round" />
      <rect x="12" y="52" width="48" height="6" rx="3" fill="currentColor" />
    </svg>
  )
}

export function FeatCalendar(props) {
  return (
    <svg viewBox="0 0 72 72" {...deco} {...props}>
      <circle cx="36" cy="36" r="34" fill="currentColor" opacity="0.14" />
      <rect x="14" y="18" width="44" height="40" rx="8" fill="currentColor" />
      <path d="M14 30 H58" stroke="#FFFFFF" strokeWidth="3.5" opacity="0.8" />
      <path d="M26 12 V22 M46 12 V22" stroke="currentColor" strokeWidth="5" strokeLinecap="round" />
      <g fill="#FFFFFF" opacity="0.9">
        <circle cx="26" cy="40" r="3" /><circle cx="36" cy="40" r="3" /><circle cx="46" cy="40" r="3" />
        <circle cx="26" cy="49" r="3" /><circle cx="36" cy="49" r="3" />
      </g>
    </svg>
  )
}

export function FeatDialect(props) {
  return (
    <svg viewBox="0 0 72 72" {...deco} {...props}>
      <circle cx="36" cy="36" r="34" fill="currentColor" opacity="0.14" />
      <path d="M14 22 a8 8 0 0 1 8 -8 h28 a8 8 0 0 1 8 8 v18 a8 8 0 0 1 -8 8 H36 l-12 10 v-10 h-2 a8 8 0 0 1 -8 -8Z" fill="currentColor" />
      <text x="36" y="40" textAnchor="middle" fontSize="24" fontWeight="800" fill="#FFFFFF" fontFamily="'Noto Sans Tamil', sans-serif">அ</text>
    </svg>
  )
}

// ── Why GramSetu: simple line icons, 40x40 ───────────────────────────────────
const why = { viewBox: '0 0 40 40', fill: 'none', stroke: 'currentColor', strokeWidth: 3, strokeLinecap: 'round', strokeLinejoin: 'round', ...deco }

export function WhyDialect(props) {
  return (
    <svg {...why} {...props}>
      <path d="M20 35 C11 26 8 21 8 16 a12 12 0 0 1 24 0 c0 5 -3 10 -12 19Z" />
      <circle cx="20" cy="16" r="4.5" />
    </svg>
  )
}

export function WhyChat(props) {
  return (
    <svg {...why} {...props}>
      <path d="M7 10 a4 4 0 0 1 4 -4 h18 a4 4 0 0 1 4 4 v13 a4 4 0 0 1 -4 4 H17 l-8 7 v-7 h-2 Z" />
      <path d="M14 15 H26 M14 21 H21" />
    </svg>
  )
}

export function WhyFree(props) {
  return (
    <svg {...why} {...props}>
      <circle cx="20" cy="20" r="13" />
      <path d="M13 21 l5 5 9 -11" />
    </svg>
  )
}

export function WhyPrivacy(props) {
  return (
    <svg {...why} {...props}>
      <path d="M20 5 L33 10 V20 c0 8 -6 13 -13 15 C13 33 7 28 7 20 V10Z" />
      <rect x="14" y="19" width="12" height="9" rx="2" />
      <path d="M16.5 19 v-3 a3.5 3.5 0 0 1 7 0 v3" />
    </svg>
  )
}

// A plain branch-and-commit mark for the GitHub link (not a trademarked logo).
export function BranchIcon(props) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" {...deco} {...props}>
      <circle cx="6" cy="5" r="2.5" /><circle cx="6" cy="19" r="2.5" /><circle cx="18" cy="9" r="2.5" />
      <path d="M6 7.5 V16.5 M18 11.5 c0 5 -6 4 -12 7" />
    </svg>
  )
}

// ── Kolam divider ────────────────────────────────────────────────────────────
// A row of pulli (dots) joined by looping lines, the way a kolam is drawn at a doorstep.
// It repeats across any width. `color` is a CSS colour.
export function KolamDivider({ color = '#F5B82E', className = '' }) {
  return (
    <svg
      width="100%"
      height="20"
      className={className}
      {...deco}
      preserveAspectRatio="none"
    >
      <defs>
        <pattern id={`kolam-${color.replace('#', '')}`} width="72" height="20" patternUnits="userSpaceOnUse">
          <g fill={color}>
            <circle cx="10" cy="10" r="2.4" /><circle cx="36" cy="10" r="2.4" /><circle cx="62" cy="10" r="2.4" />
          </g>
          <path
            d="M10 10 C18 0 28 0 36 10 C44 20 54 20 62 10 M10 10 C18 20 28 20 36 10 C44 0 54 0 62 10"
            fill="none"
            stroke={color}
            strokeWidth="1.8"
            strokeLinecap="round"
            opacity="0.85"
          />
        </pattern>
      </defs>
      <rect width="100%" height="20" fill={`url(#kolam-${color.replace('#', '')})`} />
    </svg>
  )
}
