import { useRef, useState } from 'react'
import { analyzeCrop, apiErrorMessage } from '../services/api'
import { useLanguage } from '../context/LanguageContext'
import useSlowLoad from '../hooks/useSlowLoad'
import { Button, Card, ErrorState, PageHeader, Spinner, StatePanel, WakeNotice } from '../components/ui'
import { AlertIcon, CameraIcon, CheckIcon, ImageIcon, LeafIcon, SearchIcon, SpeakerIcon } from '../components/Icons'

const T = {
  tamil: {
    title:       'பயிர் நோய் கண்டறிதல்',
    subtitle:    'இலையின் படத்தை எடுங்கள்; AI நோயை அடையாளம் காட்டும்',
    camera:      'புகைப்படம் எடு',
    gallery:     'படத்தை தேர்வு',
    preview:     'தேர்ந்தெடுத்த பயிர் படம்',
    analyzeBtn:  'AI பகுப்பாய்வு',
    loading:     'படம் ஆய்வு செய்கிறோம்...',
    errorConn:   'இணைப்பு பிழை. மீண்டும் முயற்சிக்கவும்.',
    resultTitle: 'பகுப்பாய்வு முடிவு',
    emptyTitle:  'முடிவு இங்கே தெரியும்',
    emptyBody:   'இலையின் தெளிவான படத்தைத் தேர்ந்தெடுத்து, "AI பகுப்பாய்வு" அழுத்துங்கள்.',
    verified:    'TNAU சரிபார்க்கப்பட்டது',
    aiRec:       'AI பரிந்துரை — மருத்துவரிடம் உறுதிப்படுத்தவும்',
    playAudio:   'மீண்டும் கேளுங்கள்',
    audioPlaying:'கேட்கிறீர்கள்...',
  },
  english: {
    title:       'Crop disease check',
    subtitle:    'Photograph a leaf and the AI will identify the disease',
    camera:      'Take a photo',
    gallery:     'Choose a photo',
    preview:     'Selected crop photo',
    analyzeBtn:  'Analyze photo',
    loading:     'Analyzing image...',
    errorConn:   "Couldn't reach the server. Check your internet and try again.",
    resultTitle: 'Analysis result',
    emptyTitle:  'Your result will appear here',
    emptyBody:   'Choose a clear photo of the leaf, then press "Analyze photo".',
    verified:    'TNAU Verified',
    aiRec:       'AI suggestion. Please confirm with an agriculture expert.',
    playAudio:   'Listen again',
    audioPlaying:'Playing...',
  },
}

export default function CropScreen() {
  const { language } = useLanguage()
  const t = T[language] || T.tamil

  const cameraInputRef  = useRef(null)
  const galleryInputRef = useRef(null)
  const audioRef        = useRef(null)

  const [selectedImage, setSelectedImage] = useState(null)
  const [imagePreview,  setImagePreview]  = useState('')
  const [result,        setResult]        = useState(null)
  const [isLoading,     setIsLoading]     = useState(false)
  const [error,         setError]         = useState('')
  const [isPlaying,     setIsPlaying]     = useState(false)

  const slow = useSlowLoad(isLoading)

  const handleImageSelect = (e) => {
    const file = e.target.files[0]
    if (!file) return
    setSelectedImage(file)
    setImagePreview(URL.createObjectURL(file))
    setResult(null)
    setError('')
  }

  const playAudio = (base64) => {
    if (!base64) return
    try {
      if (audioRef.current) {
        audioRef.current.pause()
      }
      const audio = new Audio('data:audio/mpeg;base64,' + base64)
      audioRef.current = audio
      setIsPlaying(true)
      audio.onended = () => setIsPlaying(false)
      audio.onerror = () => setIsPlaying(false)
      audio.play().catch(() => setIsPlaying(false))
    } catch {
      setIsPlaying(false)
    }
  }

  const handleAnalyze = async () => {
    if (!selectedImage) return
    setIsLoading(true)
    setError('')
    setIsPlaying(false)
    try {
      const res = await analyzeCrop(selectedImage)
      setResult(res.data)
      // Auto-play TTS if available
      if (res.data?.audioBase64) {
        playAudio(res.data.audioBase64)
      }
    } catch (err) {
      setError(apiErrorMessage(err, t.errorConn))
    } finally {
      setIsLoading(false)
    }
  }

  return (
    <div>
      <PageHeader title={t.title} subtitle={t.subtitle} Icon={LeafIcon} />

      <input ref={cameraInputRef}  type="file" accept="image/*" capture="environment" className="hidden" onChange={handleImageSelect} />
      <input ref={galleryInputRef} type="file" accept="image/*" className="hidden" onChange={handleImageSelect} />

      <div className="grid gap-6 lg:grid-cols-2 lg:items-start">
        {/* ── Photo pane ── */}
        <Card className="space-y-5">
          <div className="grid gap-3 sm:grid-cols-2">
            <Button variant="secondary" onClick={() => cameraInputRef.current?.click()} className="min-h-[72px] flex-col gap-1 sm:min-h-[88px]">
              <CameraIcon className="h-8 w-8" />
              {t.camera}
            </Button>
            <Button variant="secondary" onClick={() => galleryInputRef.current?.click()} className="min-h-[72px] flex-col gap-1 sm:min-h-[88px]">
              <ImageIcon className="h-8 w-8" />
              {t.gallery}
            </Button>
          </div>

          {imagePreview && (
            <img
              src={imagePreview}
              alt={t.preview}
              className="aspect-[4/3] w-full rounded-control border-2 border-line object-cover"
            />
          )}

          {selectedImage && !isLoading && (
            <Button size="lg" onClick={handleAnalyze} className="w-full">
              <SearchIcon className="h-6 w-6" />
              {t.analyzeBtn}
            </Button>
          )}
        </Card>

        {/* ── Result pane ── */}
        <div className="min-w-0 space-y-4 lg:sticky lg:top-8">
          {isLoading && (slow ? (
            <WakeNotice />
          ) : (
            <StatePanel>
              <span className="flex flex-col items-center gap-3">
                <Spinner className="h-10 w-10" />
                {t.loading}
              </span>
            </StatePanel>
          ))}

          {error && <ErrorState message={error} onRetry={selectedImage ? handleAnalyze : undefined} />}

          {!result && !isLoading && !error && (
            <StatePanel Icon={LeafIcon} title={t.emptyTitle}>{t.emptyBody}</StatePanel>
          )}

          {result && (
            <Card className="space-y-4">
              <div className="flex items-center justify-between gap-3">
                <h2 className="text-h2">{t.resultTitle}</h2>
                {result.audioBase64 && (
                  <Button variant="secondary" onClick={() => playAudio(result.audioBase64)} disabled={isPlaying}>
                    <SpeakerIcon className="h-5 w-5" />
                    {isPlaying ? t.audioPlaying : t.playAudio}
                  </Button>
                )}
              </div>
              <pre className="whitespace-pre-wrap break-words font-body text-body">{result.diagnosis}</pre>
              {result.verified ? (
                <p className="flex items-start gap-2 rounded-control bg-paddy-soft p-3 text-body font-bold text-paddy-deep">
                  <CheckIcon className="mt-1 h-5 w-5 shrink-0" />
                  <span>{t.verified} — {result.verifiedSource}</span>
                </p>
              ) : (
                <p className="flex items-start gap-2 rounded-control bg-turmeric-soft p-3 text-body font-bold">
                  <AlertIcon className="mt-1 h-5 w-5 shrink-0" />
                  <span>{t.aiRec}</span>
                </p>
              )}
            </Card>
          )}
        </div>
      </div>
    </div>
  )
}
