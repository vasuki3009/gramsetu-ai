import { useEffect, useRef } from 'react'

// Original illustration: a Tamil paddy field at sunrise. A farmer in a veshti and thundu
// (head towel) asks a question on a phone; a chat bubble floats up from it.
// Everything is inline SVG: no image files, no external assets.
// Only transform animations are used, and they pause while the scene is off-screen.
export default function HeroScene({ label, bubbleText, className = '' }) {
  const wrapRef = useRef(null)

  // Pause the ambient animations when the scene scrolls out of view (saves battery on phones).
  useEffect(() => {
    const el = wrapRef.current
    if (!el || typeof IntersectionObserver === 'undefined') return undefined
    const io = new IntersectionObserver(([e]) => {
      el.setAttribute('data-paused', e.isIntersecting ? 'false' : 'true')
    })
    io.observe(el)
    return () => io.disconnect()
  }, [])

  return (
    <div ref={wrapRef} data-paused="false" className={className}>
      <svg
        viewBox="0 0 640 420"
        role="img"
        aria-label={label}
        className="h-full w-full"
        preserveAspectRatio="xMidYMid slice"
      >
        <defs>
          <linearGradient id="hs-sky" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0" stopColor="#FDF3D2" />
            <stop offset="0.55" stopColor="#FBE2A0" />
            <stop offset="1" stopColor="#F7C85A" />
          </linearGradient>
          <radialGradient id="hs-sun" cx="0.5" cy="0.5" r="0.5">
            <stop offset="0" stopColor="#FFE9A8" />
            <stop offset="1" stopColor="#F5B82E" />
          </radialGradient>
          <linearGradient id="hs-hill-far" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0" stopColor="#B9D8A8" />
            <stop offset="1" stopColor="#9CC79B" />
          </linearGradient>
          <pattern id="hs-blades" width="14" height="12" patternUnits="userSpaceOnUse">
            <path d="M2 12 Q3 5 2 0 M7 12 Q8 4 9 0 M12 12 Q12 6 13 1" stroke="#17603A" strokeWidth="1.4" fill="none" strokeLinecap="round" />
          </pattern>
        </defs>

        {/* sky */}
        <rect width="640" height="420" fill="url(#hs-sky)" />

        {/* sun: glow, slowly turning rays, disc */}
        <circle cx="440" cy="128" r="118" fill="#FFF3C8" opacity="0.35" />
        <circle cx="440" cy="128" r="86" fill="#FFE9A8" opacity="0.45" />
        <g className="ambient motion-safe:animate-spin-slow" style={{ transformOrigin: '440px 128px' }}>
          {Array.from({ length: 16 }).map((_, i) => (
            <line
              key={i}
              x1="440"
              y1="48"
              x2="440"
              y2={i % 2 ? 58 : 40}
              stroke="#F5B82E"
              strokeWidth="4"
              strokeLinecap="round"
              transform={`rotate(${i * 22.5} 440 128)`}
            />
          ))}
        </g>
        <circle cx="440" cy="128" r="58" fill="url(#hs-sun)" />

        {/* egrets drifting across the sky */}
        <g className="ambient motion-safe:animate-drift" style={{ animationDelay: '-6s' }} fill="none" stroke="#FFFFFF" strokeWidth="2.6" strokeLinecap="round">
          <path d="M40 92 q7 -7 14 0 q7 -7 14 0" />
          <path d="M86 70 q6 -6 12 0 q6 -6 12 0" />
          <path d="M70 116 q5 -5 10 0 q5 -5 10 0" />
        </g>

        {/* far and middle hills */}
        <path d="M0 268 Q110 196 250 246 T500 226 T640 258 L640 320 L0 320Z" fill="url(#hs-hill-far)" />
        <path d="M0 290 Q140 232 300 274 T640 266 L640 330 L0 330Z" fill="#7DB88C" />

        {/* coconut palms (silhouettes) */}
        <g stroke="#14281D" strokeWidth="5" fill="none" strokeLinecap="round">
          <path d="M78 330 Q70 270 84 214" />
          <path d="M84 214 Q50 200 28 226 M84 214 Q56 188 34 192 M84 214 Q86 184 112 172 M84 214 Q114 196 140 212 M84 214 Q110 214 126 238" />
          <path d="M572 326 Q580 276 568 232" />
          <path d="M568 232 Q536 220 518 244 M568 232 Q544 208 522 212 M568 232 Q570 204 594 194 M568 232 Q598 216 620 232" />
        </g>

        {/* paddy terraces: three bands of rows, swaying gently */}
        <g className="ambient motion-safe:animate-sway" style={{ animationDelay: '-1s' }}>
          <path d="M-10 322 Q160 300 330 318 T650 308 L650 430 L-10 430Z" fill="#4A9A62" />
          <path d="M-10 322 Q160 300 330 318 T650 308 L650 430 L-10 430Z" fill="url(#hs-blades)" opacity="0.55" />
        </g>
        <g className="ambient motion-safe:animate-sway" style={{ animationDelay: '-3s' }}>
          <path d="M-10 356 Q190 332 390 352 T650 342 L650 430 L-10 430Z" fill="#37874F" />
          <path d="M-10 356 Q190 332 390 352 T650 342 L650 430 L-10 430Z" fill="url(#hs-blades)" opacity="0.6" />
        </g>
        <g className="ambient motion-safe:animate-sway" style={{ animationDelay: '-5s' }}>
          <path d="M-10 392 Q210 368 420 388 T650 378 L650 430 L-10 430Z" fill="#276F41" />
          <path d="M-10 392 Q210 368 420 388 T650 378 L650 430 L-10 430Z" fill="url(#hs-blades)" opacity="0.65" />
        </g>

        {/* farmer: veshti, shirt, thundu, phone */}
        <g transform="translate(306 352)">
          <ellipse cx="0" cy="2" rx="26" ry="5" fill="#14281D" opacity="0.18" />
          {/* veshti (cream) and feet */}
          <path d="M-14 -58 L14 -58 L18 -8 L-18 -8 Z" fill="#FBF5E6" />
          <path d="M-5 -58 L-3 -8 M6 -58 L5 -8" stroke="#E2D6B2" strokeWidth="1.6" />
          <ellipse cx="-8" cy="-4" rx="8" ry="4" fill="#14281D" />
          <ellipse cx="9" cy="-4" rx="8" ry="4" fill="#14281D" />
          {/* shirt */}
          <path d="M-15 -58 L-13 -98 Q0 -104 13 -98 L15 -58 Z" fill="#14281D" />
          {/* hanging arm */}
          <path d="M-13 -94 Q-20 -76 -15 -62" stroke="#14281D" strokeWidth="7" fill="none" strokeLinecap="round" />
          {/* raised arm and phone */}
          <path d="M12 -94 Q28 -92 36 -104" stroke="#14281D" strokeWidth="7" fill="none" strokeLinecap="round" />
          <g transform="rotate(-14 38 -112)">
            <rect x="30" y="-128" width="17" height="28" rx="4" fill="#FBF5E6" stroke="#14281D" strokeWidth="2.5" />
            <rect x="33" y="-124" width="11" height="17" rx="2" fill="#165C38" />
            <rect x="35" y="-121" width="7" height="4" rx="2" fill="#FBF5E6" />
          </g>
          {/* head and thundu (head towel) */}
          <circle cx="0" cy="-110" r="10" fill="#14281D" />
          <path d="M-11 -113 Q0 -130 11 -113 Q5 -109 0 -109 Q-6 -109 -11 -113Z" fill="#A8432A" />
          <path d="M8 -113 Q18 -108 15 -96 Q11 -103 6 -108Z" fill="#A8432A" />
        </g>

        {/* chat bubbles floating up from the phone */}
        <g className="ambient motion-safe:animate-float">
          <g transform="translate(344 190)">
            <rect x="0" y="0" width="128" height="46" rx="18" fill="#FBF5E6" />
            <path d="M18 46 L10 62 L34 46Z" fill="#FBF5E6" />
            <text x="64" y="30" textAnchor="middle" fontSize="19" fontWeight="700" fill="#0E3F27" fontFamily="'Noto Sans Tamil', sans-serif">
              {bubbleText}
            </text>
          </g>
        </g>
        <g className="ambient motion-safe:animate-float" style={{ animationDelay: '-2.4s' }}>
          <g transform="translate(394 246)">
            <rect x="0" y="0" width="64" height="34" rx="14" fill="#F5B82E" />
            <circle cx="20" cy="17" r="3.6" fill="#14281D" />
            <circle cx="32" cy="17" r="3.6" fill="#14281D" />
            <circle cx="44" cy="17" r="3.6" fill="#14281D" />
          </g>
        </g>

        {/* a few paddy flowers in the foreground */}
        <g fill="#FBF5E6" opacity="0.9">
          <circle cx="92" cy="404" r="2.6" /><circle cx="148" cy="410" r="2.2" /><circle cx="330" cy="408" r="2.6" />
          <circle cx="470" cy="406" r="2.2" /><circle cx="560" cy="410" r="2.6" />
        </g>
      </svg>
    </div>
  )
}
