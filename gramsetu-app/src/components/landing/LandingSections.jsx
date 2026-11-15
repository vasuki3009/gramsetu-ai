import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useLanguage } from '../../context/LanguageContext'
import useInView, { usePrefersReducedMotion } from '../../hooks/useInView'
import { LANDING, TECH_BADGES, GITHUB_URL } from '../../landingStrings'
import { Reveal, TypingDots } from '../ui'
import WhatsAppConnect from '../WhatsAppConnect'
import { ChevronIcon, SpeakerIcon } from '../Icons'
import {
  StepSpeak, StepUnderstand, StepAdvise,
  FeatVoice, FeatCrop, FeatScheme, FeatCalendar, FeatDialect,
  WhyDialect, WhyChat, WhyFree, WhyPrivacy, BranchIcon, KolamDivider,
} from '../art/Illustrations'

// ── 1. How it works ──────────────────────────────────────────────────────────
const STEP_ART = [StepSpeak, StepUnderstand, StepAdvise]

function HowItWorks({ t }) {
  return (
    <Reveal as="section" aria-labelledby="how-title">
      <h2 id="how-title" className="mb-8 text-center text-section">{t.title}</h2>
      <ol className="grid gap-8 xl:grid-cols-3 xl:gap-6">
        {t.steps.map((step, i) => {
          const Art = STEP_ART[i]
          return (
            <li key={step.title}>
              <Reveal delay={i * 90} className="relative flex h-full flex-col items-center rounded-card border border-line bg-white p-6 pt-10 text-center shadow-soft md:flex-row md:gap-6 md:pt-6 md:text-left xl:flex-col xl:pt-10 xl:text-center">
                {/* The steps are a real sequence, so they are numbered. */}
                <span className="absolute -top-5 left-1/2 flex h-10 w-10 -translate-x-1/2 items-center justify-center rounded-full bg-paddy text-lead font-extrabold text-white shadow-soft md:left-8 md:translate-x-0 xl:left-1/2 xl:-translate-x-1/2">
                  {i + 1}
                </span>
                <Art className="h-28 w-28 shrink-0" />
                <span className="block min-w-0">
                  <h3 className="mt-4 text-balance text-h3 font-extrabold md:mt-0 xl:mt-4">{step.title}</h3>
                  <p className="mt-2 text-pretty text-body text-muted">{step.text}</p>
                </span>
              </Reveal>
            </li>
          )
        })}
      </ol>
    </Reveal>
  )
}

