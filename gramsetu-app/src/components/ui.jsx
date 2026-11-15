import { AlertIcon, ServerIcon, SpinnerIcon } from './Icons'
import { useLanguage } from '../context/LanguageContext'
import useInView from '../hooks/useInView'
import { STR } from '../strings'

// ── Button ───────────────────────────────────────────────────────────────────
// Every button is at least 48px tall. `as` lets it render a router <Link> or an <a>.
const BUTTON_BASE =
  'inline-flex items-center justify-center gap-2 rounded-control text-center font-bold leading-snug ' +
  'transition-[transform,background-color,box-shadow,filter] duration-150 ease-out motion-safe:active:scale-[0.97] ' +
  'disabled:cursor-not-allowed disabled:opacity-50'

const BUTTON_VARIANTS = {
  primary: 'bg-paddy text-white shadow-soft hover:bg-paddy-deep hover:shadow-lift',
  secondary: 'border-2 border-paddy bg-white text-paddy hover:bg-paddy-soft',
  accent: 'bg-turmeric text-ink shadow-soft hover:brightness-105 hover:shadow-lift',
  danger: 'bg-danger text-white hover:brightness-90',
  whatsapp: 'bg-whatsapp text-ink shadow-soft hover:brightness-105 hover:shadow-lift',
  onDark: 'border-2 border-white text-white hover:bg-white/10',
  ghost: 'text-paddy hover:bg-paddy-soft',
}

const BUTTON_SIZES = {
  md: 'min-h-[48px] px-5 py-2.5 text-body xl:min-h-[44px] xl:px-4 xl:py-1.5',
  lg: 'min-h-[60px] px-7 py-3 text-btnlg xl:min-h-[48px] xl:px-5 xl:py-2',
}

export function Button({ as: Tag = 'button', variant = 'primary', size = 'md', className = '', ...props }) {
  const extra = Tag === 'button' ? { type: props.type || 'button' } : {}
  return (
    <Tag
      {...extra}
      {...props}
      className={`${BUTTON_BASE} ${BUTTON_VARIANTS[variant]} ${BUTTON_SIZES[size]} ${className}`}
    />
  )
}

// Square icon-only button (needs an aria-label from the caller).
export function IconButton({ className = '', ...props }) {
  return (
    <button
      type="button"
      {...props}
      className={`inline-flex h-12 w-12 shrink-0 items-center justify-center rounded-full bg-paddy-soft text-paddy-deep ` +
        `transition-colors hover:bg-paddy hover:text-white disabled:opacity-50 ${className}`}
    />
  )
}

// ── Card ─────────────────────────────────────────────────────────────────────
// plain: resting card (soft shadow). soft/warm: tinted panels. dark: forest panel.
// glass: translucent; the blur is only applied from 1024px up, where devices can afford it.
const CARD_TONES = {
  plain: 'border border-line bg-white shadow-soft',
  soft: 'bg-paddy-soft',
  warm: 'bg-cream-deep',
  dark: 'bg-paddy-deep text-white',
  glass: 'border border-white/50 bg-white/75 shadow-soft lg:backdrop-blur-md',
}

export function Card({ as: Tag = 'div', tone = 'plain', className = '', ...props }) {
  return <Tag {...props} className={`rounded-card p-5 sm:p-6 ${CARD_TONES[tone]} ${className}`} />
}

// ── Chip ─────────────────────────────────────────────────────────────────────
export function Chip({ selected, className = '', ...props }) {
  return (
    <button
      type="button"
      aria-pressed={selected === undefined ? undefined : selected}
      {...props}
      className={`min-h-[48px] rounded-control border-2 px-4 py-2 text-left font-medium leading-snug transition-colors xl:min-h-[44px] ${
        selected
          ? 'border-paddy bg-paddy text-white'
          : 'border-edge bg-white text-ink hover:border-paddy hover:bg-paddy-soft'
      } ${className}`}
    />
  )
}

// ── Loading ──────────────────────────────────────────────────────────────────
export function Spinner({ className = 'h-7 w-7' }) {
  return <SpinnerIcon className={`${className} text-paddy motion-safe:animate-spin`} />
}

export function Skeleton({ className = '' }) {
  return <div aria-hidden="true" className={`skeleton rounded-card motion-safe:animate-shimmer ${className}`} />
}

