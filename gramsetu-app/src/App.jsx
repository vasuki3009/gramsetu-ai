import { useEffect } from 'react'
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom'
import { LanguageProvider } from './context/LanguageContext'
import { warmUpServer } from './services/api'
import AppShell from './components/AppShell'
import HomeScreen from './screens/HomeScreen'
import VoiceScreen from './screens/VoiceScreen'
import CropScreen from './screens/CropScreen'
import SchemeScreen from './screens/SchemeScreen'
import CalendarScreen from './screens/CalendarScreen'
import WhatsAppScreen from './screens/WhatsAppScreen'

function AppContent() {
  // The free backend sleeps when idle: start waking it as soon as the app opens.
  useEffect(() => {
    warmUpServer()
  }, [])

  return (
    <AppShell>
      <Routes>
        <Route path="/"         element={<Navigate to="/home" replace />} />
        <Route path="/home"     element={<HomeScreen />} />
        <Route path="/voice"    element={<VoiceScreen />} />
        <Route path="/crop"     element={<CropScreen />} />
        <Route path="/schemes"  element={<SchemeScreen />} />
        <Route path="/calendar" element={<CalendarScreen />} />
        <Route path="/whatsapp" element={<WhatsAppScreen />} />
        <Route path="*"         element={<Navigate to="/home" replace />} />
      </Routes>
    </AppShell>
  )
}

export default function App() {
  return (
    <LanguageProvider>
      <Router>
        <AppContent />
      </Router>
    </LanguageProvider>
  )
}
