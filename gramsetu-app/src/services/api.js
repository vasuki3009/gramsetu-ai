import axios from 'axios'

// Backend base URL comes only from VITE_API_BASE_URL (set at build time, e.g. in Vercel →
// Project Settings → Environment Variables). The localhost fallback exists for `npm run dev`
// only; production builds fail loudly instead of silently calling localhost.
function resolveBaseUrl() {
  const configured = import.meta.env.VITE_API_BASE_URL
  if (configured) return configured.replace(/\/+$/, '')
  if (import.meta.env.DEV) return 'http://localhost:8081'
  throw new Error(
    'VITE_API_BASE_URL is not set. Define it in your hosting environment ' +
    '(for example https://your-backend.example.com) and redeploy.'
  )
}

const API = axios.create({
  baseURL: resolveBaseUrl(),
})

const getLang = () => localStorage.getItem('appLanguage') || 'tamil'

// Friendly, bilingual text for the API failures a farmer can actually act on.
//  429 - rate limited by the backend (Retry-After is exposed through CORS)
//  413 - file too large
//  400 - the backend explains itself in `tamilAnswer` (e.g. question too long)
// Anything else returns `fallback`, the screen's generic connection error.
export const apiErrorMessage = (err, fallback) => {
  const english = getLang() === 'english'
  const status = err?.response?.status

  if (status === 429) {
    const seconds = parseInt(err.response.headers?.['retry-after'], 10)
    if (Number.isFinite(seconds) && seconds > 0) {
      return english
        ? `Too many requests. Please wait ${seconds} seconds and try again.`
        : `கோரிக்கைகள் அதிகமாக உள்ளன. ${seconds} விநாடிகள் காத்திருந்து மீண்டும் முயற்சிக்கவும்.`
    }
    return english
      ? 'Too many requests. Please wait a moment and try again.'
      : 'கோரிக்கைகள் அதிகமாக உள்ளன. சிறிது நேரம் காத்திருந்து மீண்டும் முயற்சிக்கவும்.'
  }
  if (status === 413) {
    return english
      ? 'The file is too large. Please send a smaller one.'
      : 'கோப்பு மிகப் பெரியது. சிறிய கோப்பை அனுப்பவும்.'
  }
  if (status === 400 && typeof err.response.data?.tamilAnswer === 'string' && err.response.data.tamilAnswer) {
    return err.response.data.tamilAnswer
  }
  return fallback
}

// Fire-and-forget request that starts waking a sleeping free-tier backend while the farmer is
// still reading the home screen. The response is never read (no-cors) and every error is ignored.
export const warmUpServer = () => {
  try {
    fetch(`${API.defaults.baseURL}/api/whatsapp/health`, { mode: 'no-cors', cache: 'no-store' }).catch(() => {})
  } catch {
    /* nothing to do: warming up is best-effort */
  }
}

export const askQuestion = (question, location = 'Hosur') =>
  API.post('/api/v1/farmer/ask', { question, location, language: getLang() })

export const sendVoice = (audioBlob, location = 'Hosur', mimeType = 'audio/webm') => {
  const ext = mimeType.includes('ogg') ? 'ogg' : 'webm'
  const form = new FormData()
  form.append('audio', audioBlob, `recording.${ext}`)
  form.append('location', location)
  form.append('language', getLang())
  return API.post('/api/v1/farmer/voice', form)
}

export const analyzeCrop = (imageFile) => {
  const form = new FormData()
  form.append('image', imageFile)
  form.append('language', getLang())
  return API.post('/api/v1/crop/analyze', form)
}

export const findSchemes = (question) =>
  API.post('/api/v1/schemes/find', { question, language: getLang() })


export const getAllSchemes = () =>
  API.get('/api/v1/schemes/all')

export const getCropCalendar = () =>
  API.get('/api/v1/crops/calendar')

export const getPersonalizedCalendar = (data) =>
  API.post('/api/v1/crops/calendar/plan', { ...data, language: getLang() })

