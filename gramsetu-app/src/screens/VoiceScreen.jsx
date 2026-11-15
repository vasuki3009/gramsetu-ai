import { useEffect, useRef, useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { askQuestion, sendVoice, getAllSchemes, apiErrorMessage } from '../services/api'
import { useLanguage } from '../context/LanguageContext'
import useSlowLoad from '../hooks/useSlowLoad'
import { STR } from '../strings'
import BigMicButton from '../components/BigMicButton'
import ResultCard from '../components/ResultCard'
import { Button, Card, Chip, ErrorState, PageHeader, Skeleton, StatePanel, TypingDots, WakeNotice } from '../components/ui'
import { BulbIcon, MicIcon, SchemeIcon, SendIcon, StopIcon } from '../components/Icons'

const T = {
  tamil: {
    title:       'கேளுங்கள்',
    subtitle:    'குரலில் அல்லது எழுத்தில் கேளுங்கள்',
    orType:      'அல்லது தட்டச்சு செய்யுங்கள்',
    placeholder: 'உங்கள் கேள்வியை தட்டச்சு செய்யுங்கள்...',
    sendBtn:     'அனுப்பு',
    loading:     'பதில் தயாரிக்கிறோம்...',
    sttLoading:  'குரலை படிக்கிறோம்...',
    errorMic:    'மைக் அனுமதி இல்லை. தட்டச்சு செய்யவும்.',
    errorConn:   'இணைப்பு பிழை. மீண்டும் முயற்சிக்கவும்.',
    retry:       'மீண்டும் முயற்சிக்க',
    stop:        'நிறுத்து',
    youSaid:     'நீங்கள் சொன்னது:',
    emptyTitle:  'இன்னும் கேள்விகள் இல்லை',
    noHistory:   'குரல் பொத்தானை அழுத்தி பேசுங்கள் அல்லது கேள்வி தட்டச்சு செய்யுங்கள்',
    voiceQuestion: 'குரல் கேள்வி',
  },
  english: {
    title:       'Ask',
    subtitle:    'Ask by voice or by typing',
    orType:      'Or type your question',
    placeholder: 'Type your question here...',
    sendBtn:     'Send',
    loading:     'Preparing your answer...',
    sttLoading:  'Reading your voice...',
    errorMic:    'The microphone is blocked. Allow it in your browser settings, or type your question.',
    errorConn:   "Couldn't reach the server. Check your internet and try again.",
    retry:       'Try again',
    stop:        'Stop',
    youSaid:     'You said:',
    emptyTitle:  'No questions yet',
    noHistory:   'Press the mic to speak, or type your question.',
    voiceQuestion: 'Voice question',
  },
}

// ── Helpers ───────────────────────────────────────────────────────────────────
const fmt = (d, lang) =>
  d.toLocaleTimeString(lang === 'english' ? 'en-IN' : 'ta-IN', { hour: '2-digit', minute: '2-digit' })

// ── Component ─────────────────────────────────────────────────────────────────
export default function VoiceScreen() {
  const { language }  = useLanguage()
  const t             = T[language] || T.tamil
  const s             = STR[language] || STR.tamil
  const { state }     = useLocation()

  // chat history: newest at index 0, max 5 items
  // shape: { id, question, answer, dialect, region, confidence, detectedBy, time }
  const [chatHistory,  setChatHistory]  = useState([])
  const [isListening,  setIsListening]  = useState(false)
  const [isLoading,    setIsLoading]    = useState(false)
  const [sttLoading,   setSttLoading]   = useState(false)
  const [error,        setError]        = useState('')
  const [textQuestion, setTextQuestion] = useState(state?.q || '')
  const [popular,      setPopular]      = useState([])

  const mediaRecorderRef = useRef(null)
  const audioChunksRef   = useRef([])
  const mimeTypeRef      = useRef('')
  const currentAudioRef  = useRef(null)   // currently playing Audio element
  const inputRef         = useRef(null)

  const [isSpeaking, setIsSpeaking] = useState(false)

  const busy = isLoading || sttLoading
  const slow = useSlowLoad(busy)

  // Arrived from the home screen: put the cursor in the box (typed question or an example).
  useEffect(() => {
    if (state?.focusText || state?.q) inputRef.current?.focus()
  }, [state])

  // Popular schemes for the side panel. Best-effort: if the backend is asleep or the call
  // fails the panel just leaves that card out.
  useEffect(() => {
    let alive = true
    getAllSchemes()
      .then((res) => { if (alive && Array.isArray(res.data)) setPopular(res.data.slice(0, 3)) })
      .catch(() => {})
    return () => { alive = false }
  }, [])

  // ── TTS ───────────────────────────────────────────────────────────────────
  // /ask and /voice return `audioBase64`, synthesised server-side with the dialect-matched
  // Sarvam voice, so no API key (or direct third-party call) is needed in the browser.
  // When the backend sent no audio (English mode, TTS unavailable) we fall back to the
  // browser's built-in speech synthesis.
  // Every speech start/stop gets a new id. Events of an older clip (a cancelled utterance, a paused
  // audio element) carry an old id and are ignored, so they can neither hide the Stop button while a
  // newer clip plays nor leave it showing after the newer clip is gone.
  const speechIdRef = useRef(0)

  const speakWithBrowser = (text, id, answerLanguage) => {
    const synth = window.speechSynthesis
    if (!synth) { setIsSpeaking(false); return }   // nothing can play: do not leave the Stop button up
    const finish = () => { if (speechIdRef.current === id) setIsSpeaking(false) }
    const cleanText = text
      .replace(/\*\*/g, '')
      .replace(/\*/g, '')
      .replace(/•/g, '')
      .trim()
    const utterance = new SpeechSynthesisUtterance(cleanText)
    utterance.lang = answerLanguage === 'english' ? 'en-IN' : 'ta-IN'   // an English answer needs an English voice
    utterance.rate = 0.9
    utterance.pitch = 1.0
    utterance.onend = finish
    utterance.onerror = finish
    setIsSpeaking(true)
    synth.speak(utterance)
    // A browser without a Tamil voice may never fire end or error: if nothing is speaking or queued
    // shortly after, hide the Stop button.
    setTimeout(() => {
      if (speechIdRef.current === id && !synth.speaking && !synth.pending) setIsSpeaking(false)
    }, 1500)
  }

  const speakResponse = (text, audioB64, answerLanguage) => {
    stopSpeaking()
    const id = speechIdRef.current   // stopSpeaking() just moved it on: this clip owns the new id
    if (!audioB64) {
      speakWithBrowser(text, id, answerLanguage)
      return
    }
    const audio = new Audio('data:audio/mpeg;base64,' + audioB64)
    currentAudioRef.current = audio
    setIsSpeaking(true)
    let fellBack = false
    const fallBack = () => {
      if (fellBack || speechIdRef.current !== id) return
      fellBack = true
      currentAudioRef.current = null
      speakWithBrowser(text, id, answerLanguage)
    }
    audio.onended = () => {
      if (speechIdRef.current !== id) return
      currentAudioRef.current = null
      setIsSpeaking(false)
    }
    audio.onerror = fallBack
    audio.play().catch(fallBack)   // e.g. autoplay blocked: fall back to the browser voice
  }

  const stopSpeaking = () => {
    speechIdRef.current += 1
    if (currentAudioRef.current) {
      currentAudioRef.current.pause()
      currentAudioRef.current = null
    }
    window.speechSynthesis?.cancel()
    setIsSpeaking(false)
  }

  // Leaving the screen: stop any clip, and drop a recording in progress (it must not be sent).
  useEffect(() => () => {
    speechIdRef.current += 1
    currentAudioRef.current?.pause()
    currentAudioRef.current = null
    window.speechSynthesis?.cancel()
    const mr = mediaRecorderRef.current
    if (mr && mr.state !== 'inactive') {
      mr.onstop = null
      try { mr.stop() } catch { /* already stopped */ }
      mr.stream?.getTracks().forEach((tr) => tr.stop())
    }
  }, [])

  // ── Mic ────────────────────────────────────────────────────────────────────
  const handleMicPress = async () => {
    if (isLoading || sttLoading) return
    if (isListening) {
      mediaRecorderRef.current?.stop()
      setIsListening(false)
      return
    }
    setError('')
    stopSpeaking()   // do not record the speaker, and the Stop button only belongs to playback
    try {
      const stream   = await navigator.mediaDevices.getUserMedia({ audio: true })
      const mimeType = MediaRecorder.isTypeSupported('audio/webm;codecs=opus')
        ? 'audio/webm;codecs=opus'
        : MediaRecorder.isTypeSupported('audio/webm')
        ? 'audio/webm'
        : 'audio/ogg'
      mimeTypeRef.current    = mimeType
      audioChunksRef.current = []

      const mr = new MediaRecorder(stream, { mimeType })
      mediaRecorderRef.current = mr

      // If the microphone disappears or the recorder fails, leave the "recording" state (red Stop) at once.
      mr.onerror = () => { stream.getTracks().forEach((tr) => tr.stop()); setIsListening(false) }
      stream.getTracks().forEach((tr) => { tr.onended = () => setIsListening(false) })

      mr.ondataavailable = (e) => { if (e.data.size > 0) audioChunksRef.current.push(e.data) }
      mr.onstop = async () => {
        stream.getTracks().forEach((tr) => tr.stop())
        const blob = new Blob(audioChunksRef.current, { type: mimeTypeRef.current })
        setSttLoading(true)
        setError('')
        try {
          // Route audio through backend — it handles STT + AI + TTS server-side
          const res  = await sendVoice(blob, 'Hosur', mimeTypeRef.current)
          const data = res.data
          const newItem = {
            id:         Date.now(),
            question:   data.transcript || '',   // an empty transcript shows the "Voice question" label in the current language
            answer:     data.tamilAnswer,
            audioB64:   data.audioBase64 || '',
            dialect:    data.dialectDetected,
            answerLanguage: data.answerLanguage || language,
            region:     data.dialectRegion,
            confidence: data.confidence,
            detectedBy: data.detectedBy,
            time:       new Date(),
          }
          setChatHistory((prev) => [newItem, ...prev].slice(0, 5))
          // Auto-speak
          speakResponse(data.tamilAnswer, data.audioBase64, data.answerLanguage || language)
        } catch (err) {
          console.error('[Voice] Backend error:', err)
          setError(apiErrorMessage(err, t.errorConn))
        } finally {
          setSttLoading(false)
        }
      }

      mr.start()
      setIsListening(true)
      setTextQuestion('')
    } catch (err) {
      console.error('[Mic] getUserMedia failed:', err)
      setError(t.errorMic)
    }
  }

  // ── Replay stored audio ───────────────────────────────────────────────────
  const handleReplay = (item) => {
    speakResponse(item.answer, item.audioB64, item.answerLanguage)
  }

  // ── Submit text / transcript ────────────────────────────────────────────────
  const handleTextSubmit = async () => {
    const q = textQuestion.trim()
    if (!q) return
    setIsLoading(true)
    setError('')
    setTextQuestion('')
    try {
      const res  = await askQuestion(q)
      const data = res.data

      const newItem = {
        id:         Date.now(),
        question:   q,
        answer:     data.tamilAnswer,
        audioB64:   data.audioBase64 || '',
        dialect:    data.dialectDetected,
        answerLanguage: data.answerLanguage || language,
        region:     data.dialectRegion,
        confidence: data.confidence,
        detectedBy: data.detectedBy,
        time:       new Date(),
      }
      setChatHistory((prev) => [newItem, ...prev].slice(0, 5))
      // Auto-speak
      speakResponse(data.tamilAnswer, data.audioBase64, data.answerLanguage || language)

    } catch (err) {
      console.error('[Ask] Backend error:', err)
      setError(apiErrorMessage(err, t.errorConn))
    } finally {
      setIsLoading(false)
    }
  }

  const pickExample = (q) => {
    setTextQuestion(q)
    inputRef.current?.focus()
  }

  // ── Render ─────────────────────────────────────────────────────────────────
  return (
    <div>
      <PageHeader title={t.title} subtitle={t.subtitle} Icon={MicIcon} />

      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_18rem] lg:items-start xl:grid-cols-[minmax(0,1fr)_25rem]">
        {/* ── Conversation pane ── */}
        <section aria-label={t.title} className="min-w-0 space-y-6">
          <Card className="flex flex-col items-center gap-6 md:flex-row md:gap-8 lg:flex-col xl:flex-row">
            <BigMicButton isListening={isListening} isLoading={busy} onClick={handleMicPress} />

            <div className="w-full min-w-0 flex-1 space-y-3">
              <label htmlFor="ask-input" className="block text-small font-bold text-muted">
                {t.orType}
              </label>
              <textarea
                id="ask-input"
                ref={inputRef}
                rows={2}
                maxLength={1000}
                value={textQuestion}
                onChange={(e) => setTextQuestion(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' && !e.shiftKey) {
                    e.preventDefault()
                    handleTextSubmit()
                  }
                }}
                placeholder={t.placeholder}
                disabled={busy}
                className="min-h-[84px] w-full resize-none rounded-control border-2 border-edge bg-white px-4 py-3 text-body placeholder:text-muted focus:border-paddy disabled:bg-mist"
              />
              <Button
                onClick={handleTextSubmit}
                disabled={busy || !textQuestion.trim()}
                className="w-full"
              >
                <SendIcon className="h-5 w-5" />
                {t.sendBtn}
              </Button>
            </div>
          </Card>

          {/* Loading: spinner first, friendly "waking up" message once it drags on */}
          {busy && (slow ? (
            <WakeNotice />
          ) : (
            <div className="space-y-3">
              <div className="flex items-center gap-3 text-body text-muted">
                <TypingDots label={sttLoading ? t.sttLoading : t.loading} />
                <p aria-hidden="true">{sttLoading ? t.sttLoading : t.loading}</p>
              </div>
              <Skeleton className="h-36" />
            </div>
          ))}

          {isSpeaking && (
            <Button variant="danger" onClick={stopSpeaking}>
              <StopIcon className="h-5 w-5" />
              {t.stop}
            </Button>
          )}

          {error && (
            <ErrorState
              message={error}
              retryLabel={t.retry}
              onRetry={() => { setError(''); handleMicPress() }}
            />
          )}

          {chatHistory.length === 0 && !busy && !error && (
            <StatePanel Icon={MicIcon} title={t.emptyTitle}>{t.noHistory}</StatePanel>
          )}

          {/* Chat history — newest first */}
          <div className="space-y-8">
            {chatHistory.map((item) => (
              <article key={item.id} className="motion-safe:animate-rise">
                {/* Question bubble (farmer, right-aligned) */}
                <div className="mb-3 flex justify-end">
                  <div className="max-w-[88%] break-words rounded-card rounded-tr-md bg-paddy p-4 text-body text-white">
                    <p className="mb-1 text-small text-white/85">{t.youSaid}</p>
                    {item.question || t.voiceQuestion}
                  </div>
                </div>

                <ResultCard
                  answer={item.answer}
                  dialect={item.dialect}
                  answerLanguage={item.answerLanguage}
                  onReplay={() => handleReplay(item)}
                />

                <p className="mt-2 text-right text-small text-muted">{fmt(item.time, language)}</p>
              </article>
            ))}
          </div>
        </section>

        {/* ── Side panel: ideas, tips and schemes ── */}
        <aside aria-label={s.ask.tipsTitle} className="space-y-4 lg:sticky lg:top-8">
          <Card tone="soft">
            <h2 className="mb-3 text-h3">{s.ask.examplesTitle}</h2>
            <ul className="space-y-2">
              {s.home.examples.map((q) => (
                <li key={q}>
                  <Chip onClick={() => pickExample(q)} className="w-full">{q}</Chip>
                </li>
              ))}
            </ul>
          </Card>

          <Card>
            <h2 className="mb-3 flex items-center gap-2 text-h3">
              <BulbIcon className="h-6 w-6 text-paddy" />
              {s.ask.tipsTitle}
            </h2>
            <ul className="list-disc space-y-2 pl-5 text-body">
              {s.ask.tips.map((tip) => (
                <li key={tip}>{tip}</li>
              ))}
            </ul>
          </Card>

          {popular.length > 0 && (
            <Card>
              <h2 className="mb-3 flex items-center gap-2 text-h3">
                <SchemeIcon className="h-6 w-6 text-paddy" />
                {s.ask.schemesTitle}
              </h2>
              <ul className="space-y-2 text-body">
                {popular.map((p) => (
                  <li key={p.id ?? p.schemeName} className="break-words">{p.schemeName}</li>
                ))}
              </ul>
              <Button as={Link} to="/schemes" variant="secondary" className="mt-4 w-full">
                {s.ask.viewAll}
              </Button>
            </Card>
          )}
        </aside>
      </div>
    </div>
  )
}
