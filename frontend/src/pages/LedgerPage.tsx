import { useMemo, useState } from 'react'
import { IconCoins, IconPlus } from '@tabler/icons-react'
import { useAccounts, useCategories, useDeleteEntry, useEntries, useSummary } from '../api/hooks'
import type { Entry } from '../api/types'
import { EntryForm } from '../components/EntryForm'
import { EntryRow } from '../components/EntryRow'
import { Empty, ErrorLine, Loading, Money, MonthPicker, Panel } from '../components/ui'
import { useToast } from '../components/useToast'
import { currentMonth, dayLabel } from '../lib/format'

export function LedgerPage() {
  const [month, setMonth] = useState(currentMonth())
  const [accountId, setAccountId] = useState<number | ''>('')
  const [categoryId, setCategoryId] = useState<number | ''>('')
  const [editing, setEditing] = useState<Entry | 'new' | null>(null)
  const { toast, showToast } = useToast()

  const entries = useEntries(month)
  const summary = useSummary(month)
  const accounts = useAccounts().data ?? []
  const categories = useCategories().data ?? []
  const remove = useDeleteEntry()

  // Filtering happens here rather than in the API so switching a filter is instant.
  const shown = useMemo(() => (entries.data ?? []).filter((e) =>
    (!accountId || e.account.id === accountId || e.toAccount?.id === accountId)
    && (!categoryId || e.category?.id === categoryId)), [entries.data, accountId, categoryId])

  const byDay = useMemo(() => {
    const groups = new Map<string, Entry[]>()
    shown.forEach((e) => groups.set(e.occurredOn, [...(groups.get(e.occurredOn) ?? []), e]))
    return [...groups.entries()]
  }, [shown])

  return (
    <>
      <div className="page-head">
        <div>
          <h1>The Ledger</h1>
          <div className="sub">Every coin in and out</div>
        </div>
        <div className="row-start wrap">
          <MonthPicker month={month} onChange={setMonth} />
          <button className="primary" onClick={() => setEditing('new')}>
            <IconPlus size={18} aria-hidden="true" /> Log entry
          </button>
        </div>
      </div>

      <div className="grid grid-3" style={{ marginBottom: 14 }}>
        <Panel><div className="stat"><span className="label">Earned</span><Money value={summary.data?.income ?? 0} className="value pos" /></div></Panel>
        <Panel><div className="stat"><span className="label">Spent</span><Money value={summary.data?.expense ?? 0} className="value neg" /></div></Panel>
        <Panel><div className="stat"><span className="label">Net</span><Money value={summary.data?.net ?? 0} className="value gold" /></div></Panel>
      </div>

      <Panel>
        <div className="form-grid" style={{ marginBottom: 6 }}>
          <select aria-label="Filter by account" value={accountId} onChange={(e) => setAccountId(e.target.value ? Number(e.target.value) : '')}>
            <option value="">All accounts</option>
            {accounts.map((a) => <option key={a.id} value={a.id}>{a.name}</option>)}
          </select>
          <select aria-label="Filter by category" value={categoryId} onChange={(e) => setCategoryId(e.target.value ? Number(e.target.value) : '')}>
            <option value="">All categories</option>
            {categories.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
          </select>
        </div>

        {entries.isLoading && <Loading />}
        <ErrorLine error={entries.error ?? remove.error} />
        {entries.data && shown.length === 0 && (
          <Empty icon={<IconCoins size={26} />}>
            No loot {accountId || categoryId ? 'that matches' : 'logged this month'} yet.
            <div><button onClick={() => setEditing('new')}>Log an entry</button></div>
          </Empty>
        )}

        {byDay.map(([day, list]) => (
          <div key={day}>
            <div className="day-head">{dayLabel(day)}</div>
            <div className="list">
              {list.map((e) => (
                <EntryRow key={e.id} entry={e} onEdit={() => setEditing(e)}
                          onDelete={() => {
                            if (window.confirm('Delete this entry?')) remove.mutate(e.id)
                          }} />
              ))}
            </div>
          </div>
        ))}
      </Panel>

      {editing && (
        <EntryForm entry={editing === 'new' ? undefined : editing} onClose={() => setEditing(null)}
                   onSaved={(isNew) => showToast(isNew ? 'Logged · +5 XP' : 'Saved')} />
      )}
      {toast}
    </>
  )
}
