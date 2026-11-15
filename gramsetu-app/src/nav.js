import { HomeIcon, MicIcon, LeafIcon, SchemeIcon, CalendarIcon, ChatIcon } from './components/Icons'

// The five main sections (phone bottom bar shows exactly these) ...
export const NAV = [
  { key: 'home', path: '/home', Icon: HomeIcon },
  { key: 'ask', path: '/voice', Icon: MicIcon },
  { key: 'crop', path: '/crop', Icon: LeafIcon },
  { key: 'schemes', path: '/schemes', Icon: SchemeIcon },
  { key: 'calendar', path: '/calendar', Icon: CalendarIcon },
]

// ... and extras that only the desktop sidebar lists.
export const NAV_EXTRA = [{ key: 'whatsapp', path: '/whatsapp', Icon: ChatIcon }]
