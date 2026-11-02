import { useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import { IconCoins, IconPencil, IconPlus } from '@tabler/icons-react'
import {
  useAdminAccounts,
  useAdminAudit,
  useAdminCategories,
  useAdminDeleteEntry,
  useAdminEntries,
  useAdminSaveEntry,
  useAdminSetBalance,
  useAdminSummary,
  useAdminUsers,
} from '../api/adminHooks'
import { money } from '../api/client'
import type { Account, AdminUser, AuditRow, Entry } from '../api/types'
import { EntryEditor } from '../components/EntryForm'
import { EntryRow } from '../components/EntryRow'
import { Empty, ErrorLine, Field, Loading, Modal, Money, MonthPicker, Panel } from '../components/ui'
import { useToast } from '../components/useToast'
import { currentMonth, dayLabel, rm } from '../lib/format'

export function AdminPage() {
  const users = useAdminUsers()
  const [userId, setUserId] = useState<number | ''>('')
  const target = users.data?.find((u) => u.id === userId)

  return (
    <>
      <div className="page-head">
        <div>
          <h1>Keeper's Desk</h1>
          <div className="sub">Set the books straight. Every change is written down.</div>
        </div>
      </div>

      <Panel>
        {users.isLoading && <Loading rows={1} />}
        <ErrorLine error={users.error} />
        {users.data && (
          <Field label="Whose books?">
            <select value={userId} onChange={(e) => setUserId(e.target.value ? Number(e.target.value) : '')}>
              <option value="">Choose a user…</option>
              {users.data.map((u) => (
                <option key={u.id} value={u.id}>{u.username} · {rm(u.totalBalance)}</option>
              ))}
            </select>
          </Field>
        )}
      </Panel>

      {/* Keyed by user so an open form or the month can't carry over to someone else's books. */}
      {target && <Books key={target.id} user={target} />}
    </>
  )
}

function Books({ user }: { user: AdminUser }) {
  const [month, setMonth] = useState(currentMonth())
  const [editing, setEditing] = useState<Entry | 'new' | null>(null)
  const [balanceOf, setBalanceOf] = useState<Account | null>(null)
  const { toast, showToast } = useToast()

  const accounts = useAdminAccounts(user.id)
  const categories = useAdminCategories(user.id)
  const entries = useAdminEntries(user.id, month)
  const summary = useAdminSummary(user.id, month)
  const audit = useAdminAudit(user.id)
  const save = useAdminSaveEntry(user.id)
  const remove = useAdminDeleteEntry(user.id)

  const total = (accounts.data ?? []).reduce((sum, a) => sum + a.balance, 0)

  // Archived accounts stay in the lists: their history may be what needs fixing.
  const accountChoices = useMemo(() => (accounts.data ?? []).map((a) => (
    a.archived ? { ...a, name: `${a.name} (archived)` } : a
  )), [accounts.data])

  const byDay = useMemo(() => {
    const groups = new Map<string, Entry[]>()
    ;(entries.data ?? []).forEach((e) => groups.set(e.occurredOn, [...(groups.get(e.occurredOn) ?? []), e]))
    return [...groups.entries()]
  }, [entries.data])

  return (
    <div className="stack" style={{ marginTop: 14 }}>
      <div className="row wrap">
        <h2 style={{ margin: 0 }}>{user.username}'s books</h2>
        <MonthPicker month={month} onChange={setMonth} />
      </div>

      <div className="grid grid-3">
        <Panel><div className="stat"><span className="label">Money in</span><Money value={summary.data?.income ?? 0} className="value pos" /></div></Panel>
        <Panel><div className="stat"><span className="label">Money spent</span><Money value={summary.data?.expense ?? 0} className="value neg" /></div></Panel>
        <Panel><div className="stat"><span className="label">Total money</span><Money value={total} className="value gold" /></div></Panel>
      </div>

      <Panel title="Accounts">
        {accounts.isLoading && <Loading />}
        <ErrorLine error={accounts.error} />
        <div className="list">
          {accounts.data?.map((a) => (
            <div key={a.id} className="list-row">
              <div className="grow">
                <div className="title">{a.name}</div>
                <div className="small muted">
                  Opening {rm(a.openingBalance)}{a.archived ? ' · archived' : ''}
                </div>
              </div>
              <Money value={a.balance} />
              <button className="ghost" aria-label={`Set balance of ${a.name}`} onClick={() => setBalanceOf(a)}>
                <IconPencil size={16} />
              </button>
            </div>
          ))}
        </div>
      </Panel>

      <Panel title="Entries this month" action={(
        <button onClick={() => setEditing('new')}><IconPlus size={16} aria-hidden="true" /> Add correction</button>
      )}>
        {entries.isLoading && <Loading />}
        <ErrorLine error={entries.error ?? remove.error} />
        {entries.data?.length === 0 && <Empty icon={<IconCoins size={26} />}>Nothing logged this month.</Empty>}
        {byDay.map(([day, list]) => (
          <div key={day}>
            <div className="day-head">{dayLabel(day)}</div>
            <div className="list">
              {list.map((e) => (
                <EntryRow key={e.id} entry={e} onEdit={() => setEditing(e)}
                          onDelete={() => {
                            if (window.confirm(`Delete this entry from ${user.username}'s books?`)) {
                              remove.mutate(e.id, { onSuccess: () => showToast('Deleted') })
                            }
                          }} />
              ))}
            </div>
          </div>
        ))}
      </Panel>

      <Panel title="Change log">
        {audit.isLoading && <Loading rows={2} />}
        <ErrorLine error={audit.error} />
        {audit.data?.length === 0 && <p className="muted small">No admin changes to these books yet.</p>}
        <div className="list">
          {audit.data?.map((row) => (
            <div key={row.id} className="list-row">
              <div className="grow">
                <div className="title">{describe(row)}</div>
                <div className="small muted">{row.admin} · {new Date(row.at).toLocaleString()}</div>
              </div>
            </div>
          ))}
        </div>
      </Panel>

      {editing && (
        <EntryEditor
          entry={editing === 'new' ? undefined : editing}
          accounts={accountChoices}
          categories={categories.data ?? []}
          newHint="A correction earns no XP"
          newLabel="Add"
          saving={save.isPending}
          error={save.error}
          onClose={() => setEditing(null)}
          onSubmit={(body) => save.mutate(
            { ...body, id: editing === 'new' ? undefined : editing.id },
            {
              onSuccess: () => {
                setEditing(null)
                showToast('Saved')
              },
            },
          )}
        />
      )}
      {balanceOf && (
        <BalanceForm userId={user.id} account={balanceOf} onClose={() => setBalanceOf(null)}
                     onSaved={() => showToast('Balance set')} />
      )}
      {toast}
    </div>
  )
}

function BalanceForm({ userId, account, onClose, onSaved }: {
  userId: number
  account: Account
  onClose: () => void
  onSaved: () => void
}) {
  const save = useAdminSetBalance(userId)
  const [balance, setBalance] = useState(account.balance.toFixed(2))
  const value = Number(balance)
  const valid = balance.trim() !== '' && Number.isFinite(value)

  function submit(e: FormEvent) {
    e.preventDefault()
    if (!valid) return
    save.mutate({ accountId: account.id, balance: money(value) }, {
      onSuccess: () => {
        onSaved()
        onClose()
      },
    })
  }

  return (
    <Modal title={`Set balance of ${account.name}`} onClose={onClose}>
      <form onSubmit={submit} className="stack" style={{ gap: 12 }}>
        <p className="muted small" style={{ margin: 0 }}>
          Now {rm(account.balance)}. This moves the opening balance, so no entry is added and no month's
          money in or spent changes. To fix one wrong entry, edit that entry instead.
        </p>
        <Field label="Correct balance (RM)">
          <input inputMode="decimal" autoFocus value={balance} onChange={(e) => setBalance(e.target.value)} />
        </Field>
        <ErrorLine error={save.error} />
        <div className="row">
          <span className="muted small">{valid ? `Opening becomes ${rm(account.openingBalance + value - account.balance)}` : 'Enter an amount'}</span>
          <button type="submit" className="primary" disabled={!valid || save.isPending}>
            {save.isPending ? 'Saving…' : 'Set balance'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

function describe({ action, before, after, refId }: AuditRow): string {
  switch (action) {
    case 'ENTRY_CREATED': return `Added ${after?.type?.toLowerCase()} of ${rm(after?.amount ?? 0)}`
    case 'ENTRY_UPDATED': return `Entry #${refId}: ${rm(before?.amount ?? 0)} → ${rm(after?.amount ?? 0)}`
    case 'ENTRY_DELETED': return `Deleted ${before?.type?.toLowerCase()} of ${rm(before?.amount ?? 0)}`
    case 'BALANCE_SET': return `${after?.name}: ${rm(before?.balance ?? 0)} → ${rm(after?.balance ?? 0)}`
  }
}
