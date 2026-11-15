// Inline SVG icons (stroke style, 24x24 grid). Decorative by default: the text label beside
// each icon carries the meaning, so the SVG is hidden from screen readers.
const base = {
  viewBox: '0 0 24 24',
  fill: 'none',
  stroke: 'currentColor',
  strokeWidth: 2,
  strokeLinecap: 'round',
  strokeLinejoin: 'round',
  'aria-hidden': true,
  focusable: 'false',
}

export const MicIcon = (props) => (
  <svg {...base} {...props}>
    <rect x="9" y="2.5" width="6" height="12" rx="3" />
    <path d="M5 11.5a7 7 0 0 0 14 0" />
    <path d="M12 18.5V22" />
  </svg>
)

export const LeafIcon = (props) => (
  <svg {...base} {...props}>
    <path d="M20 4C10 4 4.5 9 4.5 15.5c0 1.4.4 2.6 1 3.5C7 19.6 8.2 20 9.5 20 16 20 20 14 20 4Z" />
    <path d="M4.5 19.5C8 14 11.5 11.5 15 10" />
  </svg>
)

export const SchemeIcon = (props) => (
  <svg {...base} {...props}>
    <path d="M3 9.5 12 4l9 5.5" />
    <path d="M5 10v8M9.5 10v8M14.5 10v8M19 10v8" />
    <path d="M3 21h18" />
  </svg>
)

export const CalendarIcon = (props) => (
  <svg {...base} {...props}>
    <rect x="3.5" y="5" width="17" height="15.5" rx="2.5" />
    <path d="M3.5 10h17M8 3v4M16 3v4" />
    <path d="M8 14.5h.01M12 14.5h.01M16 14.5h.01M8 17.5h.01M12 17.5h.01" />
  </svg>
)

export const ChevronIcon = (props) => (
  <svg {...base} strokeWidth={2.5} {...props}>
    <path d="m9 5 7 7-7 7" />
  </svg>
)

export const ChevronDownIcon = (props) => (
  <svg {...base} strokeWidth={2.5} {...props}>
    <path d="m5 9 7 7 7-7" />
  </svg>
)

export const InfoIcon = (props) => (
  <svg {...base} {...props}>
    <circle cx="12" cy="12" r="9" />
    <path d="M12 11v5.5M12 7.5h.01" />
  </svg>
)

export const ChatIcon = (props) => (
  <svg {...base} {...props}>
    <path d="M4 5.5A2.5 2.5 0 0 1 6.5 3h11A2.5 2.5 0 0 1 20 5.5v8a2.5 2.5 0 0 1-2.5 2.5H10l-4.5 4v-4H6.5A2.5 2.5 0 0 1 4 13.5Z" />
  </svg>
)

export const HomeIcon = (props) => (
  <svg {...base} {...props}>
    <path d="M4 11 12 4l8 7" />
    <path d="M6 9.5V20h12V9.5" />
    <path d="M10 20v-5h4v5" />
  </svg>
)

export const CameraIcon = (props) => (
  <svg {...base} {...props}>
    <path d="M4 8.5A2.5 2.5 0 0 1 6.5 6H8l1.2-2h5.6L16 6h1.5A2.5 2.5 0 0 1 20 8.5v8a2.5 2.5 0 0 1-2.5 2.5h-11A2.5 2.5 0 0 1 4 16.5Z" />
    <circle cx="12" cy="12.5" r="3.2" />
  </svg>
)

export const ImageIcon = (props) => (
  <svg {...base} {...props}>
    <rect x="3.5" y="4.5" width="17" height="15" rx="2.5" />
    <circle cx="9" cy="10" r="1.6" />
    <path d="m4 17 5-4.5 4 3.5 3-2.5 4 3.5" />
  </svg>
)

export const SendIcon = (props) => (
  <svg {...base} {...props}>
    <path d="M4 12 20 4l-5 16-3.5-6.5Z" />
    <path d="M11.5 13.5 20 4" />
  </svg>
)

export const SpeakerIcon = (props) => (
  <svg {...base} {...props}>
    <path d="M4 9.5h3.5L12 5.5v13L7.5 14.5H4Z" />
    <path d="M15.5 9a4 4 0 0 1 0 6M18 6.5a7.5 7.5 0 0 1 0 11" />
  </svg>
)

export const StopIcon = (props) => (
  <svg {...base} {...props}>
    <rect x="6.5" y="6.5" width="11" height="11" rx="2" />
  </svg>
)

export const SearchIcon = (props) => (
  <svg {...base} {...props}>
    <circle cx="10.5" cy="10.5" r="6.5" />
    <path d="m16 16 4.5 4.5" />
  </svg>
)

export const AlertIcon = (props) => (
  <svg {...base} {...props}>
    <path d="M12 4 3 19.5h18Z" />
    <path d="M12 10v4.5M12 17.5h.01" />
  </svg>
)

export const CheckIcon = (props) => (
  <svg {...base} strokeWidth={2.5} {...props}>
    <path d="m5 12.5 4.5 4.5L19 7.5" />
  </svg>
)

export const ServerIcon = (props) => (
  <svg {...base} {...props}>
    <rect x="4" y="4" width="16" height="6" rx="2" />
    <rect x="4" y="14" width="16" height="6" rx="2" />
    <path d="M8 7h.01M8 17h.01" />
  </svg>
)

export const BulbIcon = (props) => (
  <svg {...base} {...props}>
    <path d="M9 18h6M10 21h4" />
    <path d="M12 3a6 6 0 0 0-3.5 10.9c.6.5 1 1.2 1 2.1h5c0-.9.4-1.6 1-2.1A6 6 0 0 0 12 3Z" />
  </svg>
)

export const ExternalIcon = (props) => (
  <svg {...base} {...props}>
    <path d="M14 4h6v6M20 4l-9 9" />
    <path d="M18 14v4.5a1.5 1.5 0 0 1-1.5 1.5h-10A1.5 1.5 0 0 1 5 18.5v-10A1.5 1.5 0 0 1 6.5 7H11" />
  </svg>
)

export const GlobeIcon = (props) => (
  <svg {...base} {...props}>
    <circle cx="12" cy="12" r="8.5" />
    <path d="M3.5 12h17" />
    <path d="M12 3.5c2.5 2.5 3.5 5.5 3.5 8.5s-1 6-3.5 8.5c-2.5-2.5-3.5-5.5-3.5-8.5s1-6 3.5-8.5Z" />
  </svg>
)

export const SpinnerIcon = (props) => (
  <svg {...base} {...props}>
    <circle cx="12" cy="12" r="9" opacity="0.25" />
    <path d="M21 12a9 9 0 0 0-9-9" />
  </svg>
)
