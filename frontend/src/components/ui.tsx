import { useEffect } from 'react'
import type { ReactNode } from 'react'
import { IconChevronLeft, IconChevronRight, IconX } from '@tabler/icons-react'
import { errorText } from '../lib/errors'
import { monthLabel, percent, rm, shiftMonth } from '../lib/format'

export function Panel({ title, action, children, raised, className = '' }: {
  title?: ReactNode
  action?: ReactNode
  children: ReactNode
  raised?: boolean
  className?: string
}) {
  return (
    <section className={`panel ${raised ? 'raised' : ''} ${className}`}>
      {(title || action) && (
        <div className="row" style={{ marginBottom: 8 }}>
          {title ? <h2 style={{ margin: 0 }}>{title}</h2> : <span />}
          {action}
        </div>
      )}
      {children}
    </section>
  )
}

/** A notched game-style bar. `value`/`max` are clamped to 0–100%. */
export function Bar({ value, max, color, thin, label }: {
  value: number
  max: number
  color: string
  thin?: boolean
  label: string
}) {
  const pct = percent(value, max)
  return (
    <div className={`bar ${thin ? 'thin' : ''}`} role="progressbar" aria-label={label}
         aria-valuenow={Math.round(pct)} aria-valuemin={0} aria-valuemax={100}>
      <div style={{ width: `${pct}%`, background: color }} />
    </div>
  )
}

export function Money({ value, className = '' }: { value: number; className?: string }) {
  return <span className={`num ${className}`}>{rm(value)}</span>
}

export function MonthPicker({ month, onChange }: { month: string; onChange: (m: string) => void }) {
  return (
    <div className="row-start">
      <button className="ghost" aria-label="Previous month" onClick={() => onChange(shiftMonth(month, -1))}>
        <IconChevronLeft size={18} />
      </button>
      <span className="num" style={{ minWidth: 130, textAlign: 'center' }}>{monthLabel(month)}</span>
      <button className="ghost" aria-label="Next month" onClick={() => onChange(shiftMonth(month, 1))}>
        <IconChevronRight size={18} />
      </button>
    </div>
  )
}

export function Modal({ title, onClose, children }: { title: string; onClose: () => void; children: ReactNode }) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose()
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])

  return (
    <div className="backdrop" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div className="panel modal" role="dialog" aria-modal="true" aria-label={title}>
        <div className="row" style={{ marginBottom: 12 }}>
          <h2 style={{ margin: 0 }}>{title}</h2>
          <button className="ghost" aria-label="Close" onClick={onClose}><IconX size={18} /></button>
        </div>
        {children}
      </div>
    </div>
  )
}

export function Field({ label, error, children }: { label: string; error?: string; children: ReactNode }) {
  return (
    <label className="field">
      {label}
      {children}
      {error && <span className="field-error">{error}</span>}
    </label>
  )
}

export function ErrorLine({ error }: { error: unknown }) {
  const text = errorText(error)
  return text ? <p className="field-error" role="alert">{text}</p> : null
}

export function Loading() {
  return <p className="muted">Loading…</p>
}

export function Empty({ children }: { children: ReactNode }) {
  return <div className="empty">{children}</div>
}
