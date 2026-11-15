import { useState } from 'react'
import { findSchemes, apiErrorMessage } from '../services/api'
import { useLanguage } from '../context/LanguageContext'
import useSlowLoad from '../hooks/useSlowLoad'
import SchemeCard from '../components/SchemeCard'
import { Button, Card, Chip, ErrorState, PageHeader, Skeleton, StatePanel, WakeNotice } from '../components/ui'
import { SchemeIcon, SearchIcon } from '../components/Icons'

const T = {
  tamil: {
    title:       'அரசு திட்டங்கள்',
    subtitle:    'உங்கள் நிலம் பற்றி சொல்லுங்கள்; பொருந்தும் திட்டங்களைக் காட்டுகிறோம்',
    label:       'உங்கள் நிலைமை',
    placeholder: 'உங்கள் நிலம் பற்றி சொல்லுங்கள்...',
    examplesTitle: 'இப்படி எழுதலாம்',
    examples: [
      'நான் சிறிய விவசாயி, 2 ஏக்கர் நிலம் உள்ளது',
      'பயிர் காப்பீடு வேண்டும்',
      'விவசாயக் கடன் எப்படி பெறுவது?',
    ],
    searchBtn:   'திட்டங்கள் தேடு',
    loading:     'திட்டங்கள் தேடுகிறோம்...',
    errorConn:   'இணைப்பு பிழை. மீண்டும் முயற்சிக்கவும்.',
    noResults:   'பொருந்தும் திட்டங்கள் கிடைக்கவில்லை.',
    tryAgain:    'வேறு வார்த்தைகளில் முயற்சிக்கவும்.',
    emptyTitle:  'திட்டங்கள் இங்கே தெரியும்',
    emptyBody:   'இடதுபுறம் உங்கள் நிலைமையை எழுதி "திட்டங்கள் தேடு" அழுத்துங்கள்.',
    aiSummary:   'AI பரிந்துரை',
  },
  english: {
    title:       'Government schemes',
    subtitle:    'Describe your farm and we will show the schemes that fit',
    label:       'Your situation',
    placeholder: 'Describe your farm situation...',
    examplesTitle: 'You can write',
    examples: [
      'I am a small farmer with 2 acres of land',
      'I need crop insurance',
      'How do I get a farm loan?',
    ],
    searchBtn:   'Find schemes',
    loading:     'Searching schemes...',
    errorConn:   "Couldn't reach the server. Check your internet and try again.",
    noResults:   'No matching schemes found.',
    tryAgain:    'Try with different keywords.',
    emptyTitle:  'Schemes will appear here',
    emptyBody:   'Describe your situation and press "Find schemes".',
    aiSummary:   'AI suggestion',
  },
}

export default function SchemeScreen() {
  const { language }  = useLanguage()
  const t             = T[language] || T.tamil

  const [question,  setQuestion]  = useState('')
  const [schemes,   setSchemes]   = useState([])
  const [isLoading, setIsLoading] = useState(false)
  const [error,     setError]     = useState('')
  const [searched,  setSearched]  = useState(false)

  const slow = useSlowLoad(isLoading)

  const handleFind = async () => {
    if (!question.trim()) return
    setIsLoading(true)
    setError('')
    setSchemes([])
    setSearched(false)
    try {
      const res = await findSchemes(question)
      setSchemes(res.data)
      setSearched(true)
    } catch (err) {
      console.error('[Schemes] error:', err)
      setError(apiErrorMessage(err, t.errorConn))
    } finally {
      setIsLoading(false)
    }
  }

  const aiSummary = schemes.length > 0 ? schemes[0].tamilExplanation : null

  return (
    <div>
      <PageHeader title={t.title} subtitle={t.subtitle} Icon={SchemeIcon} />

      <div className="grid gap-6 lg:grid-cols-[minmax(0,20rem)_minmax(0,1fr)] lg:items-start xl:grid-cols-[minmax(0,24rem)_minmax(0,1fr)]">
        {/* ── Search pane ── */}
        <Card className="space-y-4 lg:sticky lg:top-8">
          <label htmlFor="scheme-q" className="block text-small font-bold text-muted">{t.label}</label>
          <textarea
            id="scheme-q"
            value={question}
            onChange={(e) => setQuestion(e.target.value)}
            placeholder={t.placeholder}
            className="min-h-[9rem] w-full resize-y rounded-control border-2 border-edge bg-white px-4 py-3 text-body placeholder:text-muted focus:border-paddy disabled:bg-mist"
            disabled={isLoading}
          />

          <div>
            <p className="mb-2 text-small font-bold text-muted">{t.examplesTitle}</p>
            <ul className="space-y-2">
              {t.examples.map((ex) => (
                <li key={ex}>
                  <Chip onClick={() => setQuestion(ex)} className="w-full">{ex}</Chip>
                </li>
              ))}
            </ul>
          </div>

          <Button size="lg" onClick={handleFind} disabled={isLoading || !question.trim()} className="w-full">
            <SearchIcon className="h-6 w-6" />
            {t.searchBtn}
          </Button>
        </Card>

        {/* ── Results pane ── */}
        <div className="min-w-0 space-y-5">
          {isLoading && (slow ? (
            <WakeNotice />
          ) : (
            <div role="status" aria-label={t.loading}>
              <p className="mb-3 text-body text-muted">{t.loading}</p>
              <div className="grid gap-4 xl:grid-cols-2">
                <Skeleton className="h-52" />
                <Skeleton className="h-52" />
              </div>
            </div>
          ))}

          {error && <ErrorState message={error} onRetry={handleFind} />}

          {searched && schemes.length === 0 && !isLoading && (
            <StatePanel Icon={SearchIcon} title={t.noResults}>{t.tryAgain}</StatePanel>
          )}

          {!searched && !isLoading && !error && (
            <StatePanel Icon={SchemeIcon} title={t.emptyTitle}>{t.emptyBody}</StatePanel>
          )}

          {aiSummary && (
            <Card tone="soft" className="border-l-[6px] border-paddy">
              <p className="mb-1 text-small font-bold text-paddy">{t.aiSummary}</p>
              <p className="break-words text-body">{aiSummary}</p>
            </Card>
          )}

          <div className="grid gap-4 xl:grid-cols-2">
            {schemes.map((scheme, i) => (
              <SchemeCard key={i} {...scheme} />
            ))}
          </div>
        </div>
      </div>
    </div>
  )
}
