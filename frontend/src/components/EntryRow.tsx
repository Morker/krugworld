import { IconArrowsLeftRight, IconPencil, IconRepeat, IconTrash } from '@tabler/icons-react'
import type { Entry } from '../api/types'
import { signed } from '../lib/format'
import { CategoryIcon } from './icons'

export function EntryRow({ entry, onEdit, onDelete }: { entry: Entry; onEdit: () => void; onDelete: () => void }) {
  const transfer = entry.type === 'TRANSFER'
  const out = entry.type === 'EXPENSE'
  return (
    <div className="list-row">
      {transfer
        ? <span className="cat-icon" style={{ color: 'var(--mp)' }} aria-hidden="true"><IconArrowsLeftRight size={18} /></span>
        : <CategoryIcon icon={entry.category!.icon} color={entry.category!.color} />}
      <div className="grow">
        <div className="title">{entry.note || (transfer ? 'Transfer' : entry.category!.name)}</div>
        <div className="small muted row-start" style={{ gap: 6 }}>
          {transfer ? `${entry.account.name} → ${entry.toAccount!.name}` : `${entry.category!.name} · ${entry.account.name}`}
          {entry.recurring && <span className="chip"><IconRepeat size={12} aria-hidden="true" /> recurring</span>}
        </div>
      </div>
      <span className={`num ${entry.type === 'INCOME' ? 'pos' : ''}`} style={{ fontSize: '1.1em' }}>
        {transfer ? signed(entry.amount, false).slice(1) : signed(entry.amount, out)}
      </span>
      <button className="ghost" aria-label="Edit entry" onClick={onEdit}><IconPencil size={16} /></button>
      <button className="ghost danger" aria-label="Delete entry" onClick={onDelete}><IconTrash size={16} /></button>
    </div>
  )
}
