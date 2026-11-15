import { useNavigate } from 'react-router-dom'
import { useLanguage } from '../context/LanguageContext'

export default function LanguageSelectScreen() {
  const navigate = useNavigate()
  const { setLanguage } = useLanguage()

  const choose = (lang) => {
    setLanguage(lang)
    navigate('/home')
  }

  return (
    <div
      className="min-h-screen flex flex-col items-center justify-center px-6"
      style={{ backgroundColor: '#0A3D2B' }}
    >
      {/* Title */}
      <div className="text-center mb-12">
        <h1
          className="text-white font-bold"
          style={{ fontFamily: 'Georgia, serif', fontSize: '52px' }}
        >
          GramSetu
        </h1>
        <p
          className="mt-2 italic"
          style={{ color: '#86EFAC', fontSize: '18px' }}
        >
          உழவனுக்கு AI துணை
        </p>
      </div>

      {/* Prompt */}
      <div className="text-center mb-8">
        <p style={{ color: '#D1FAE5', fontSize: '16px', fontWeight: '600' }}>
          மொழியை தேர்வு செய்யவும்
        </p>
        <p style={{ color: '#6EE7B7', fontSize: '14px', marginTop: '4px' }}>
          Select your language
        </p>
      </div>

      {/* Buttons */}
      <div className="flex gap-4 w-full max-w-sm">
        <button
          onClick={() => choose('tamil')}
          className="flex-1 flex flex-col items-center justify-center rounded-control py-6 px-4 transition-all duration-150 active:scale-95"
          style={{ border: '2px solid #4ADE80', backgroundColor: 'transparent' }}
        >
          <span style={{ fontSize: '36px' }}>🌿</span>
          <span className="mt-3 font-bold text-white" style={{ fontSize: '20px' }}>தமிழ்</span>
          <span className="mt-1" style={{ color: '#86EFAC', fontSize: '13px' }}>தமிழில் தொடரவும்</span>
        </button>

        <button
          onClick={() => choose('english')}
          className="flex-1 flex flex-col items-center justify-center rounded-control py-6 px-4 transition-all duration-150 active:scale-95"
          style={{ border: '2px solid #4ADE80', backgroundColor: 'transparent' }}
        >
          <span style={{ fontSize: '36px' }}>🌐</span>
          <span className="mt-3 font-bold text-white" style={{ fontSize: '20px' }}>English</span>
          <span className="mt-1" style={{ color: '#86EFAC', fontSize: '13px' }}>Continue in English</span>
        </button>
      </div>
    </div>
  )
}
