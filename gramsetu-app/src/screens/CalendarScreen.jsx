import { useEffect, useState } from 'react'
import { getCropCalendar, getPersonalizedCalendar, apiErrorMessage } from '../services/api'
import { useLanguage } from '../context/LanguageContext'
import useSlowLoad from '../hooks/useSlowLoad'
import { Button, Card, Chip, ErrorState, PageHeader, Skeleton, StatePanel, WakeNotice } from '../components/ui'
import { CalendarIcon, LeafIcon } from '../components/Icons'

const MONTHS_TAMIL = [
  'ஜனவரி', 'பிப்ரவரி', 'மார்ச்', 'ஏப்ரல்', 'மே', 'ஜூன்',
  'ஜூலை', 'ஆகஸ்ட்', 'செப்டம்பர்', 'அக்டோபர்', 'நவம்பர்', 'டிசம்பர்',
]

// Season by current month (1-indexed)
const getSeason = (month) => {
  if (month >= 3 && month <= 5)  return 'கோடை (மார்ச்–மே)'
  if (month >= 6 && month <= 9)  return 'கார் (ஜூன்–செப்)'
  if (month >= 10 && month <= 12) return 'குளிர் (அக்டோ–டிசம்)'
  return 'தை (ஜன–பிப்)'
}

const CROPS    = ['தக்காளி', 'நெல்', 'நிலக்கடலை', 'மஞ்சள்', 'கத்திரிக்காய்', 'பீன்ஸ்']
const SOILS    = ['செம்மண்', 'கருமண்', 'மணல் மண்', 'வண்டல் மண்']
const DISTRICTS = ['தஞ்சாவூர்', 'கோயம்புத்தூர்', 'கிருஷ்ணகிரி', 'திண்டுக்கல்', 'திருநெல்வேலி']

const DISTRICT_DATA = {
  'தஞ்சாவூர்': [
    { cropNameTamil: 'நெல்',    cropNameEnglish: 'Rice',      emoji: '🌾', currentMonthActivity: 'அறுவடை காலம் - கோடை நெல் நடவு தயார்', activityType: 'HARVESTING',  localTip: '' },
    { cropNameTamil: 'வாழை',   cropNameEnglish: 'Banana',    emoji: '🍌', currentMonthActivity: 'குலை பராமரிக்கவும்',                   activityType: 'MAINTENANCE', localTip: '' },
    { cropNameTamil: 'கரும்பு', cropNameEnglish: 'Sugarcane', emoji: '🌿', currentMonthActivity: 'நடவு செய்யலாம்',                       activityType: 'PLANTING',    localTip: '' },
  ],
  'கோயம்புத்தூர்': [
    { cropNameTamil: 'பருத்தி',       cropNameEnglish: 'Cotton',    emoji: '🌿', currentMonthActivity: 'நிலம் தயார் செய்யுங்கள்', activityType: 'MAINTENANCE', localTip: '' },
    { cropNameTamil: 'மக்காச்சோளம்', cropNameEnglish: 'Maize',     emoji: '🌽', currentMonthActivity: 'நடவு செய்யலாம்',          activityType: 'PLANTING',    localTip: '' },
    { cropNameTamil: 'கரும்பு',       cropNameEnglish: 'Sugarcane', emoji: '🌿', currentMonthActivity: 'பராமரிப்பு',              activityType: 'MAINTENANCE', localTip: '' },
  ],
  'திண்டுக்கல்': [
    { cropNameTamil: 'வாழை',    cropNameEnglish: 'Banana',   emoji: '🍌', currentMonthActivity: 'நடவு செய்யலாம்', activityType: 'PLANTING',   localTip: '' },
    { cropNameTamil: 'மஞ்சள்', cropNameEnglish: 'Turmeric', emoji: '🌿', currentMonthActivity: 'அறுவடை காலம்',   activityType: 'HARVESTING', localTip: '' },
    { cropNameTamil: 'மரவள்ளி', cropNameEnglish: 'Tapioca',  emoji: '🌱', currentMonthActivity: 'நடவு தயார்',     activityType: 'PLANTING',   localTip: '' },
  ],
  'திருநெல்வேலி': [
    { cropNameTamil: 'வாழை',       cropNameEnglish: 'Banana',    emoji: '🍌', currentMonthActivity: 'பராமரிப்பு காலம்', activityType: 'MAINTENANCE', localTip: '' },
    { cropNameTamil: 'நெல்',       cropNameEnglish: 'Rice',      emoji: '🌾', currentMonthActivity: 'நடவு தயார்',       activityType: 'PLANTING',    localTip: '' },
    { cropNameTamil: 'நிலக்கடலை', cropNameEnglish: 'Groundnut', emoji: '🥜', currentMonthActivity: 'அறுவடை காலம்',    activityType: 'HARVESTING',  localTip: '' },
  ],
}