// Three bouncing dots: "the AI is writing". `label` is read by screen readers.
export function TypingDots({ label, className = '' }) {
  return (
    <span role="status" className={`inline-flex items-center gap-1.5 ${className}`}>
      <span className="sr-only">{label}</span>
      {[0, 1, 2].map((i) => (
        <span
          key={i}
          aria-hidden="true"
          className="h-2.5 w-2.5 rounded-full bg-paddy motion-safe:animate-dots"
          style={{ animationDelay: `${i * 160}ms` }}
        />
      ))}
    </span>
  )
}

// ── Reveal on scroll ─────────────────────────────────────────────────────────
// Fades a section in once as it enters the viewport. With reduced motion it is simply visible.
export function Reveal({ as: Tag = 'div', delay = 0, className = '', children, ...rest }) {
  const [ref, inView] = useInView()
  return (
    <Tag
      ref={ref}
      style={delay ? { transitionDelay: `${delay}ms` } : undefined}
      className={`reveal ${inView ? 'is-visible' : ''} ${className}`}
      {...rest}
    >
      {children}
    </Tag>
  )
}

// ── Page header ──────────────────────────────────────────────────────────────
export function PageHeader({ title, subtitle, Icon, children }) {
  return (
    <header className="mb-6 flex flex-wrap items-start justify-between gap-4 lg:mb-8">
      <div className="flex min-w-0 items-start gap-4">
        {Icon && (
          <span className="mt-1 hidden h-14 w-14 shrink-0 items-center justify-center rounded-card bg-paddy text-white shadow-soft sm:flex">
            <Icon className="h-7 w-7" />
          </span>
        )}
        <div className="min-w-0">
          <h1 className="text-balance text-h1">{title}</h1>
          {subtitle && <p className="mt-1 max-w-2xl text-pretty text-lead text-muted">{subtitle}</p>}
        </div>
      </div>
      {children}
    </header>
  )
}

// ── Empty / error / info states ──────────────────────────────────────────────
const STATE_TONES = {
  neutral: { box: 'border-2 border-dashed border-edge bg-white text-ink', icon: 'bg-paddy-soft text-paddy' },
  error: { box: 'border-2 border-danger bg-danger-soft text-ink', icon: 'bg-danger text-white' },
  wake: { box: 'border-2 border-turmeric bg-turmeric-soft text-ink', icon: 'bg-turmeric text-ink' },
}

export function StatePanel({ Icon, title, tone = 'neutral', action, children, className = '' }) {
  const t = STATE_TONES[tone]
  return (
    <div
      role={tone === 'error' ? 'alert' : 'status'}
      className={`flex flex-col items-center gap-3 rounded-card p-6 text-center sm:p-8 ${t.box} ${className}`}
    >
      {Icon && (
        <span className={`flex h-14 w-14 items-center justify-center rounded-full ${t.icon}`}>
          <Icon className="h-7 w-7" />
        </span>
      )}
      {title && <p className="text-balance text-h3 font-bold">{title}</p>}
      {children && <div className="max-w-md text-pretty text-body text-muted">{children}</div>}
      {action}
    </div>
  )
}

export function ErrorState({ message, onRetry, retryLabel, className }) {
  const { language } = useLanguage()
  const s = STR[language] || STR.tamil
  return (
    <StatePanel Icon={AlertIcon} tone="error" className={className} action={
      onRetry && <Button variant="danger" onClick={onRetry}>{retryLabel || s.common.retry}</Button>
    }>
      {message}
    </StatePanel>
  )
}

// Shown instead of a plain spinner once a request has been waiting long enough that the free
// backend is probably still waking up (see hooks/useSlowLoad). The bar is indeterminate:
// we cannot know how long the server needs, so it only shows that something is happening.
export function WakeNotice({ className = '' }) {
  const { language } = useLanguage()
  const w = (STR[language] || STR.tamil).wake
  return (
    <StatePanel Icon={ServerIcon} tone="wake" title={w.title} className={className}>
      <span className="block">{w.body}</span>
      <span aria-hidden="true" className="mt-4 block h-2 w-full overflow-hidden rounded-full bg-turmeric/30">
        <span className="block h-full w-full motion-safe:animate-shimmer" style={{ background: 'linear-gradient(90deg, rgba(224,154,18,0.2) 0%, #E09A12 50%, rgba(224,154,18,0.2) 100%)', backgroundSize: '200% 100%' }} />
      </span>
    </StatePanel>
  )
}
