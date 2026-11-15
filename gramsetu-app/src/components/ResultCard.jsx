import DialectBadge from './DialectBadge'
import { IconButton } from './ui'
import { SpeakerIcon } from './Icons'
import { useLanguage } from '../context/LanguageContext'

// Splits "some **bold** text" into mixed text/strong nodes
function formatInline(text) {
  return text.split(/(\*\*[^*]+\*\*)/).map((part, i) =>
    part.startsWith('**') && part.endsWith('**') ? <strong key={i}>{part.slice(2, -2)}</strong> : part
  )
}

function formatResponse(text) {
  const lines = text.split('\n')
  const elements = []
  let listBuffer = []

  const flushList = () => {
    if (listBuffer.length > 0) {
      elements.push(
        <ul key={`ul-${elements.length}`} className="my-2 space-y-1.5">
          {listBuffer}
        </ul>
      )
      listBuffer = []
    }
  }

  lines.forEach((line, i) => {
    // Full-line heading: **Heading Text**
    if (/^\*\*[^*]+\*\*$/.test(line.trim())) {
      flushList()
      elements.push(
        <h4 key={i} className="mb-1 mt-4 text-h3 text-paddy">
          {line.trim().replace(/\*\*/g, '')}
        </h4>
      )
      return
    }
    // Bullet: lines starting with * or -
    if (/^[*-] /.test(line)) {
      listBuffer.push(
        <li key={i} className="flex gap-2">
          <span className="shrink-0 text-paddy" aria-hidden="true">•</span>
          <span>{formatInline(line.slice(2))}</span>
        </li>
      )
      return
    }
    // Numbered list
    if (/^\d+\./.test(line)) {
      listBuffer.push(
        <li key={i} className="flex gap-2">
          <span className="shrink-0 font-bold text-paddy">{line.match(/^\d+\./)[0]}</span>
          <span>{formatInline(line.replace(/^\d+\.\s*/, ''))}</span>
        </li>
      )
      return
    }
    // Blank line
    if (line.trim() === '') {
      flushList()
      elements.push(<div key={i} className="h-2" />)
      return
    }
    // Normal paragraph
    flushList()
    elements.push(<p key={i} className="my-1">{formatInline(line)}</p>)
  })
  flushList()
  return elements
}

const LABELS = {
  tamil: { answer: 'AI பதில்', replay: 'மீண்டும் கேளுங்கள்', asked: 'நீங்கள் கேட்டது:' },
  english: { answer: 'AI answer', replay: 'Listen again', asked: 'You asked:' },
}

export default function ResultCard({ transcript, answer, audioBase64, dialect, answerLanguage, onReplay }) {
  const { language } = useLanguage()
  const l = LABELS[language] || LABELS.tamil

  // Fallback: play backend audio if no onReplay provided
  const handleReplay = () => {
    if (onReplay) {
      onReplay()
    } else if (audioBase64) {
      const audio = new Audio('data:audio/mpeg;base64,' + audioBase64)
      audio.play().catch((e) => console.warn('[TTS] Replay failed:', e))
    }
  }

  const hasAudio = !!(onReplay || audioBase64)

  return (
    <div className="w-full">
      {transcript && (
        <div className="mb-3 rounded-control bg-white p-4 text-body">
          <p className="mb-1 text-small text-muted">{l.asked}</p>
          <p className="break-words">{transcript}</p>
        </div>
      )}

      {answer && (
        <article className="rounded-card border-2 border-line border-l-[6px] border-l-paddy bg-white p-5 sm:p-6">
          <div className="mb-2 flex items-center justify-between gap-3">
            <p className="text-small font-bold text-paddy">{l.answer}</p>
            {hasAudio && (
              <IconButton onClick={handleReplay} aria-label={l.replay} title={l.replay}>
                <SpeakerIcon className="h-6 w-6" />
              </IconButton>
            )}
          </div>
          <div className="break-words text-body text-ink">{formatResponse(answer)}</div>
        </article>
      )}

      {dialect && (answerLanguage || language) !== 'english' && (
        <div className="mt-3">
          <DialectBadge dialect={dialect} />
        </div>
      )}
    </div>
  )
}
