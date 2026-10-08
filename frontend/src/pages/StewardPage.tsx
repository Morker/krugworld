import { useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { IconSend, IconTrash } from '@tabler/icons-react'
import { useStewardChat, useStewardStatus } from '../api/lifeHooks'
import type { StewardTurn } from '../api/types'
import { Loading, Panel } from '../components/ui'
import { errorText } from '../lib/errors'

const KEY = 'krugworld.steward'
const GREETING: StewardTurn = {
  role: 'assistant',
  text: 'Well met, traveller. I keep thy ledger, thy almanac, thy feast and thy training. What wouldst thou have done?',
  subtitle: 'Hi! I can log spending, add events, track meals and workouts, and more. What do you need?',
}

// The transcript lives in this tab's sessionStorage: the server keeps no chat state.
function loadHistory(): StewardTurn[] {
  try {
    const saved = JSON.parse(sessionStorage.getItem(KEY) ?? 'null') as StewardTurn[] | null
    if (Array.isArray(saved) && saved.length) return saved
  } catch {
    // unreadable or blocked storage: start fresh
  }
  return [GREETING]
}

/** Talk to the Steward; it acts on your data through the same rules as the app. */
export function StewardPage() {
  const status = useStewardStatus()
  const chat = useStewardChat()
  const [history, setHistory] = useState<StewardTurn[]>(loadHistory)
  const [message, setMessage] = useState('')
  const end = useRef<HTMLDivElement>(null)

  useEffect(() => {
    try {
      sessionStorage.setItem(KEY, JSON.stringify(history.slice(-40)))
    } catch {
      // storage unavailable: the chat still works for this visit
    }
    end.current?.scrollIntoView({ behavior: 'smooth', block: 'end' })
  }, [history])

  function send(e: FormEvent) {
    e.preventDefault()
    const text = message.trim()
    if (!text || chat.isPending) return
    const before = history
    setHistory([...before, { role: 'user', text }])
    setMessage('')
    chat.mutate({ message: text, history: before.slice(1) }, {
      onSuccess: (r) => setHistory((h) => [...h, { role: 'assistant', text: r.reply, subtitle: r.subtitle }]),
      onError: (err) => setHistory((h) => [...h, { role: 'assistant', text: errorText(err) ?? 'Something went wrong.', error: true }]),
    })
  }

  const s = status.data
  const remaining = chat.data?.remaining ?? s?.remaining

  return (
    <>
      <div className="page-head">
        <div>
          <h1>The Steward</h1>
          <div className="sub">{s?.ready ? `Serving via ${s.provider}` : 'Your helper with real powers over the realm'}</div>
        </div>
        <button className="ghost" onClick={() => setHistory([GREETING])}><IconTrash size={16} /> New conversation</button>
      </div>

      {status.isLoading && <Loading />}
      {s && !s.ready && (
        <Panel>
          <p>The Steward has no key to serve with yet.</p>
          <p className="small muted">
            Whoever runs this server sets <code>ANTHROPIC_API_KEY</code> (Claude), or <code>OPENAI_API_KEY</code> with
            {' '}<code>KRUGWORLD_STEWARD_PROVIDER=openai</code>, and restarts the app.
          </p>
        </Panel>
      )}

      {s?.ready && (
        <Panel>
          <div className="chat" aria-live="polite">
            {history.map((t, i) => (
              <div key={i} className={`bubble ${t.role} ${t.error ? 'error' : ''}`}>
                <div>{t.text}</div>
                {t.subtitle && <div className="subtitle">{t.subtitle}</div>}
              </div>
            ))}
            {chat.isPending && <div className="bubble assistant muted">The steward ponders…</div>}
            <div ref={end} />
          </div>
          <form className="chat-input" onSubmit={send}>
            <input aria-label="Message the Steward" value={message} maxLength={1000} autoFocus
                   placeholder="spent 12 on nasi lemak · gym tomorrow 7am · had roti canai"
                   onChange={(e) => setMessage(e.target.value)} />
            <button type="submit" className="primary" disabled={chat.isPending || !message.trim()} aria-label="Send">
              <IconSend size={18} />
            </button>
          </form>
          {remaining !== undefined && <p className="small muted" style={{ margin: '8px 0 0' }}>{remaining} of {s.limit} messages left today</p>}
        </Panel>
      )}
    </>
  )
}
