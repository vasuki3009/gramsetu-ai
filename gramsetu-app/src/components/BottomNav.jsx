import { NavLink } from 'react-router-dom'
import { useLanguage } from '../context/LanguageContext'
import { STR } from '../strings'
import { NAV } from '../nav'

// Phone and tablet navigation (below 1024px). Five items, each at least 64px tall; the desktop
// layout uses the sidebar in AppShell instead.
export default function BottomNav() {
  const { language } = useLanguage()
  const s = STR[language] || STR.tamil

  return (
    <nav
      aria-label={s.navLabel}
      className="fixed inset-x-0 bottom-0 z-40 border-t-2 border-line bg-white pb-[env(safe-area-inset-bottom)] lg:hidden"
    >
      <ul className="mx-auto grid max-w-3xl grid-cols-5">
        {NAV.map(({ key, path, Icon }) => (
          <li key={key}>
            <NavLink
              to={path}
              className={({ isActive }) =>
                `flex min-h-[64px] [@media(max-height:500px)]:min-h-[52px] flex-col items-center justify-center gap-0.5 px-1 py-1.5 text-center text-xs font-bold leading-tight transition-colors ` +
                `sm:text-[0.8125rem] md:flex-row md:gap-2 md:text-base ` +
                (isActive ? 'bg-paddy text-white' : 'text-ink hover:bg-paddy-soft')
              }
            >
              <Icon className="h-6 w-6 shrink-0" />
              <span>{s.nav[key]}</span>
            </NavLink>
          </li>
        ))}
      </ul>
    </nav>
  )
}