const T = {
  tamil: {
    title:        'பயிர் நாட்காட்டி',
    subtitle:     'இந்த மாதம் என்ன செய்வது என்று பாருங்கள்',
    tabGeneral:   'மாத நாட்காட்டி',
    tabMyPlan:    'என் திட்டம்',
    planting:     'இப்போது நடவு',
    harvesting:   'இப்போது அறுவடை',
    maintenance:  'பூக்கும் / பராமரிப்பு',
    noPlanting:   'இந்த மாதம் ஏற்ற பயிர் இல்லை',
    noHarvest:    'இந்த மாதம் ஏற்ற பயிர் இல்லை',
    errorMsg:     'தரவு கிடைக்கவில்லை. மீண்டும் முயற்சிக்கவும்.',
    monthLine:    (d) => `${d} மாவட்டம் — இந்த மாதம் என்ன செய்வது?`,
    step1:        'என்ன பயிர் சாகுபடி செய்கிறீர்கள்?',
    step2:        'மண் வகை என்ன?',
    step3:        'நிலம் எத்தனை ஏக்கர்?',
    step4:        'தற்போதைய பருவம்:',
    acresPlaceholder: 'ஏக்கர் எண்ணிக்கை (எ.கா: 2)',
    generateBtn:  'திட்டம் தயார் செய்',
    generating:   'திட்டம் தயாரிக்கிறோம்...',
    planTitle:    'உங்கள் தனிப்பட்ட சாகுபடி திட்டம்',
    planEmpty:    'திட்டம் இங்கே தெரியும்',
    planEmptyBody:'இடதுபுறம் பயிர், மண், நிலத்தின் அளவைத் தேர்ந்தெடுத்து "திட்டம் தயார் செய்" அழுத்துங்கள்.',
    newPlan:      'புதிய திட்டம்',
    planError:    'திட்டம் கிடைக்கவில்லை. மீண்டும் முயற்சிக்கவும்.',
  },
  english: {
    title:        'Crop calendar',
    subtitle:     'See what to do on the farm this month',
    tabGeneral:   'Monthly calendar',
    tabMyPlan:    'My plan',
    planting:     'Planting now',
    harvesting:   'Harvesting now',
    maintenance:  'Flowering / care',
    noPlanting:   'No suitable crop this month',
    noHarvest:    'No suitable crop this month',
    errorMsg:     "Couldn't load the calendar. Check your internet and try again.",
    monthLine:    (d) => `${d} district — what to do this month?`,
    step1:        'Which crop do you grow?',
    step2:        'What is your soil type?',
    step3:        'How many acres?',
    step4:        'Current season:',
    acresPlaceholder: 'Number of acres (e.g. 2)',
    generateBtn:  'Create plan',
    generating:   'Creating your plan...',
    planTitle:    'Your personal cultivation plan',
    planEmpty:    'Your plan will appear here',
    planEmptyBody:'Choose a crop, soil and acreage on the left, then press "Create plan".',
    newPlan:      'New plan',
    planError:    "Couldn't create your plan. Check your internet and try again.",
  },
}

