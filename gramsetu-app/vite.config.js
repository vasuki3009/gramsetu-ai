import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig(({ command, mode }) => {
  // Fail the production build early (e.g. a Vercel deploy) when the backend URL is missing,
  // instead of shipping a site that cannot reach its API. Skipped in dev, preview and test modes.
  if (command === 'build' && mode === 'production') {
    const env = loadEnv(mode, process.cwd(), 'VITE_')
    if (!env.VITE_API_BASE_URL) {
      throw new Error(
        '\n[gramsetu] VITE_API_BASE_URL is not set, refusing to build for production.\n' +
        'Set it in your hosting environment (Vercel → Project Settings → Environment Variables)\n' +
        'or in gramsetu-app/.env.production, e.g. VITE_API_BASE_URL=https://your-backend.example.com\n'
      )
    }
  }

  return {
    plugins: [react()],
    server: {
      port: 5173,
    },
  }
})
