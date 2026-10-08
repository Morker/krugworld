import { useEffect, useState } from 'react'

type Status = { app: string; status: string; schemaVersion: string }

// Step 1 placeholder: proves browser -> Vite proxy -> Spring -> Postgres
// works end to end. Replaced by the real app shell and skins in step 3.
export default function App() {
  const [status, setStatus] = useState<Status | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    fetch('/api/status')
      .then((res) => {
        if (!res.ok) throw new Error(`HTTP ${res.status}`)
        return res.json() as Promise<Status>
      })
      .then(setStatus)
      .catch((e: Error) => setError(e.message))
  }, [])

  return (
    <main style={{ textAlign: 'center' }}>
      <h1>Coffold</h1>
      {status && (
        <p>
          API {status.status} · database schema v{status.schemaVersion}
        </p>
      )}
      {error && <p style={{ color: '#e5606a' }}>Can't reach the API ({error}). Is the backend running?</p>}
      {!status && !error && <p>Connecting…</p>}
    </main>
  )
}
