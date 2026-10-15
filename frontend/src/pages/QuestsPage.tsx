import { useState } from 'react'
import type { FormEvent } from 'react'
import { IconCheck, IconFlag, IconMinus, IconPencil, IconPlus, IconTrash } from '@tabler/icons-react'
import { money } from '../api/client'
import { useContribute, useDeleteQuest, useQuests, useSaveQuest } from '../api/hooks'
import type { Quest } from '../api/types'
import { PlainIcon } from '../components/icons'
import { ICON_KEYS } from '../lib/icons'
import { Bar, Empty, ErrorLine, Field, Loading, Modal, Money, Panel } from '../components/ui'
import { useToast } from '../components/useToast'

/** Savings goals, presented as quests that pay XP when completed. */
export function QuestsPage() {
  const quests = useQuests()
  const [editing, setEditing] = useState<Quest | 'new' | null>(null)
  const { toast, showToast } = useToast()

  const active = (quests.data ?? []).filter((q) => !q.completedAt)
  const done = (quests.data ?? []).filter((q) => q.completedAt)

  return (
    <>
      <div className="page-head">
        <div>
          <h1>Quests</h1>
          <div className="sub">Save for something. Finish a quest for +200 XP.</div>
        </div>
        <button className="primary" onClick={() => setEditing('new')}><IconPlus size={18} aria-hidden="true" /> New quest</button>
      </div>

      {quests.isLoading && <Loading />}
      <ErrorLine error={quests.error} />

      {quests.data && active.length === 0 && (
        <Panel>
          <Empty icon={<IconFlag size={26} />}>
            No active quests. A new laptop, a trip, an emergency fund: name it and start saving.
            <div><button onClick={() => setEditing('new')}>Start a quest</button></div>
          </Empty>
        </Panel>
      )}

      <div className="grid grid-2">
        {active.map((q) => (
          <QuestCard key={q.id} quest={q} onEdit={() => setEditing(q)}
                     onCompleted={() => showToast(`Quest complete! +${q.xpReward} XP`)} />
        ))}
      </div>

      {done.length > 0 && (
        <div style={{ marginTop: 14 }}><Panel title="Completed" raised>
          <div className="list">
            {done.map((q) => (
              <div key={q.id} className="list-row">
                <span className="gold"><IconCheck size={18} aria-hidden="true" /></span>
                <span className="grow">{q.name}</span>
                <span className="num"><Money value={q.targetAmount} /></span>
              </div>
            ))}
          </div>
        </Panel></div>
      )}

      {editing && <QuestForm quest={editing === 'new' ? undefined : editing} onClose={() => setEditing(null)} />}
      {toast}
    </>
  )
}

function QuestCard({ quest, onEdit, onCompleted }: { quest: Quest; onEdit: () => void; onCompleted: () => void }) {
  const contribute = useContribute()
  const remove = useDeleteQuest()
  const [amount, setAmount] = useState('')
  const left = quest.targetAmount - quest.savedAmount

  function send(sign: 1 | -1) {
    const value = Number(amount)
    if (!(value > 0)) return
    contribute.mutate({ id: quest.id, amount: money(sign * value) }, {
      onSuccess: (q) => {
        setAmount('')
        if (q.completedAt) onCompleted()
      },
    })
  }

  return (
    <Panel>
      <div className="row">
        <h2 className="row-start" style={{ margin: 0 }}><PlainIcon icon={quest.icon} size={22} /> {quest.name}</h2>
        <span className="num gold">+{quest.xpReward} XP</span>
      </div>
      <Bar value={quest.savedAmount} max={quest.targetAmount} color="var(--ok)" label={`${quest.name} progress`} />
      <div className="row small muted num">
        <span><Money value={quest.savedAmount} /> of <Money value={quest.targetAmount} /></span>
        <span>{left > 0 ? <><Money value={left} /> to go</> : 'Ready!'}</span>
      </div>
      {quest.deadline && <div className="small muted">By {quest.deadline}</div>}
      <div className="row-start" style={{ marginTop: 10 }}>
        <input inputMode="decimal" placeholder="Amount" aria-label={`Amount for ${quest.name}`} value={amount}
               onChange={(e) => setAmount(e.target.value)} style={{ maxWidth: 140 }} />
        <button onClick={() => send(1)} disabled={contribute.isPending}><IconPlus size={16} /> Add</button>
        <button className="ghost" aria-label="Take back" onClick={() => send(-1)} disabled={contribute.isPending}><IconMinus size={16} /></button>
        <span style={{ flex: 1 }} />
        <button className="ghost" aria-label="Edit quest" onClick={onEdit}><IconPencil size={16} /></button>
        <button className="ghost danger" aria-label="Abandon quest"
                onClick={() => window.confirm(`Abandon "${quest.name}"?`) && remove.mutate(quest.id)}><IconTrash size={16} /></button>
      </div>
      <ErrorLine error={contribute.error ?? remove.error} />
    </Panel>
  )
}

function QuestForm({ quest, onClose }: { quest?: Quest; onClose: () => void }) {
  const save = useSaveQuest()
  const [name, setName] = useState(quest?.name ?? '')
  const [icon, setIcon] = useState(quest?.icon ?? 'savings')
  const [target, setTarget] = useState(quest ? String(quest.targetAmount) : '')
  const [deadline, setDeadline] = useState(quest?.deadline ?? '')
  const [problem, setProblem] = useState<string | null>(null)

  function submit(e: FormEvent) {
    e.preventDefault()
    if (!name.trim()) return setProblem('Name your quest.')
    if (!(Number(target) > 0)) return setProblem('Set a target above zero.')
    setProblem(null)
    save.mutate({ id: quest?.id, name, icon, targetAmount: money(target), deadline: deadline || null }, { onSuccess: onClose })
  }

  return (
    <Modal title={quest ? 'Edit quest' : 'New quest'} onClose={onClose}>
      <form onSubmit={submit} className="stack" style={{ gap: 12 }}>
        <Field label="What are you saving for?">
          <input value={name} maxLength={60} autoFocus placeholder="New laptop" onChange={(e) => setName(e.target.value)} />
        </Field>
        <div className="form-grid">
          <Field label="Target (RM)"><input inputMode="decimal" value={target} placeholder="3000" onChange={(e) => setTarget(e.target.value)} /></Field>
          <Field label="By (optional)"><input type="date" value={deadline} onChange={(e) => setDeadline(e.target.value)} /></Field>
        </div>
        <Field label="Icon">
          <div className="row-start wrap" style={{ gap: 6 }}>
            {ICON_KEYS.map((key) => (
              <button key={key} type="button" aria-label={key} aria-pressed={icon === key}
                      className={icon === key ? 'primary' : ''} style={{ padding: 6 }} onClick={() => setIcon(key)}>
                <PlainIcon icon={key} />
              </button>
            ))}
          </div>
        </Field>
        {problem && <p className="field-error" role="alert">{problem}</p>}
        <ErrorLine error={save.error} />
        <div className="row">
          <span />
          <button type="submit" className="primary" disabled={save.isPending}>{save.isPending ? 'Saving…' : quest ? 'Save' : 'Begin quest'}</button>
        </div>
      </form>
    </Modal>
  )
}
