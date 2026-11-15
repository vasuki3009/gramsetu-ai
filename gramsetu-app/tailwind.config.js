/** @type {import('tailwindcss').Config} */
// GramSetu design tokens. The written rules live in DESIGN_SYSTEM.md.

// ── Fluid type ────────────────────────────────────────────────────────────────
// Phones keep their large Tamil sizes. Every token follows its phone ramp up to 640px, then eases
// to its desktop size, reached at 1280px and held beyond. All sizes are in px here and emitted as rem.
const REM = 16
function fluid({ min, base, vw, max, desk }) {
  const rem = (n) => `${+(n / REM).toFixed(4)}rem`
  const at640 = Math.min(max, Math.max(min, base + vw * 6.4)) // the phone ramp's value at 640px
  const phone = `clamp(${rem(min)}, ${rem(base)} + ${vw}vw, ${rem(max)})`
  if (desk >= at640) return `clamp(${rem(min)}, ${rem(base)} + ${vw}vw, ${rem(desk)})` // grows to its desktop size
  // shrinks: a straight line through (640px, at640) and (1280px, desk), never above the phone ramp, never below its desktop size
  const slope = (at640 - desk) / 640 // px per px of viewport width
  const line = `${rem(at640 + slope * 640)} - ${+(slope * 100).toFixed(4)}vw`
  return `min(${phone}, max(${rem(desk)}, calc(${line})))` // phone ramp is the ceiling; the line eases down; desk is the floor
}

