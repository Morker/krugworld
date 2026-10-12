import { useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { IconSend } from '@tabler/icons-react'
import { useStewardChat, useStewardStatus } from '../api/lifeHooks'
import { errorText } from '../lib/errors'
import { conversation, useConversation } from './conversation'
import { Portrait } from './Portrait'

const SUGGESTIONS = ['spent 12 on nasi lemak', 'apa ada dalam calendar minggu ni?', 'had roti canai for breakfast', 'berapa lagi budget makan?']

/** The conversation and its input box: used by the docked panel and the Steward page alike. */
export function StewardChat({ compact = false }: { compact?: boolean }) {
  const status = useStewardStatus()
  const chat = useStewardChat()
  const { turns, pending } = useConversation()
  const [message, setMessage] = useState('')
  const end = useRef<HTMLDivElement>(null)

  useEffect(() => {
    end.current?.scrollIntoView({ behavior: 'smooth', block: 'end' })
  }, [turns.length, pending])

  function send(text: string) {
    const clean = text.trim()
    if (!clean || pending) return
    conversation.ask(clean)
    setMessage('')
    chat.mutate({ message: clean, history: conversation.historyBeforeLast() }, {
      onSuccess: (r) => conversation.answer({ role: 'assistant', text: r.reply, subtitle: r.subtitle }),
      onError: (err) => conversation.answer({ role: 'assistant', text: errorText(err) ?? 'Something went wrong.', error: true }),
    })
  }

  function submit(e: FormEvent) {
    e.preventDefault()
    send(message)
  }

  const s = status.data
  if (s && !s.ready) {
    return (
      <div className="steward-asleep">
        <Portrait size={compact ? 72 : 96} />
        <p><b>Gaby is resting.</b> She has no key to serve with yet.</p>
        <p className="small muted">
          Whoever runs this server sets <code>OPENAI_API_KEY</code> and restarts the app
          (or <code>ANTHROPIC_API_KEY</code> with <code>KRUGWORLD_STEWARD_PROVIDER=anthropic</code> for Claude).
        </p>
      </div>
    )
  }

  const remaining = chat.data?.remaining ?? s?.remaining
  return (
    <div className={`steward-chat ${compact ? 'compact' : ''}`}>
      <div className="chat" aria-live="polite">
        {turns.map((t, i) => (
          <div key={i} className={`chat-row ${t.role}`}>
            {t.role === 'assistant' && <Portrait size={28} title="" />}
            <div className={`bubble ${t.role} ${t.error ? 'error' : ''}`}>
              <div>{t.text}</div>
              {t.subtitle && <div className="subtitle">{t.subtitle}</div>}
            </div>
          </div>
        ))}
        {pending && (
          <div className="chat-row assistant">
            <Portrait size={28} mood="thinking" title="" />
            <div className="bubble assistant thinking-dots" aria-label="Gaby is thinking"><span /><span /><span /></div>
          </div>
        )}
        {turns.length === 1 && !pending && (
          <div className="suggestions">
            {SUGGESTIONS.map((q) => <button key={q} className="chip" onClick={() => send(q)}>{q}</button>)}
          </div>
        )}
        <div ref={end} />
      </div>
      <form className="chat-input" onSubmit={submit}>
        <input aria-label="Message Gaby" value={message} maxLength={1000} autoFocus={!compact}
               placeholder="Ask Gaby anything… (English or BM)" onChange={(e) => setMessage(e.target.value)} />
        <button type="submit" className="primary" disabled={pending || !message.trim()} aria-label="Send">
          <IconSend size={18} />
        </button>
      </form>
      {remaining !== undefined && s && (
        <p className="small muted" style={{ margin: '6px 0 0' }}>{remaining} of {s.limit} messages left today</p>
      )}
    </div>
  )
}
