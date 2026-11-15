import { Link, NavLink, useLocation } from 'react-router-dom'
import { useLanguage } from '../context/LanguageContext'
import { STR } from '../strings'
import { NAV, NAV_EXTRA } from '../nav'
import { GlobeIcon, LeafIcon } from './Icons'
import BottomNav from './BottomNav'

function LanguageToggle({ className = '' }) {
  const { language, setLanguage } = useLanguage()
  const s = STR[language] || STR.tamil
  return (
    <button
      type="button"
      onClick={() => setLanguage(language === 'english' ? 'tamil' : 'english')}
      aria-label={s.langLabel}
      lang={language === 'english' ? 'ta' : 'en'}
      className={`inline-flex min-h-[48px] items-center justify-center gap-2 rounded-control border-2 border-white/70 px-4 font-bold text-white transition-colors hover:bg-white/10 ${className}`}
    >
      <GlobeIcon className="h-5 w-5" />
      <span>{s.langBtn}</span>
    </button>
  )
}

function Brand({ s }) {
  return (
    <Link to="/home" className="flex min-h-[48px] items-center gap-3 rounded-control text-white">
      <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-control bg-turmeric text-ink">
        <LeafIcon className="h-6 w-6" />
      </span>
      <span className="min-w-0">
        <span className="block text-h3 font-extrabold leading-tight">GramSetu</span>
        <span className="block text-sm leading-snug text-white/85">{s.tagline}</span>
      </span>
    </Link>
  )
}

// Desktop and tablet-landscape navigation (1024px and up).
function Sidebar({ s }) {
  return (
    <aside className="hidden bg-paddy-deep text-white lg:sticky lg:top-0 lg:flex lg:h-[100dvh] lg:flex-col lg:gap-5 lg:p-4">
      <Brand s={s} />
      <nav aria-label={s.navLabel} className="flex-1">
        <ul className="space-y-2">
          {[...NAV, ...NAV_EXTRA].map(({ key, path, Icon }) => (
            <li key={key}>
              <NavLink
                to={path}
                className={({ isActive }) =>
                  `flex min-h-[52px] items-center gap-3 rounded-control px-4 text-body font-bold transition-colors ` +
                  (isActive ? 'bg-white text-paddy-deep' : 'text-white hover:bg-white/10')
                }
              >
                <Icon className="h-6 w-6 shrink-0" />
                <span>{s.nav[key]}</span>
              </NavLink>
            </li>
          ))}
        </ul>
      </nav>
      <div className="space-y-3">
        <LanguageToggle className="w-full" />
        <p className="text-sm leading-snug text-white/80">{s.region}</p>
      </div>
    </aside>
  )
}

// Phone and tablet header (below 1024px).
function TopBar({ s }) {
  return (
    <header className="sticky top-0 z-30 flex [@media(max-height:500px)]:static items-center justify-between gap-3 bg-paddy-deep px-4 py-2 sm:px-6 lg:hidden">
      <Brand s={s} />
      <LanguageToggle />
    </header>
  )
}

export default function AppShell({ children }) {
  const { language } = useLanguage()
  const s = STR[language] || STR.tamil
  const { pathname } = useLocation()

  return (
    <div className="min-h-[100dvh] bg-cream text-ink lg:grid lg:grid-cols-[15rem_minmax(0,1fr)] xl:grid-cols-[14rem_minmax(0,1fr)]">
      <a
        href="#main"
        className="sr-only focus:not-sr-only focus:fixed focus:left-3 focus:top-3 focus:z-[100] focus:rounded-xl focus:bg-white focus:px-4 focus:py-3 focus:font-bold"
      >
        {s.skip}
      </a>
      <Sidebar s={s} />
      <div className="flex min-h-[100dvh] min-w-0 flex-col">
        <TopBar s={s} />
        <main
          id="main"
          tabIndex={-1}
          className="mx-auto w-full max-w-page flex-1 px-4 pb-28 pt-5 sm:px-6 sm:pt-8 lg:px-7 lg:pb-10 lg:pt-8 xl:px-8"
        >
          {/* Keyed by route so each page fades in once; reduced-motion users get no animation. */}
          <div key={pathname} className="motion-safe:animate-rise">
            {children}
          </div>
        </main>
      </div>
      <BottomNav />
    </div>
  )
}
