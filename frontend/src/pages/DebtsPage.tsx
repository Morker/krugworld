import { useState } from 'react'
import type { FormEvent } from 'react'
import {
  IconArrowBackUp,
  IconCalendarDue,
  IconCash,
  IconHeartHandshake,
  IconPencil,
  IconPlus,
  IconTrash,
} from '@tabler/icons-react'
import { money } from '../api/client'
import { useDebts, useDeleteDebt, useDeleteDebtPayment, usePayDebt, useSaveDebt, useSettleDebt } from '../api/hooks'
import type { Debt, DebtDirection } from '../api/types'
import { Bar, Empty, ErrorLine, Field, Loading, Modal, Money, Panel } from '../components/ui'
import { useToast } from '../components/useToast'
import { dayLabel, relativeDay, rm, today } from '../lib/format'

const SIDES: Record<DebtDirection, { title: string; empty: string; color: string; cls: string }> = {
  THEY_OWE: { title: 'Owed to you', empty: 'Nobody owes you a single coin.', color: 'var(--ok)', cls: 'pos' },
  I_OWE: { title: 'You owe', empty: 'Your slate is clean.', color: 'var(--hp)', cls: 'neg' },
}

/** Promises between people: who borrowed from you, and whom you borrowed from. */
export function DebtsPage() {
  const debts = useDebts()
  const [editing, setEditing] = useState<Debt | DebtDirection | null>(null)
  const [paying, setPaying] = useState<Debt | null>(null)
  const { toast, showToast } = useToast()

  const list = debts.data ?? []
  const open = list.filter((d) => !d.settledOn)
  const settled = list.filter((d) => d.settledOn)
  const total = (dir: DebtDirection) => open.filter((d) => d.direction === dir).reduce((s, d) => s + d.outstanding, 0)
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
      <ErrorLine error={debts.error} />

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
                    {side.map((d) => <DebtCard key={d.id} debt={d} onPay={() => setPaying(d)} onEdit={() => setEditing(d)} />)}
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
            {settled.map((d) => <SettledRow key={d.id} debt={d} onOpen={() => setPaying(d)} />)}
          </div>
        </details>
      )}

      {editing && (
        <DebtForm debt={typeof editing === 'string' ? undefined : editing}
                  direction={typeof editing === 'string' ? editing : editing.direction}
                  onClose={() => setEditing(null)} />
      )}
      {paying && (
        <PaymentDialog debt={list.find((d) => d.id === paying.id) ?? paying} onClose={() => setPaying(null)}
                       onCleared={() => showToast('Debt cleared!')} />
      )}
      {toast}
    </>
  )
}

function DebtCard({ debt, onPay, onEdit }: { debt: Debt; onPay: () => void; onEdit: () => void }) {
  const side = SIDES[debt.direction]
  return (
    <div className={`debt-card ${debt.overdue ? 'overdue' : ''}`}>
      <div className="row">
        <span className="row-start" style={{ minWidth: 0 }}>
          <span className="debt-face" style={{ borderColor: side.color }} aria-hidden="true">{debt.person.charAt(0).toUpperCase()}</span>
          <span style={{ minWidth: 0 }}>
            <span className="title ellipsis" style={{ display: 'block' }}>{debt.person}</span>
            <span className="small muted ellipsis" style={{ display: 'block' }}>{debt.note || `since ${dayLabel(debt.startedOn)}`}</span>
          </span>
        </span>
        <Money value={debt.outstanding} className={side.cls} />
      </div>
      <Bar value={debt.repaid} max={debt.amount} color={side.color} thin label={`${debt.person}: repaid so far`} />
      <div className="row small">
        <span className="muted">
          {debt.repaid > 0 ? <>paid back <Money value={debt.repaid} /> of <Money value={debt.amount} /></> : <>of <Money value={debt.amount} /></>}
        </span>
        {debt.dueOn && (
          <span className={debt.overdue ? 'neg' : 'muted'} style={{ whiteSpace: 'nowrap' }}>
            <IconCalendarDue size={14} aria-hidden="true" /> {debt.overdue ? 'overdue since ' : 'due '}{relativeDay(debt.dueOn)}
          </span>
        )}
      </div>
      <div className="row" style={{ marginTop: 6, justifyContent: 'flex-end', gap: 4 }}>
        <button className="ghost" aria-label="Edit" onClick={onEdit}><IconPencil size={16} /></button>
        <button onClick={onPay}><IconCash size={16} aria-hidden="true" /> {debt.direction === 'THEY_OWE' ? 'They paid' : 'I paid'}</button>
      </div>
    </div>
  )
}

