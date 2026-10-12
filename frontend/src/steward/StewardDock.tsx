import { useEffect } from 'react'
import { IconMaximize, IconRefresh, IconX } from '@tabler/icons-react'
import { Link } from 'react-router-dom'
import { useStewardStatus } from '../api/lifeHooks'
import { conversation } from './conversation'
import { Portrait } from './Portrait'
import { StewardChat } from './StewardChat'
import { useMood } from './useMood'

/**
 * Gaby, always one click away: a summon button in the corner, and a
 * panel that docks in the right-hand column on wide screens (the page
 * shrinks to make room), slides over the page on narrower ones, and goes
 * full-screen on phones.
 */
export function StewardDock({ open, onToggle }: { open: boolean; onToggle: (open: boolean) => void }) {
  const mood = useMood()
  const status = useStewardStatus().data

  useEffect(() => {
    if (!open) return
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onToggle(false)
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [open, onToggle])

  if (!open) {
    return (
      <button className="summon" onClick={() => onToggle(true)} aria-label="Talk to Gaby the Steward">
        <Portrait size={52} mood={mood} title="" />
        <span className="summon-label">Ask Gaby</span>
      </button>
    )
  }

  return (
    <aside className="dock" aria-label="Gaby the Steward">
      <header className="dock-head">
        <div className="dock-portrait"><Portrait size={72} mood={mood} /></div>
        <div className="grow">
          <h2 style={{ margin: 0 }}>Gaby</h2>
          <div className="small muted">
            {mood === 'thinking' ? 'pondering…' : status?.ready ? 'Steward of the realm' : 'resting'}
          </div>
        </div>
        <button className="ghost" aria-label="New conversation" title="New conversation" onClick={() => conversation.reset()}>
          <IconRefresh size={18} />
        </button>
        <Link to="/steward" className="btn ghost" aria-label="Open full page" title="Open full page" onClick={() => onToggle(false)}>
          <IconMaximize size={18} />
        </Link>
        <button className="ghost" aria-label="Close" title="Close (Esc)" onClick={() => onToggle(false)}>
          <IconX size={18} />
        </button>
      </header>
      <StewardChat compact />
    </aside>
  )
}
