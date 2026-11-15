import { Button, Card } from './ui'
import { ExternalIcon } from './Icons'
import { useLanguage } from '../context/LanguageContext'
import { SCHEME_EN } from '../schemeText'

const LABELS = {
  tamil: { eligibility: 'தகுதி', benefits: 'நன்மைகள்', apply: 'விண்ணப்பிக்க' },
  english: { eligibility: 'Who can apply', benefits: 'Benefits', apply: 'Apply' },
}

export default function SchemeCard({ schemeName, eligibility, benefits, officialLink }) {
  const { language } = useLanguage()
  const l = LABELS[language] || LABELS.tamil
  // The backend sends these two lines in Tamil; in English mode show the English text for known schemes.
  const en = language === 'english' ? SCHEME_EN[officialLink] : null
  const eligibilityText = en?.eligibility ?? eligibility
  const benefitsText = en?.benefits ?? benefits

  return (
    <Card as="article" className="flex h-full flex-col">
      <h3 className="text-balance text-h3 text-paddy-deep">{schemeName}</h3>

      <dl className="mt-3 flex-1 space-y-3 text-body">
        <div>
          <dt className="text-small font-bold text-paddy">{l.eligibility}</dt>
          <dd className="break-words">{eligibilityText}</dd>
        </div>
        <div>
          <dt className="text-small font-bold text-paddy">{l.benefits}</dt>
          <dd className="break-words">{benefitsText}</dd>
        </div>
      </dl>

      {officialLink && (
        <Button
          as="a"
          href={officialLink}
          target="_blank"
          rel="noopener noreferrer"
          className="mt-5 w-full"
        >
          {l.apply}
          <ExternalIcon className="h-5 w-5" />
        </Button>
      )}
    </Card>
  )
}
