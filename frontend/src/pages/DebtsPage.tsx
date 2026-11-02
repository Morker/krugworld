import { useState } from 'react'
import type { FormEvent } from 'react'
import {
  IconCalendarDue,
  IconHeartHandshake,
  IconPencil,
  IconPlus,
  IconSquare,
  IconSquareCheck,
  IconTrash,
} from '@tabler/icons-react'
import { money } from '../api/client'
import { useDebts, useDeleteDebt, useSaveDebt, useTickDebt } from '../api/hooks'
import type { Debt, DebtDirection } from '../api/types'
import { Empty, ErrorLine, Field, Loading, Modal, Money, Panel } from '../components/ui'
import { useToast } from '../components/useToast'
import { dayLabel, relativeDay, today } from '../lib/format'

const SIDES: Record<DebtDirection, { title: string; empty: string; color: string; cls: string }> = {
  THEY_OWE: { title: 'Owed to you', empty: 'Nobody owes you a single coin.', color: 'var(--ok)', cls: 'pos' },
  I_OWE: { title: 'You owe', empty: 'Your slate is clean.', color: 'var(--hp)', cls: 'neg' },
}

/** Promises between people: who borrowed from you, and whom you borrowed from. Tick them off once settled. */
export function DebtsPage() {
  const debts = useDebts()
  const tick = useTickDebt()
  const [editing, setEditing] = useState<Debt | DebtDirection | null>(null)
  const { toast, showToast } = useToast()

  function setSettled(debt: Debt, settled: boolean) {
    tick.mutate({ id: debt.id, settled }, {
      onSuccess: () => settled && showToast(`Settled with ${debt.person}!`),
    })
  }

  const list = debts.data ?? []
  const open = list.filter((d) => !d.settledOn)
  const settled = list.filter((d) => d.settledOn)
  const total = (dir: DebtDirection) => open.filter((d) => d.direction === dir).reduce((s, d) => s + d.amount, 0)
  const owedToMe = total('THEY_OWE')
  const iOwe = total('I_OWE')
  const net = owedToMe - iOwe

  return (
    <>
      <div className="page-head">
        <div>
          <h1>Debts</h1>
          <div className="sub">Who owes you, and whom you owe</div>
        </div>
        <button className="primary" onClick={() => setEditing('THEY_OWE')}>
          <IconPlus size={18} aria-hidden="true" /> Record a debt
        </button>
      </div>

      <div className="grid grid-3" style={{ marginBottom: 14 }}>
        <Panel><div className="stat"><span className="label">Owed to you</span><Money value={owedToMe} className="value pos" /></div></Panel>
        <Panel><div className="stat"><span className="label">You owe</span><Money value={iOwe} className="value neg" /></div></Panel>
        <Panel>
          <div className="stat">
            <span className="label">{net >= 0 ? 'In your favour' : 'Against you'}</span>
            <Money value={Math.abs(net)} className="value gold" />
          </div>
        </Panel>
      </div>

      {debts.isLoading && <Loading />}
      <ErrorLine error={debts.error ?? tick.error} />

      {debts.data && (
        <div className="grid grid-2" style={{ alignItems: 'start' }}>
          {(['THEY_OWE', 'I_OWE'] as const).map((dir) => {
            const side = open.filter((d) => d.direction === dir)
            return (
              <Panel key={dir} title={SIDES[dir].title}
                     action={<button className="ghost" onClick={() => setEditing(dir)}><IconPlus size={16} aria-hidden="true" /> Add</button>}>
                {side.length === 0 ? (
                  <Empty icon={<IconHeartHandshake size={26} />}>{SIDES[dir].empty}</Empty>
                ) : (
                  <div className="stack" style={{ gap: 8 }}>
                    {side.map((d) => (
                      <DebtCard key={d.id} debt={d} onTick={() => setSettled(d, true)} onEdit={() => setEditing(d)} />
                    ))}
                  </div>
                )}
              </Panel>
            )
          })}
        </div>
      )}

      {settled.length > 0 && (
        <details className="panel settled" style={{ marginTop: 14 }}>
          <summary><h2 style={{ display: 'inline', margin: 0 }}>Settled</h2> <span className="small muted">({settled.length})</span></summary>
          <div className="list" style={{ marginTop: 8 }}>
            {settled.map((d) => <SettledRow key={d.id} debt={d} onUntick={() => setSettled(d, false)} />)}
          </div>
        </details>
      )}

      {editing && (
        <DebtForm debt={typeof editing === 'string' ? undefined : editing}
                  direction={typeof editing === 'string' ? editing : editing.direction}
                  onClose={() => setEditing(null)} />
      )}
      {toast}
    </>
  )
}

