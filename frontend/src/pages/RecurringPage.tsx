import { useState } from 'react'
import type { FormEvent } from 'react'
import { IconPencil, IconPlayerPause, IconPlayerPlay, IconPlus, IconRepeat, IconTrash } from '@tabler/icons-react'
import { money } from '../api/client'
import { useAccounts, useCategories, useDeleteRule, useRules, useSaveRule } from '../api/hooks'
import type { Rule } from '../api/types'
import { CategoryIcon } from '../components/icons'
import { Empty, ErrorLine, Field, Loading, Modal, Money, Panel } from '../components/ui'
import { dayLabel, today } from '../lib/format'

/** Salary, rent, subscriptions: logged automatically on their day each month. */
export function RecurringPage() {
  const rules = useRules()
  const save = useSaveRule()
  const remove = useDeleteRule()
  const [editing, setEditing] = useState<Rule | 'new' | null>(null)

  const list = rules.data ?? []
  const monthlyIn = list.filter((r) => r.active && r.type === 'INCOME').reduce((s, r) => s + r.amount, 0)
  const monthlyOut = list.filter((r) => r.active && r.type === 'EXPENSE').reduce((s, r) => s + r.amount, 0)

  function toggle(r: Rule) {
    save.mutate({
      id: r.id, type: r.type, amount: money(r.amount), accountId: r.account.id, categoryId: r.category.id,
      dayOfMonth: r.dayOfMonth, note: r.note, active: !r.active,
    })
  }

  return (
    <>
      <div className="page-head">
        <div>
          <h1>Recurring</h1>
          <div className="sub">Logged for you on the day, every month</div>
        </div>
        <button className="primary" onClick={() => setEditing('new')}><IconPlus size={18} aria-hidden="true" /> Add recurring</button>
      </div>

      <div className="grid grid-2" style={{ marginBottom: 14 }}>
        <Panel><div className="stat"><span className="label">Comes in each month</span><Money value={monthlyIn} className="value pos" /></div></Panel>
        <Panel><div className="stat"><span className="label">Goes out each month</span><Money value={monthlyOut} className="value neg" /></div></Panel>
      </div>

      <Panel>
        {rules.isLoading && <Loading />}
        <ErrorLine error={rules.error ?? save.error ?? remove.error} />
        {rules.data && list.length === 0 && (
          <Empty icon={<IconRepeat size={26} />}>
            Nothing recurring yet. Add your salary, rent or subscriptions once, and they log themselves.
            <div><button onClick={() => setEditing('new')}>Add recurring</button></div>
          </Empty>
        )}
        <div className="list">
          {list.map((r) => (
            <div key={r.id} className="list-row" style={{ opacity: r.active ? 1 : 0.55 }}>
              <CategoryIcon icon={r.category.icon} color={r.category.color} />
              <div className="grow">
                <div className="title">{r.note || r.category.name}</div>
                <div className="small muted">
                  Day {r.dayOfMonth} · {r.account.name} · {r.active ? `next ${dayLabel(r.nextRunOn)}` : 'paused'}
                </div>
              </div>
              <span className={`num ${r.type === 'INCOME' ? 'pos' : ''}`}>
                {r.type === 'INCOME' ? '+' : '−'}<Money value={r.amount} />
              </span>
              <button className="ghost" aria-label={r.active ? 'Pause' : 'Resume'} onClick={() => toggle(r)}>
                {r.active ? <IconPlayerPause size={16} /> : <IconPlayerPlay size={16} />}
              </button>
              <button className="ghost" aria-label="Edit" onClick={() => setEditing(r)}><IconPencil size={16} /></button>
              <button className="ghost danger" aria-label="Delete"
                      onClick={() => window.confirm('Stop this recurring entry? Entries it already logged stay.') && remove.mutate(r.id)}>
                <IconTrash size={16} />
              </button>
            </div>
          ))}
        </div>
      </Panel>

      {editing && <RuleForm rule={editing === 'new' ? undefined : editing} onClose={() => setEditing(null)} />}
    </>
  )
}

