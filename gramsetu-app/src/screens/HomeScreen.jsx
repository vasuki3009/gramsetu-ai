import { lazy, Suspense } from 'react'
import { useNavigate } from 'react-router-dom'
import HeroScene from '../components/art/HeroScene'
import { Button, Chip, Skeleton } from '../components/ui'
import { MicIcon } from '../components/Icons'
import useInView from '../hooks/useInView'
import { useLanguage } from '../context/LanguageContext'
import { STR } from '../strings'
import { LANDING } from '../landingStrings'

// Everything below the first screen lives in its own chunk, fetched only when the visitor
// scrolls near it. Phones on slow connections never pay for it unless they read that far.
const LandingSections = lazy(() => import('../components/landing/LandingSections'))

function LazyOnView({ children, minHeight = 640 }) {
  const [ref, inView] = useInView({ rootMargin: '600px 0px' })
  return (
    <div ref={ref} style={inView ? undefined : { minHeight }}>
      {inView ? (
        <Suspense fallback={<Skeleton className="h-[40rem]" />}>{children}</Suspense>
      ) : null}
    </div>
  )
}

export default function HomeScreen() {
  const navigate = useNavigate()
  const { language } = useLanguage()
  const s = STR[language] || STR.tamil
  const h = s.home
  const hero = (LANDING[language] || LANDING.tamil).hero

  return (
    <div className="space-y-10 lg:space-y-11">
      {/* Hero: the one thing this app is for, in a Tamil paddy-field scene */}
      <section
        aria-labelledby="hero-title"
        className="overflow-hidden rounded-panel bg-paddy-deep text-white shadow-lift xl:grid xl:grid-cols-[1.05fr_1fr]"
      >
        <div className="relative order-first xl:order-last">
          <HeroScene
            label={hero.alt}
            bubbleText={hero.bubble}
            className="aspect-[16/9] w-full sm:aspect-[16/7] xl:aspect-auto xl:h-full xl:min-h-[26rem]"
          />
          {/* soft blend from the scene into the green panel */}
          <div aria-hidden="true" className="pointer-events-none absolute inset-x-0 bottom-0 h-20 bg-gradient-to-t from-paddy-deep to-transparent xl:inset-y-0 xl:left-0 xl:right-auto xl:h-auto xl:w-48 xl:bg-gradient-to-r xl:from-paddy-deep xl:via-paddy-deep/70 xl:to-transparent" />
        </div>

        <div className="relative p-6 sm:p-10 xl:flex xl:flex-col xl:justify-center xl:p-9">
          <p className="text-body font-medium text-white/90 sm:text-lead">{h.greeting}</p>
          <h1 id="hero-title" className="mt-2 text-balance text-display font-extrabold lg:text-hero">
            {h.heroTitle}
          </h1>
          <p className="mt-3 max-w-xl text-pretty text-body text-white/90 sm:mt-4 sm:text-lead">{h.heroText}</p>

          <div className="mt-5 flex flex-wrap gap-3 sm:mt-7">
            <Button variant="accent" size="lg" className="w-full whitespace-nowrap sm:w-auto" onClick={() => navigate('/voice')}>
              <MicIcon className="h-7 w-7" />
              {h.ctaVoice}
            </Button>
            <Button variant="onDark" size="lg" className="w-full whitespace-nowrap sm:w-auto" onClick={() => navigate('/voice', { state: { focusText: true } })}>
              {h.ctaType}
            </Button>
          </div>
        </div>
      </section>

      {/* Example questions */}
      <section aria-labelledby="examples-title">
        <h2 id="examples-title" className="mb-3 text-h2">{h.examplesTitle}</h2>
        <ul className="flex flex-wrap gap-3">
          {h.examples.map((q) => (
            <li key={q}>
              <Chip onClick={() => navigate('/voice', { state: { q } })}>{q}</Chip>
            </li>
          ))}
        </ul>
      </section>

      <LazyOnView>
        <LandingSections />
      </LazyOnView>
    </div>
  )
}