export default {
  content: ["./index.html", "./src/**/*.{js,jsx}"],
  theme: {
    extend: {
      colors: {
        // Legacy tokens (kept so nothing that still references them breaks)
        gramGreen: { DEFAULT: '#1A6B45', dark: '#0A3D2B', light: '#E1F5EE' },
        gramAmber: '#D4790A',
        gramPurple: '#534AB7',

        // Text and surfaces. Every text/background pair used in the UI is >= 4.5:1.
        ink: '#14281D',        // body text (14.3:1 on cream)
        muted: '#465A4D',      // secondary text (6.8:1 on cream)
        cream: { DEFAULT: '#FBF5E6', deep: '#F3E8CC' }, // page and warm surfaces
        mist: '#EDF2E4',       // cool surface (kept for inputs' disabled state)
        paper: '#FAFCF6',
        line: '#E2D6B2',       // decorative outlines only (NOT for control boundaries)
        edge: '#6F8A76',       // control boundaries: 3.77:1 on white, 3.46:1 on cream (WCAG 1.4.11)

        // Brand colours
        paddy: { DEFAULT: '#165C38', deep: '#0E3F27', soft: '#DCEBD9', leaf: '#2F7D4F' }, // deep green
        turmeric: { DEFAULT: '#F5B82E', soft: '#FCEFC9', deep: '#E09A12', ink: '#8A5A00' }, // paddy gold; `ink` is the text-safe gold (5.45:1 on cream)
        soil: { DEFAULT: '#A8432A', soft: '#F6E3DC', clay: '#D9693F' }, // terracotta; `clay` decorative only
        indigoDye: { DEFAULT: '#2F3F8F', soft: '#E1E5F5' },
        danger: { DEFAULT: '#B42318', soft: '#FDE8E5' },
        whatsapp: '#25D366', // dark ink text only: white on this green fails contrast
      },
      fontFamily: {
        display: ['"Noto Sans Tamil"', 'Latha', 'Vijaya', 'sans-serif'],
        body: ['"Noto Sans Tamil"', 'Latha', 'Vijaya', 'sans-serif'],
      },
      // Fluid type scale (phone size -> desktop size). Tamil needs generous line-height for its vowel
      // signs: 1.6-1.65 for reading text, never below 1.3 for any heading.
      fontSize: {
        small: [fluid({ min: 15.2, base: 15.2, vw: 0, max: 15.2, desk: 12 }), { lineHeight: '1.6' }],
        body: [fluid({ min: 16.2, base: 15.2, vw: 0.24, max: 18, desk: 14 }), { lineHeight: '1.65' }],
        lead: [fluid({ min: 18, base: 16.7, vw: 0.38, max: 21, desk: 14.4 }), { lineHeight: '1.6' }],
        btnlg: [fluid({ min: 18, base: 16.7, vw: 0.38, max: 21, desk: 14 }), { lineHeight: '1.4' }],
        h3: [fluid({ min: 18, base: 16.7, vw: 0.38, max: 21, desk: 16 }), { lineHeight: '1.45' }],
        h2: [fluid({ min: 21, base: 17.5, vw: 0.86, max: 28.5, desk: 20 }), { lineHeight: '1.4' }],
        section: [fluid({ min: 26.5, base: 22.8, vw: 0.57, max: 28.5, desk: 23 }), { lineHeight: '1.35' }],
        h1: [fluid({ min: 26.6, base: 19.8, vw: 1.71, max: 41.8, desk: 27 }), { lineHeight: '1.35' }],
        display: [fluid({ min: 30.4, base: 18.2, vw: 3.23, max: 57, desk: 36 }), { lineHeight: '1.3' }],
        // hero heading from 1024px up: 32px at 1024, 36px from 1280 (the sidebar leaves the hero narrow at 1024)
        hero: ['clamp(2rem, 1rem + 1.5625vw, 2.25rem)', { lineHeight: '1.3' }],
      },
      maxWidth: { page: '1200px' },
      // Radii: control 14, card 20, panel 32, pill full
      borderRadius: { control: '0.875rem', card: '1.25rem', panel: '2rem' },
      // Elevation: soft (resting), lift (hover), glow (gold halo)
      boxShadow: {
        soft: '0 1px 2px rgba(14,63,39,0.06), 0 6px 20px rgba(14,63,39,0.07)',
        lift: '0 2px 4px rgba(14,63,39,0.08), 0 18px 40px rgba(14,63,39,0.16)',
        glow: '0 0 0 8px rgba(245,184,46,0.22), 0 0 0 20px rgba(245,184,46,0.1)',
      },
      transitionTimingFunction: { out: 'cubic-bezier(0.22, 1, 0.36, 1)' },
      keyframes: {
        rise: {
          '0%': { opacity: '0', transform: 'translateY(8px)' },
          '100%': { opacity: '1', transform: 'translateY(0)' },
        },
        ring: {
          '0%, 100%': { boxShadow: '0 0 0 0 rgba(180, 35, 24, 0.45)' },
          '50%': { boxShadow: '0 0 0 18px rgba(180, 35, 24, 0)' },
        },
        float: {
          '0%, 100%': { transform: 'translateY(0)' },
          '50%': { transform: 'translateY(-8px)' },
        },
        sway: {
          '0%, 100%': { transform: 'translateX(-3px)' },
          '50%': { transform: 'translateX(3px)' },
        },
        drift: {
          '0%': { transform: 'translateX(-30px)', opacity: '0' },
          '15%, 85%': { opacity: '1' },
          '100%': { transform: 'translateX(560px)', opacity: '0' },
        },
        spinSlow: { to: { transform: 'rotate(360deg)' } },
        dots: {
          '0%, 80%, 100%': { transform: 'translateY(0)', opacity: '0.45' },
          '40%': { transform: 'translateY(-5px)', opacity: '1' },
        },
        shimmer: {
          '0%': { backgroundPosition: '200% 0' },
          '100%': { backgroundPosition: '-200% 0' },
        },
        micRing: {
          '0%': { transform: 'scale(1)', opacity: '0.55' },
          '100%': { transform: 'scale(1.7)', opacity: '0' },
        },
      },
      animation: {
        rise: 'rise 220ms ease-out both',
        ring: 'ring 1.4s ease-in-out infinite',
        float: 'float 6s ease-in-out infinite',
        sway: 'sway 7s ease-in-out infinite',
        drift: 'drift 26s linear infinite',
        'spin-slow': 'spinSlow 80s linear infinite',
        dots: 'dots 1.2s ease-in-out infinite',
        shimmer: 'shimmer 1.6s linear infinite',
        'mic-ring': 'micRing 1.6s ease-out infinite',
      },
    },
  },
  plugins: [],
}
