import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.tsx'

window.addEventListener('error', (e) => console.error('[CAPTURED]', (e as ErrorEvent).message))

createRoot(document.getElementById('root')!, {
  onUncaughtError: (error, errorInfo) =>
    console.error('[CAPTURED]', error, errorInfo?.componentStack),
}).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
