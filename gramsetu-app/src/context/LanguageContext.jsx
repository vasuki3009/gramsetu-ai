import { createContext, useContext, useEffect, useState } from 'react'
import { STR } from '../strings'

const LanguageContext = createContext()

// Tamil-first: Tamil unless the visitor has switched. services/api.js reads the same
// 'appLanguage' key, so the choice reaches the backend as well.
const readStored = () => {
  try {
    return localStorage.getItem('appLanguage') === 'english' ? 'english' : 'tamil'
  } catch {
    return 'tamil'
  }
}

export function LanguageProvider({ children }) {
  const [language, setLanguageState] = useState(readStored)

  const setLanguage = (lang) => {
    setLanguageState(lang)
    try {
      localStorage.setItem('appLanguage', lang)
    } catch {
      /* storage unavailable (private mode): the choice just lasts for this visit */
    }
  }

  useEffect(() => {
    document.documentElement.lang = language === 'english' ? 'en' : 'ta'
    // The tab title follows the language too (index.html holds the Tamil one for the first paint).
    document.title = `GramSetu — ${STR[language].tagline}`
  }, [language])

  return (
    <LanguageContext.Provider value={{ language, setLanguage }}>
      {children}
    </LanguageContext.Provider>
  )
}

export function useLanguage() {
  return useContext(LanguageContext)
}
