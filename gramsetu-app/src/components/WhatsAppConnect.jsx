import { Link } from 'react-router-dom'
import { QRCodeSVG } from 'qrcode.react'
import { ChatIcon, ChevronDownIcon, ChevronIcon } from './Icons'
import { Button, Card, StatePanel } from './ui'
import { useLanguage } from '../context/LanguageContext'
import { STR } from '../strings'

// Full WhatsApp join link, e.g. https://wa.me/<number>?text=join%20<sandbox-code>
// Set VITE_WHATSAPP_JOIN_URL at build time. When it is missing the join button and QR are hidden.
const WA_URL = import.meta.env.VITE_WHATSAPP_JOIN_URL

function Steps({ steps }) {
  return (
    <ol className="space-y-3">
      {steps.map((step, i) => (
        <li key={step} className="flex items-start gap-3 text-body">
          <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-paddy text-base font-bold text-white">
            {i + 1}
          </span>
          <span className="pt-0.5">{step}</span>
        </li>
      ))}
    </ol>
  )
}

function JoinButton({ label }) {
  return (
    <Button as="a" variant="whatsapp" size="lg" href={WA_URL} target="_blank" rel="noopener noreferrer" className="w-full sm:w-auto">
      <ChatIcon className="h-6 w-6" />
      {label}
    </Button>
  )
}

function Qr({ caption, size }) {
  return (
    <div className="hidden flex-col items-center gap-2 md:flex">
      <p className="text-center text-small text-muted">{caption}</p>
      <div className="rounded-control border-2 border-line bg-white p-3">
        <QRCodeSVG value={WA_URL} size={size} bgColor="#ffffff" fgColor="#14281D" />
      </div>
    </div>
  )
}

// variant "card": collapsed by default so the main actions stay above the fold (home screen).
// variant "page": always open, with the QR code, on its own screen.
export default function WhatsAppConnect({ variant = 'card' }) {
  const { language } = useLanguage()
  const w = (STR[language] || STR.tamil).whatsapp

  if (variant === 'page') {
    return (
      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_minmax(0,18rem)] lg:items-start xl:grid-cols-[minmax(0,1fr)_minmax(0,22rem)]">
        <Card>
          <h2 className="mb-4 text-h2">{w.stepsTitle}</h2>
          <Steps steps={w.steps} />
          <p className="mt-5 text-body text-muted">{w.dialects}</p>
          {WA_URL ? (
            <div className="mt-6">
              <JoinButton label={w.join} />
            </div>
          ) : (
            <StatePanel className="mt-6" tone="wake">{w.noLink}</StatePanel>
          )}
        </Card>

        <div className="space-y-6">
          {WA_URL && (
            <Card className="flex justify-center">
              <Qr caption={w.qr} size={176} />
            </Card>
          )}
          <Card tone="soft">
            <h2 className="mb-2 text-h3">{w.sandboxTitle}</h2>
            <p className="text-body">{w.sandbox}</p>
          </Card>
        </div>
      </div>
    )
  }

  return (
    <details className="group rounded-card border-2 border-edge bg-white">
      <summary className="flex min-h-[76px] cursor-pointer list-none items-center gap-4 rounded-card px-5 py-3 [&::-webkit-details-marker]:hidden">
        <span className="flex h-12 w-12 shrink-0 items-center justify-center rounded-full bg-whatsapp text-ink">
          <ChatIcon className="h-6 w-6" />
        </span>
        <span className="min-w-0 flex-1">
          <span className="block text-balance text-h3 font-bold">{w.title}</span>
          <span className="block text-small text-muted">{w.hint}</span>
        </span>
        <ChevronDownIcon className="h-6 w-6 shrink-0 transition-transform group-open:rotate-180 motion-reduce:transition-none" />
      </summary>

      <div className="px-5 pb-5">
        <Steps steps={w.steps} />
        <p className="mt-4 text-small text-muted">{w.dialects}</p>

        {WA_URL && (
          <div className="mt-5">
            <JoinButton label={w.join} />
          </div>
        )}

        <Link
          to="/whatsapp"
          className="mt-4 inline-flex min-h-[48px] items-center gap-1 rounded-xl font-bold text-paddy underline-offset-4 hover:underline"
        >
          {w.guide}
          <ChevronIcon className="h-5 w-5" />
        </Link>
      </div>
    </details>
  )
}