function DebtCard({ debt, onTick, onEdit }: { debt: Debt; onTick: () => void; onEdit: () => void }) {
  const side = SIDES[debt.direction]
  return (
    <div className={`debt-card ${debt.overdue ? 'overdue' : ''}`}>
      <div className="row">
        <span className="row-start" style={{ minWidth: 0 }}>
          <button className="ghost debt-tick" aria-pressed={false} aria-label={`Tick off the debt with ${debt.person} as settled`}
                  onClick={onTick}>
            <IconSquare size={22} />
          </button>
          <span className="debt-face" style={{ borderColor: side.color }} aria-hidden="true">{debt.person.charAt(0).toUpperCase()}</span>
          <span style={{ minWidth: 0 }}>
            <span className="title ellipsis" style={{ display: 'block' }}>{debt.person}</span>
            <span className="small muted ellipsis" style={{ display: 'block' }}>{debt.note || `since ${dayLabel(debt.startedOn)}`}</span>
          </span>
        </span>
        <Money value={debt.amount} className={side.cls} />
      </div>
      <div className="row small">
        {debt.dueOn ? (
          <span className={debt.overdue ? 'neg' : 'muted'} style={{ whiteSpace: 'nowrap' }}>
            <IconCalendarDue size={14} aria-hidden="true" /> {debt.overdue ? 'overdue since ' : 'due '}{relativeDay(debt.dueOn)}
          </span>
        ) : <span className="muted">no due date</span>}
        <button className="ghost" aria-label={`Edit the debt with ${debt.person}`} onClick={onEdit}><IconPencil size={16} /></button>
      </div>
    </div>
  )
}

function SettledRow({ debt, onUntick }: { debt: Debt; onUntick: () => void }) {
  const remove = useDeleteDebt()
  return (
    <div className="list-row">
      <button className="ghost debt-tick" aria-pressed={true} aria-label={`Untick: ${debt.person} is owed again`} onClick={onUntick}>
        <IconSquareCheck size={22} color="var(--ok)" />
      </button>
      <div className="grow">
        <div className="title">{debt.person}{debt.note && <span className="muted"> · {debt.note}</span>}</div>
        <div className="small muted">
          {debt.direction === 'THEY_OWE' ? 'They owed you' : 'You owed'} <Money value={debt.amount} /> · settled {dayLabel(debt.settledOn!)}
        </div>
      </div>
      <button className="ghost danger" aria-label={`Delete the debt with ${debt.person}`}
              onClick={() => window.confirm(`Forget the debt with ${debt.person} entirely?`) && remove.mutate(debt.id)}>
        <IconTrash size={16} />
      </button>
    </div>
  )
}

function DebtForm({ debt, direction: initial, onClose }: { debt?: Debt; direction: DebtDirection; onClose: () => void }) {
  const save = useSaveDebt()
  const remove = useDeleteDebt()
  const [direction, setDirection] = useState<DebtDirection>(initial)
  const [person, setPerson] = useState(debt?.person ?? '')
  const [amount, setAmount] = useState(debt ? String(debt.amount) : '')
  const [note, setNote] = useState(debt?.note ?? '')
  const [startedOn, setStartedOn] = useState(debt?.startedOn ?? today())
  const [dueOn, setDueOn] = useState(debt?.dueOn ?? '')
  const [problem, setProblem] = useState<string | null>(null)

  function submit(e: FormEvent) {
    e.preventDefault()
    if (!person.trim()) return setProblem('Who is it with?')
    if (!(Number(amount) > 0)) return setProblem('Enter an amount above zero.')
    if (dueOn && dueOn < startedOn) return setProblem("The due date can't be before it started.")
    setProblem(null)
    save.mutate({ id: debt?.id, direction, person, amount: money(amount), note, startedOn, dueOn: dueOn || null },
      { onSuccess: onClose })
  }

  return (
    <Modal title={debt ? `Debt with ${debt.person}` : 'Record a debt'} onClose={onClose}>
      <form onSubmit={submit} className="stack" style={{ gap: 12 }}>
        <div className="segmented" role="group" aria-label="Which way">
          <button type="button" aria-pressed={direction === 'THEY_OWE'} onClick={() => setDirection('THEY_OWE')}>They owe me</button>
          <button type="button" aria-pressed={direction === 'I_OWE'} onClick={() => setDirection('I_OWE')}>I owe them</button>
        </div>
        <div className="form-grid">
          <Field label="Who"><input value={person} maxLength={80} autoFocus placeholder="Ali" onChange={(e) => setPerson(e.target.value)} /></Field>
          <Field label="Amount (RM)"><input inputMode="decimal" value={amount} placeholder="50" onChange={(e) => setAmount(e.target.value)} /></Field>
        </div>
        <Field label="What for? (optional)">
          <input value={note} maxLength={200} placeholder="Lunch at the mamak" onChange={(e) => setNote(e.target.value)} />
        </Field>
        <div className="form-grid">
          <Field label="Since"><input type="date" value={startedOn} onChange={(e) => setStartedOn(e.target.value)} /></Field>
          <Field label="Due by (optional)"><input type="date" value={dueOn} onChange={(e) => setDueOn(e.target.value)} /></Field>
        </div>
        {problem && <p className="field-error" role="alert">{problem}</p>}
        <ErrorLine error={save.error ?? remove.error} />
        <div className="row">
          {debt ? (
            <button type="button" className="ghost danger"
                    onClick={() => window.confirm(`Forget the debt with ${debt.person} entirely?`) && remove.mutate(debt.id, { onSuccess: onClose })}>
              <IconTrash size={16} aria-hidden="true" /> Delete
            </button>
          ) : <span />}
          <button type="submit" className="primary" disabled={save.isPending}>{save.isPending ? 'Saving…' : 'Save'}</button>
        </div>
      </form>
    </Modal>
  )
}
