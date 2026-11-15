import { MicIcon, StopIcon, SpinnerIcon } from './Icons'
import { useLanguage } from '../context/LanguageContext'

const LABELS = {
  tamil: { idle: 'அழுத்திப் பேசுங்கள்', listening: 'நிறுத்த அழுத்துங்கள்', loading: 'பதில் தயாரிக்கிறோம்...' },
  english: { idle: 'Press and speak', listening: 'Press to stop', loading: 'Preparing your answer...' },
}

// The one big action on the Ask screen. State is shown by colour, icon AND text, never colour alone.
export default function BigMicButton({ isListening, isLoading, onClick }) {
  const { language } = useLanguage()
  const l = LABELS[language] || LABELS.tamil

  let tone = 'bg-paddy hover:bg-paddy-deep'
  let Icon = MicIcon
  let label = l.idle
  if (isLoading) {
    tone = 'bg-muted'
    Icon = SpinnerIcon
    label = l.loading
  } else if (isListening) {
    tone = 'bg-danger motion-safe:animate-ring'
    Icon = StopIcon
    label = l.listening
  }

  return (
    <div className="flex flex-col items-center gap-3">
      <span className="relative inline-flex">
        {/* Pulse rings while recording: they show that the microphone is live */}
        {isListening && (
          <>
            <span aria-hidden="true" className="absolute inset-0 rounded-full bg-danger/40 motion-safe:animate-mic-ring" />
            <span aria-hidden="true" className="absolute inset-0 rounded-full bg-danger/30 motion-safe:animate-mic-ring" style={{ animationDelay: '0.8s' }} />
          </>
        )}
      <button
        type="button"
        onClick={onClick}
        disabled={isLoading}
        aria-label={label}
        className={`relative flex h-32 w-32 items-center justify-center rounded-full text-white shadow-lift transition-colors duration-200 ` +
          `motion-safe:active:scale-95 disabled:cursor-wait sm:h-36 sm:w-36 ${tone}`}
      >
        <Icon className={`h-14 w-14 ${isLoading ? 'motion-safe:animate-spin' : ''}`} />
      </button>
      </span>
      <span className="text-center text-body font-bold text-ink" aria-live="polite">
        {label}
      </span>
    </div>
  )
}
