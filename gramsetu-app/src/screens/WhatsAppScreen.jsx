import WhatsAppConnect from '../components/WhatsAppConnect'
import { PageHeader } from '../components/ui'
import { ChatIcon } from '../components/Icons'
import { useLanguage } from '../context/LanguageContext'
import { STR } from '../strings'

// Full-page version of the WhatsApp card from the home screen: steps, join button and QR code.
export default function WhatsAppScreen() {
  const { language } = useLanguage()
  const w = (STR[language] || STR.tamil).whatsapp

  return (
    <div>
      <PageHeader title={w.title} subtitle={w.pageIntro} Icon={ChatIcon} />
      <WhatsAppConnect variant="page" />
    </div>
  )
}