// ── 2. WhatsApp chat mock ────────────────────────────────────────────────────
// A short, orchestrated moment: question, typing dots, answer. It plays once when the phone
// scrolls into view. With reduced motion everything is shown at once.
function ChatMock({ t }) {
  const [ref, inView] = useInView({ rootMargin: '0px 0px -20% 0px' })
  const reduced = usePrefersReducedMotion()
  const [step, setStep] = useState(0) // 0 nothing, 1 question, 2 typing, 3 answer

  useEffect(() => {
    if (reduced) { setStep(3); return undefined }
    if (!inView) return undefined
    const timers = [
      setTimeout(() => setStep(1), 300),
      setTimeout(() => setStep(2), 1200),
      setTimeout(() => setStep(3), 2800),
    ]
    return () => timers.forEach(clearTimeout)
  }, [inView, reduced])

  return (
    <div ref={ref} className="mx-auto w-full max-w-sm">
      <p className="mb-3 text-center">
        <span className="inline-flex min-h-[32px] items-center rounded-full bg-turmeric-soft px-4 text-small font-bold text-ink">
          {t.sample}
        </span>
      </p>
      <div className="rounded-[2.5rem] bg-paddy-deep p-3 shadow-lift">
        <div className="overflow-hidden rounded-[2rem] bg-cream-deep">
          <div className="flex items-center gap-3 bg-paddy px-4 py-3 text-white">
            <span aria-hidden="true" className="flex h-10 w-10 items-center justify-center rounded-full bg-turmeric text-lg font-extrabold text-ink">G</span>
            <span className="font-bold">{t.botName}</span>
          </div>

          <div className="min-h-[24rem] space-y-3 p-4" aria-live="polite">
            {step >= 1 && (
              <div className="flex justify-end motion-safe:animate-rise">
                <p className="max-w-[85%] rounded-card rounded-tr-sm bg-paddy-soft px-4 py-3 text-body">{t.question}</p>
              </div>
            )}
            {step === 2 && (
              <div className="flex justify-start motion-safe:animate-rise">
                <p className="rounded-card rounded-tl-sm bg-white px-4 py-3 shadow-soft">
                  <TypingDots label={t.typing} />
                </p>
              </div>
            )}
            {step >= 3 && (
              <div className="flex flex-col items-start gap-2 motion-safe:animate-rise">
                <p className="max-w-[92%] rounded-card rounded-tl-sm bg-white px-4 py-3 text-body shadow-soft">{t.answer}</p>
                <span aria-hidden="true" className="inline-flex items-center gap-2 rounded-full bg-white px-3 py-2 text-small font-bold text-paddy shadow-soft">
                  <SpeakerIcon className="h-5 w-5" />
                  <span className="flex items-end gap-0.5">
                    {[10, 16, 8, 18, 12, 6, 14].map((h, i) => (
                      <span key={i} className="w-1 rounded-full bg-paddy" style={{ height: h }} />
                    ))}
                  </span>
                  {t.voiceNote}
                </span>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}

function WhatsAppSection({ t }) {
  return (
    <Reveal as="section" aria-labelledby="chat-title" className="grid items-center gap-10 lg:grid-cols-2">
      <div className="space-y-5">
        <h2 id="chat-title" className="text-balance text-section">{t.title}</h2>
        <p className="max-w-lg text-pretty text-lead text-muted">{t.text}</p>
        <WhatsAppConnect />
      </div>
      <ChatMock t={t} />
    </Reveal>
  )
}

// ── 3. Feature cards ─────────────────────────────────────────────────────────
const FEATURES = [
  { key: 'voice', path: '/voice', Art: FeatVoice, text: 'text-paddy', bg: 'from-paddy-soft to-white', span: 'xl:col-span-2' },
  { key: 'crop', path: '/crop', Art: FeatCrop, text: 'text-soil', bg: 'from-soil-soft to-white', span: 'xl:col-span-2' },
  { key: 'schemes', path: '/schemes', Art: FeatScheme, text: 'text-turmeric-ink', bg: 'from-turmeric-soft to-white', span: 'xl:col-span-2' },
  { key: 'calendar', path: '/calendar', Art: FeatCalendar, text: 'text-indigoDye', bg: 'from-indigoDye-soft to-white', span: 'xl:col-span-3' },
  { key: 'dialect', path: '/voice', Art: FeatDialect, text: 'text-paddy-leaf', bg: 'from-paddy-soft to-cream-deep', span: 'sm:col-span-2 xl:col-span-3' },
]

function Features({ t }) {
  return (
    <Reveal as="section" aria-labelledby="features-title">
      <h2 id="features-title" className="mb-8 text-center text-section">{t.title}</h2>
      <ul className="grid gap-5 sm:grid-cols-2 xl:grid-cols-6 xl:gap-4">
        {FEATURES.map(({ key, path, Art, text, bg, span }, i) => (
          <li key={key} className={span}>
            <Reveal delay={i * 70} className="h-full">
              <Link
                to={path}
                className={`group relative flex h-full min-h-[11rem] flex-col gap-3 overflow-hidden rounded-card border border-line bg-gradient-to-br ${bg} p-6 shadow-soft transition duration-200 ease-out motion-safe:hover:-translate-y-1 hover:shadow-lift`}
              >
                {/* gradient accent blob in the corner */}
                <span aria-hidden="true" className={`absolute -right-10 -top-10 h-36 w-36 rounded-full bg-current opacity-[0.09] transition-transform duration-300 motion-safe:group-hover:scale-125 ${text}`} />
                <Art className={`relative h-16 w-16 ${text}`} />
                <span className="relative">
                  <span className="block text-h3 font-bold">{t.items[key].title}</span>
                  <span className="mt-1 block text-pretty text-body text-muted">{t.items[key].text}</span>
                </span>
                <ChevronIcon className="relative mt-auto h-6 w-6 self-end text-ink transition-transform motion-safe:group-hover:translate-x-1" />
              </Link>
            </Reveal>
          </li>
        ))}
      </ul>
    </Reveal>
  )
}

// ── 4. Why GramSetu strip ────────────────────────────────────────────────────
const WHY_ICONS = [WhyDialect, WhyChat, WhyFree, WhyPrivacy]

function Why({ t }) {
  return (
    <Reveal as="section" aria-labelledby="why-title" className="rounded-panel bg-gradient-to-br from-paddy-deep to-paddy p-7 text-white shadow-lift sm:p-10 xl:p-8">
      <h2 id="why-title" className="mb-7 text-center text-section">{t.title}</h2>
      <ul className="grid gap-x-8 gap-y-7 sm:grid-cols-2">
        {t.items.map((item, i) => {
          const Icon = WHY_ICONS[i]
          return (
            <li key={item.title} className="flex gap-4">
              <span className="flex h-14 w-14 shrink-0 items-center justify-center rounded-full bg-white/10 text-turmeric">
                <Icon className="h-8 w-8" />
              </span>
              <span>
                <span className="block text-h3 font-bold">{item.title}</span>
                <span className="mt-1 block text-pretty text-body text-white/90">{item.text}</span>
              </span>
            </li>
          )
        })}
      </ul>
    </Reveal>
  )
}

// ── 5. Footer ────────────────────────────────────────────────────────────────
function SiteFooter({ t }) {
  return (
    <footer className="overflow-hidden rounded-panel bg-paddy-deep text-white">
      <KolamDivider color="#F5B82E" className="block" />
      <div className="grid gap-8 p-7 sm:p-10 xl:p-8 md:grid-cols-2 xl:grid-cols-[1.2fr_1fr_1.05fr]">
        <div>
          <p className="text-h3 font-extrabold">GramSetu</p>
          <p className="text-lead text-turmeric">{t.lead}</p>
          <p className="mt-3 max-w-sm text-pretty text-body text-white/90">{t.about}</p>
        </div>

        <div>
          <h2 className="mb-3 text-h3">{t.stackTitle}</h2>
          <ul className="flex flex-wrap gap-2">
            {TECH_BADGES.map((b) => (
              <li key={b} className="rounded-full border border-white/40 px-3 py-1 text-small">{b}</li>
            ))}
          </ul>
        </div>

        <div className="space-y-3">
          <a
            href={GITHUB_URL}
            target="_blank"
            rel="noopener noreferrer"
            className="inline-flex min-h-[48px] items-center gap-2 rounded-control border-2 border-white px-4 py-2 font-bold transition-colors hover:bg-white/10"
          >
            <BranchIcon className="h-6 w-6" />
            {t.githubLabel}
          </a>
          <p className="text-pretty text-small text-white/85">{t.disclaimer}</p>
        </div>
      </div>
      <p className="border-t border-white/15 px-7 py-4 text-small text-white/85 sm:px-10">{t.built}</p>
    </footer>
  )
}

export default function LandingSections() {
  const { language } = useLanguage()
  const t = (LANDING[language] || LANDING.tamil)

  return (
    <div className="space-y-16 lg:space-y-14">
      <HowItWorks t={t.how} />
      <WhatsAppSection t={t.chat} />
      <Features t={t.features} />
      <Why t={t.why} />
      <SiteFooter t={t.footer} />
    </div>
  )
}