function RuleForm({ rule, onClose }: { rule?: Rule; onClose: () => void }) {
  const accounts = (useAccounts().data ?? []).filter((a) => !a.archived)
  const categories = (useCategories().data ?? []).filter((c) => !c.archived)
  const save = useSaveRule()

  const [type, setType] = useState<'INCOME' | 'EXPENSE'>(rule?.type ?? 'EXPENSE')
  const [amount, setAmount] = useState(rule ? String(rule.amount) : '')
  const [day, setDay] = useState(() => String(rule?.dayOfMonth ?? new Date().getDate()))
  const [startOn, setStartOn] = useState(today())
  const [accountId, setAccountId] = useState<number | ''>(rule?.account.id ?? '')
  const [categoryId, setCategoryId] = useState<number | ''>(rule?.category.id ?? '')
  const [note, setNote] = useState(rule?.note ?? '')
  const [problem, setProblem] = useState<string | null>(null)

  const choices = categories.filter((c) => c.kind === type)
  const account = accountId || accounts[0]?.id
  const category = choices.some((c) => c.id === categoryId) ? categoryId : choices[0]?.id

  function submit(e: FormEvent) {
    e.preventDefault()
    const d = Number(day)
    if (!(Number(amount) > 0)) return setProblem('Enter an amount above zero.')
    if (!Number.isInteger(d) || d < 1 || d > 31) return setProblem('Day must be 1 to 31.')
    if (!account || !category) return setProblem('Pick an account and a category.')
    setProblem(null)
    save.mutate({
      id: rule?.id, type, amount: money(amount), accountId: Number(account), categoryId: Number(category),
      dayOfMonth: d, startOn: rule ? undefined : startOn, note, active: rule?.active ?? true,
    }, { onSuccess: onClose })
  }

  return (
    <Modal title={rule ? 'Edit recurring' : 'Add recurring'} onClose={onClose}>
      <form onSubmit={submit} className="stack" style={{ gap: 12 }}>
        <div className="segmented" role="group" aria-label="Type">
          <button type="button" aria-pressed={type === 'EXPENSE'} onClick={() => setType('EXPENSE')}>Goes out</button>
          <button type="button" aria-pressed={type === 'INCOME'} onClick={() => setType('INCOME')}>Comes in</button>
        </div>
        <Field label="What is it?">
          <input value={note} maxLength={200} autoFocus placeholder="Rent" onChange={(e) => setNote(e.target.value)} />
        </Field>
        <div className="form-grid">
          <Field label="Amount (RM)"><input inputMode="decimal" value={amount} placeholder="900" onChange={(e) => setAmount(e.target.value)} /></Field>
          <Field label="Day of month"><input inputMode="numeric" value={day} onChange={(e) => setDay(e.target.value)} /></Field>
          {!rule && <Field label="Starting"><input type="date" value={startOn} onChange={(e) => setStartOn(e.target.value)} /></Field>}
        </div>
        <div className="form-grid">
          <Field label="Account">
            <select value={account ?? ''} onChange={(e) => setAccountId(Number(e.target.value))}>
              {accounts.map((a) => <option key={a.id} value={a.id}>{a.name}</option>)}
            </select>
          </Field>
          <Field label="Category">
            <select value={category ?? ''} onChange={(e) => setCategoryId(Number(e.target.value))}>
              {choices.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
            </select>
          </Field>
        </div>
        <p className="small muted" style={{ margin: 0 }}>Days 29 to 31 fall on the last day of shorter months.</p>
        {problem && <p className="field-error" role="alert">{problem}</p>}
        <ErrorLine error={save.error} />
        <div className="row"><span /><button type="submit" className="primary" disabled={save.isPending}>{save.isPending ? 'Saving…' : 'Save'}</button></div>
      </form>
    </Modal>
  )
}