function SettledRow({ debt, onOpen }: { debt: Debt; onOpen: () => void }) {
  const remove = useDeleteDebt()
  const letGo = debt.amount - debt.repaid
  return (
    <div className="list-row">
      <span className="debt-face small-face" aria-hidden="true">{debt.person.charAt(0).toUpperCase()}</span>
      <div className="grow">
        <div className="title">{debt.person}{debt.note && <span className="muted"> · {debt.note}</span>}</div>
        <div className="small muted">
          {debt.direction === 'THEY_OWE' ? 'They owed you' : 'You owed'} <Money value={debt.amount} /> · settled {dayLabel(debt.settledOn!)}
          {letGo > 0 && <> · <Money value={letGo} /> let go</>}
        </div>
      </div>
      <button className="ghost" onClick={onOpen}>Details</button>
      <button className="ghost danger" aria-label="Delete"
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

/** Record money paid back, see what's been paid, or let the rest go. */
function PaymentDialog({ debt, onClose, onCleared }: { debt: Debt; onClose: () => void; onCleared: () => void }) {
  const pay = usePayDebt()
  const undo = useDeleteDebtPayment()
  const settle = useSettleDebt()
  const [amount, setAmount] = useState(debt.outstanding > 0 ? String(debt.outstanding) : '')
  const [paidOn, setPaidOn] = useState(today())
  const [note, setNote] = useState('')
  const [problem, setProblem] = useState<string | null>(null)
  const isOpen = !debt.settledOn

  function submit(e: FormEvent) {
    e.preventDefault()
    if (!(Number(amount) > 0)) return setProblem('Enter an amount above zero.')
    if (Number(amount) > debt.outstanding) return setProblem(`Only ${rm(debt.outstanding)} is left.`)
    setProblem(null)
    pay.mutate({ id: debt.id, amount: money(amount), paidOn, note }, {
      onSuccess: (d) => {
        if (d.settledOn) {
          onCleared()
          onClose()
        } else {
          setAmount(String(d.outstanding))
          setNote('')
        }
      },
    })
  }

  return (
    <Modal title={`${debt.person} · ${debt.direction === 'THEY_OWE' ? 'owes you' : 'you owe'}`} onClose={onClose}>
      <div className="stack" style={{ gap: 12 }}>
        <div>
          <div className="row small"><span className="muted">Paid back</span><span><Money value={debt.repaid} /> of <Money value={debt.amount} /></span></div>
          <Bar value={debt.repaid} max={debt.amount} color={SIDES[debt.direction].color} label="Repaid so far" />
        </div>

        {isOpen && (
          <form onSubmit={submit} className="stack" style={{ gap: 10 }}>
            <div className="form-grid">
              <Field label="Paid back (RM)"><input inputMode="decimal" value={amount} autoFocus onChange={(e) => setAmount(e.target.value)} /></Field>
              <Field label="On"><input type="date" value={paidOn} onChange={(e) => setPaidOn(e.target.value)} /></Field>
            </div>
            <Field label="Note (optional)"><input value={note} maxLength={200} placeholder="DuitNow transfer" onChange={(e) => setNote(e.target.value)} /></Field>
            {problem && <p className="field-error" role="alert">{problem}</p>}
            <div className="row">
              <button type="button" className="ghost" disabled={settle.isPending}
                      onClick={() => window.confirm(`Let the remaining ${rm(debt.outstanding)} go? It moves to Settled.`)
                        && settle.mutate({ id: debt.id }, { onSuccess: onClose })}>
                Let the rest go
              </button>
              <button type="submit" className="primary" disabled={pay.isPending}>
                <IconCash size={16} aria-hidden="true" /> {pay.isPending ? 'Saving…' : 'Record payment'}
              </button>
            </div>
          </form>
        )}
        {!isOpen && debt.repaid < debt.amount && (
          <button className="ghost" onClick={() => settle.mutate({ id: debt.id, reopen: true }, { onSuccess: onClose })}>
            <IconArrowBackUp size={16} aria-hidden="true" /> Open it again
          </button>
        )}

        <ErrorLine error={pay.error ?? undo.error ?? settle.error} />

        {debt.payments.length > 0 && (
          <div>
            <div className="today-label">Repayments</div>
            <div className="list">
              {debt.payments.map((p) => (
                <div key={p.id} className="list-row" style={{ padding: '4px 2px' }}>
                  <span className="grow small">{dayLabel(p.paidOn)}{p.note && <span className="muted"> · {p.note}</span>}</span>
                  <Money value={p.amount} />
                  <button className="ghost danger" aria-label="Remove this repayment" disabled={undo.isPending}
                          onClick={() => undo.mutate({ id: debt.id, paymentId: p.id })}>
                    <IconTrash size={15} />
                  </button>
                </div>
              ))}
            </div>
          </div>
        )}
      </div>
    </Modal>
  )
}