const TYPE_TONES = {
  PLANTING:    { box: 'bg-paddy-soft',     text: 'text-paddy-deep' },
  HARVESTING:  { box: 'bg-turmeric-soft',  text: 'text-ink' },
  FLOWERING:   { box: 'bg-soil-soft',      text: 'text-ink' },
  MAINTENANCE: { box: 'bg-indigoDye-soft', text: 'text-ink' },
}

function CropCard({ crop, type }) {
  const tone = TYPE_TONES[type] || TYPE_TONES.PLANTING
  return (
    <li className={`flex items-start gap-4 rounded-control p-4 ${tone.box}`}>
      <span aria-hidden="true" className="text-4xl leading-none">{crop.emoji}</span>
      <div className="min-w-0 flex-1">
        <p className={`text-h3 font-bold ${tone.text}`}>{crop.cropNameTamil}</p>
        <p className="mt-0.5 break-words text-body">{crop.currentMonthActivity}</p>
        {crop.localTip ? <p className="mt-1 text-small text-muted">{crop.localTip}</p> : null}
      </div>
    </li>
  )
}

function Section({ title, crops, type, emptyMsg }) {
  return (
    <section>
      <h2 className="mb-3 text-h2">{title}</h2>
      {crops.length === 0 ? (
        <p className="rounded-control border-2 border-dashed border-line p-4 text-body text-muted">{emptyMsg}</p>
      ) : (
        <ul className="space-y-3">
          {crops.map((c) => (
            <CropCard key={c.cropNameEnglish} crop={c} type={type} />
          ))}
        </ul>
      )}
    </section>
  )
}

function renderMarkdownLine(text) {
  const parts = text.split(/\*\*(.*?)\*\*/g)
  return parts.map((part, i) =>
    i % 2 === 1 ? <strong key={i}>{part}</strong> : part
  )
}

