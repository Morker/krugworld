import { useState } from 'react'
import type { FormEvent } from 'react'
import { money } from '../api/client'
import { useAccounts, useCategories, useSaveEntry } from '../api/hooks'
import type { Account, Category, Entry, EntryRequest, EntryType } from '../api/types'
import { today } from '../lib/format'
import { ErrorLine, Field, Modal } from './ui'

const TYPES: { id: EntryType; label: string }[] = [
  { id: 'EXPENSE', label: 'Spent' },
  { id: 'INCOME', label: 'Earned' },
  { id: 'TRANSFER', label: 'Moved' },
]

/** Add or edit one ledger entry. Logging a new one earns XP, so it says so. */
export function EntryForm({ entry, onClose, onSaved }: {
  entry?: Entry
  onClose: () => void
  onSaved?: (isNew: boolean) => void
}) {
  const accounts = (useAccounts().data ?? []).filter((a) => !a.archived)
  const categories = (useCategories().data ?? []).filter((c) => !c.archived)
  const save = useSaveEntry()

  return (
    <EntryEditor
      entry={entry}
      accounts={accounts}
      categories={categories}
      newHint="+5 XP for logging"
      newLabel="Log it"
      saving={save.isPending}
      error={save.error}
      onClose={onClose}
      onSubmit={(body) => save.mutate(
        { ...body, id: entry?.id },
        {
          onSuccess: () => {
            onSaved?.(!entry)
            onClose()
          },
        },
      )}
    />
  )
}

/** The form itself. It only knows the lists it's handed, so the admin page can use it on someone else's books. */
export function EntryEditor({ entry, accounts, categories, newHint, newLabel, saving, error, onClose, onSubmit }: {
  entry?: Entry
  accounts: Account[]
  categories: Category[]
  newHint: string
  newLabel: string
  saving: boolean
  error: unknown
  onClose: () => void
  onSubmit: (body: EntryRequest) => void
}) {
  const [type, setType] = useState<EntryType>(entry?.type ?? 'EXPENSE')
  const [amount, setAmount] = useState(entry ? String(entry.amount) : '')
  const [occurredOn, setOccurredOn] = useState(entry?.occurredOn ?? today())
  const [accountId, setAccountId] = useState<number | ''>(entry?.account.id ?? '')
  const [toAccountId, setToAccountId] = useState<number | ''>(entry?.toAccount?.id ?? '')
  const [categoryId, setCategoryId] = useState<number | ''>(entry?.category?.id ?? '')
  const [note, setNote] = useState(entry?.note ?? '')
  const [problem, setProblem] = useState<string | null>(null)

  const kind = type === 'INCOME' ? 'INCOME' : 'EXPENSE'
  const choices = categories.filter((c) => c.kind === kind)
  const account = accountId || accounts[0]?.id
  const category = choices.some((c) => c.id === categoryId) ? categoryId : choices[0]?.id

  function submit(e: FormEvent) {
    e.preventDefault()
    const value = Number(amount)
    if (!amount || !Number.isFinite(value) || value <= 0) {
      setProblem('Enter an amount above zero.')
      return
    }
    if (!account) {
      setProblem('Add an account first (Accounts page).')
      return
    }
    if (type === 'TRANSFER' && (!toAccountId || toAccountId === account)) {
      setProblem('Pick a different account to move the money to.')
      return
    }
    setProblem(null)
    onSubmit({
      type,
      amount: money(value),
      accountId: Number(account),
      toAccountId: type === 'TRANSFER' ? Number(toAccountId) : null,
      categoryId: type === 'TRANSFER' ? null : Number(category),
      occurredOn,
      note,
    })
  }

  return (
    <Modal title={entry ? 'Edit entry' : 'Log an entry'} onClose={onClose}>
      <form onSubmit={submit} className="stack" style={{ gap: 12 }}>
        <div className="segmented" role="group" aria-label="Entry type">
          {TYPES.map((t) => (
            <button key={t.id} type="button" aria-pressed={type === t.id} onClick={() => setType(t.id)}>
              {t.label}
            </button>
          ))}
        </div>

        <div className="form-grid">
          <Field label="Amount (RM)">
            <input inputMode="decimal" autoFocus value={amount} placeholder="12.50"
                   onChange={(e) => setAmount(e.target.value)} />
          </Field>
          <Field label="Date">
            <input type="date" value={occurredOn} onChange={(e) => setOccurredOn(e.target.value)} required />
          </Field>
        </div>

        <div className="form-grid">
          <Field label={type === 'TRANSFER' ? 'From account' : 'Account'}>
            <select value={account ?? ''} onChange={(e) => setAccountId(Number(e.target.value))}>
              {accounts.map((a) => <option key={a.id} value={a.id}>{a.name}</option>)}
            </select>
          </Field>
          {type === 'TRANSFER' ? (
            <Field label="To account">
              <select value={toAccountId} onChange={(e) => setToAccountId(Number(e.target.value))}>
                <option value="">Choose…</option>
                {accounts.filter((a) => a.id !== account).map((a) => (
                  <option key={a.id} value={a.id}>{a.name}</option>
                ))}
              </select>
            </Field>
          ) : (
            <Field label="Category">
              <select value={category ?? ''} onChange={(e) => setCategoryId(Number(e.target.value))}>
                {choices.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
              </select>
            </Field>
          )}
        </div>

        <Field label="Note">
          <input value={note} maxLength={200} placeholder="Nasi lemak at the mamak"
                 onChange={(e) => setNote(e.target.value)} />
        </Field>

        {problem && <p className="field-error" role="alert">{problem}</p>}
        <ErrorLine error={error} />

        <div className="row">
          <span className="muted small">{entry ? '' : newHint}</span>
          <button type="submit" className="primary" disabled={saving}>
            {saving ? 'Saving…' : entry ? 'Save' : newLabel}
          </button>
        </div>
      </form>
    </Modal>
  )
}