function renderPlan(plan) {
  const lines = plan.split('\n').filter((l) => l.trim())
  const sections = []
  let current = null

  for (const line of lines) {
    const trimmed = line.trim()
    if (/^#{1,3}\s/.test(trimmed)) {
      if (current) sections.push(current)
      current = { heading: trimmed.replace(/^#+\s*/, '').replace(/\*\*/g, ''), lines: [] }
    } else if (/^[-•*]\s/.test(trimmed)) {
      if (!current) current = { heading: null, lines: [] }
      current.lines.push(trimmed.replace(/^[-•*]\s*/, ''))
    } else if (trimmed) {
      if (!current) current = { heading: null, lines: [] }
      current.lines.push(trimmed)
    }
  }
  if (current) sections.push(current)

  return sections.map((sec, i) => (
    <Card key={i} className="!p-4">
      {sec.heading && <h3 className="mb-2 text-h3 text-paddy-deep">{sec.heading}</h3>}
      {sec.lines.slice(0, 4).map((ln, j) => (
        <p key={j} className="break-words text-body">
          {renderMarkdownLine(ln)}
        </p>
      ))}
    </Card>
  ))
}

function PersonalizedPlanWizard({ t }) {
  const now       = new Date()
  const season    = getSeason(now.getMonth() + 1)

  const [cropName,    setCropName]    = useState('')
  const [soilType,    setSoilType]    = useState('')
  const [acres,       setAcres]       = useState('')
  const [plan,        setPlan]        = useState('')
  const [loading,     setLoading]     = useState(false)
  const [error,       setError]       = useState('')

  const slow = useSlowLoad(loading)

  const handleGenerate = async () => {
    if (!cropName || !soilType || !acres) return
    setLoading(true)
    setError('')
    try {
      const res = await getPersonalizedCalendar({ cropName, soilType, acres, season })
      setPlan(res.data.plan)
    } catch (err) {
      setError(apiErrorMessage(err, t.planError))
    } finally {
      setLoading(false)
    }
  }

  const reset = () => { setPlan(''); setCropName(''); setSoilType(''); setAcres(''); setError('') }

  return (
    <div className="grid gap-6 lg:grid-cols-[minmax(0,22rem)_minmax(0,1fr)] lg:items-start xl:grid-cols-[minmax(0,26rem)_minmax(0,1fr)]">
      {/* ── Questions ── */}
      <Card className="space-y-6">
        <div>
          <p className="mb-2 text-body font-bold">1. {t.step1}</p>
          <div className="flex flex-wrap gap-2">
            {CROPS.map((c) => (
              <Chip key={c} selected={cropName === c} onClick={() => setCropName(c)}>{c}</Chip>
            ))}
          </div>
        </div>

        <div>
          <p className="mb-2 text-body font-bold">2. {t.step2}</p>
          <div className="flex flex-wrap gap-2">
            {SOILS.map((s) => (
              <Chip key={s} selected={soilType === s} onClick={() => setSoilType(s)}>{s}</Chip>
            ))}
          </div>
        </div>

        <div>
          <label htmlFor="acres" className="mb-2 block text-body font-bold">3. {t.step3}</label>
          <input
            id="acres"
            type="number"
            inputMode="decimal"
            min="0.5"
            max="100"
            step="0.5"
            value={acres}
            onChange={(e) => setAcres(e.target.value)}
            placeholder={t.acresPlaceholder}
            className="min-h-[56px] w-full rounded-control border-2 border-edge bg-white px-4 text-body placeholder:text-muted focus:border-paddy"
          />
        </div>

        <div>
          <p className="mb-2 text-body font-bold">4. {t.step4}</p>
          <p className="inline-flex min-h-[48px] items-center gap-2 rounded-control bg-paddy px-4 font-bold text-white">
            <CalendarIcon className="h-5 w-5" />
            {season}
          </p>
        </div>

        <Button
          size="lg"
          onClick={handleGenerate}
          disabled={!cropName || !soilType || !acres || loading}
          className="w-full"
        >
          {loading ? t.generating : t.generateBtn}
        </Button>
      </Card>

      {/* ── Plan ── */}
      <div className="min-w-0 space-y-4">
        {loading && (slow ? <WakeNotice /> : <Skeleton className="h-40" />)}
        {error && <ErrorState message={error} onRetry={handleGenerate} />}

        {!plan && !loading && !error && (
          <StatePanel Icon={LeafIcon} title={t.planEmpty}>{t.planEmptyBody}</StatePanel>
        )}

        {plan && (
          <>
            <div className="flex flex-wrap items-center justify-between gap-3">
              <h2 className="text-h2">{t.planTitle}</h2>
              <Button variant="secondary" onClick={reset}>{t.newPlan}</Button>
            </div>
            <div className="grid gap-4 xl:grid-cols-2">{renderPlan(plan)}</div>
          </>
        )}
      </div>
    </div>
  )
}

export default function CalendarScreen() {
  const { language } = useLanguage()
  const t = T[language] || T.tamil

  const [activeTab,        setActiveTab]        = useState('general')  // 'general' | 'myplan'
  const [selectedDistrict, setSelectedDistrict] = useState('கிருஷ்ணகிரி')
  const [calendarData,     setCalendarData]     = useState([])
  const [loading,          setLoading]          = useState(true)
  const [error,            setError]            = useState('')
  const [reloadKey,        setReloadKey]        = useState(0)

  const slow = useSlowLoad(loading)

  const now        = new Date()
  const monthLabel = language === 'english'
    ? now.toLocaleString('en-US', { month: 'long' })
    : MONTHS_TAMIL[now.getMonth()]
  const year = now.getFullYear()

  useEffect(() => {
    if (activeTab !== 'general') return
    setError('')
    if (selectedDistrict === 'கிருஷ்ணகிரி') {
      setLoading(true)
      getCropCalendar()
        .then((res) => setCalendarData(res.data))
        .catch(() => setError(t.errorMsg))
        .finally(() => setLoading(false))
    } else {
      setCalendarData(DISTRICT_DATA[selectedDistrict] || [])
      setLoading(false)
    }
  }, [selectedDistrict, activeTab, reloadKey])

  const planting    = calendarData.filter((c) => c.activityType === 'PLANTING')
  const harvesting  = calendarData.filter((c) => c.activityType === 'HARVESTING')
  const maintenance = calendarData.filter((c) => c.activityType === 'FLOWERING' || c.activityType === 'MAINTENANCE')

  const tab = (key, label) => (
    <button
      key={key}
      type="button"
      role="tab"
      id={`tab-${key}`}
      aria-selected={activeTab === key}
      aria-controls={`panel-${key}`}
      tabIndex={activeTab === key ? 0 : -1}
      onClick={() => setActiveTab(key)}
      onKeyDown={(e) => {
        if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(e.key)) return
        e.preventDefault()
        const next = e.key === 'ArrowLeft' || e.key === 'Home' ? 'general' : 'myplan'
        setActiveTab(next)
        setTimeout(() => document.getElementById(`tab-${next}`)?.focus(), 0)
      }}
      className={`min-h-[52px] flex-1 rounded-xl px-4 text-body font-bold transition-colors ${
        activeTab === key ? 'bg-paddy text-white' : 'text-ink hover:bg-paddy-soft'
      }`}
    >
      {label}
    </button>
  )

  return (
    <div>
      <PageHeader title={t.title} subtitle={t.subtitle} Icon={CalendarIcon} />

      <div role="tablist" className="mb-6 flex max-w-xl gap-1 rounded-control border-2 border-line bg-white p-1">
        {tab('general', t.tabGeneral)}
        {tab('myplan', t.tabMyPlan)}
      </div>

      {activeTab === 'myplan' && (
        <div role="tabpanel" id="panel-myplan" aria-labelledby="tab-myplan">
          <PersonalizedPlanWizard t={t} />
        </div>
      )}

      {activeTab === 'general' && (
        <div role="tabpanel" id="panel-general" aria-labelledby="tab-general" className="space-y-6">
          {/* District selector: wraps onto several lines, never scrolls sideways */}
          <div className="flex flex-wrap gap-2">
            {DISTRICTS.map((d) => (
              <Chip key={d} selected={selectedDistrict === d} onClick={() => setSelectedDistrict(d)} className="rounded-full">
                {d}
              </Chip>
            ))}
          </div>

          <Card tone="dark" className="text-center sm:text-left">
            <p className="text-h2">{monthLabel} {year}</p>
            <p className="mt-1 text-lead text-white/90">{t.monthLine(selectedDistrict)}</p>
          </Card>

          {loading && (slow ? (
            <WakeNotice />
          ) : (
            <div className="grid gap-6 md:grid-cols-2 xl:grid-cols-3" aria-hidden="true">
              <Skeleton className="h-40" />
              <Skeleton className="hidden h-40 xl:block" />
              <Skeleton className="hidden h-40 xl:block" />
            </div>
          ))}

          {error && <ErrorState message={error} onRetry={() => setReloadKey((k) => k + 1)} />}

          {!loading && !error && (
            <div className="grid gap-8 md:grid-cols-2 md:gap-6 xl:grid-cols-3">
              <Section title={t.planting}   crops={planting}   type="PLANTING"   emptyMsg={t.noPlanting} />
              <Section title={t.harvesting} crops={harvesting} type="HARVESTING" emptyMsg={t.noHarvest} />
              {(maintenance.length > 0) && (
                <Section title={t.maintenance} crops={maintenance} type="MAINTENANCE" emptyMsg="" />
              )}
            </div>
          )}
        </div>
      )}
    </div>
  )
}
