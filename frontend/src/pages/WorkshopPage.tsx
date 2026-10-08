import { useState } from 'react'
import type { FormEvent } from 'react'
import { IconArrowBackUp, IconArrowRight, IconCheck, IconPencil, IconTrash } from '@tabler/icons-react'
import { useDeleteIdea, useIdeas, useSaveIdea } from '../api/lifeHooks'
import type { Idea, IdeaStatus } from '../api/types'
import { Empty, ErrorLine, Field, Loading, Modal, Panel } from '../components/ui'
import { useToast } from '../components/useToast'

const COLUMNS: { status: IdeaStatus; title: string; empty: string }[] = [
  { status: 'TODO', title: 'Pinned', empty: 'Nothing pinned.' },
  { status: 'DOING', title: 'On the anvil', empty: 'Nothing in progress.' },
  { status: 'DONE', title: 'Forged', empty: 'Nothing finished yet.' },
]

const NEXT: Record<IdeaStatus, IdeaStatus> = { TODO: 'DOING', DOING: 'DONE', DONE: 'TODO' }

/** The workshop: pin an idea, work on it, finish it (+15 XP). */
export function WorkshopPage() {
  const ideas = useIdeas()
  const save = useSaveIdea()
  const remove = useDeleteIdea()
  const { toast, showToast } = useToast()
  const [title, setTitle] = useState('')
  const [notes, setNotes] = useState('')
  const [editing, setEditing] = useState<Idea | null>(null)

  function pin(e: FormEvent) {
    e.preventDefault()
    if (!title.trim()) return
    save.mutate({ title, notes }, { onSuccess: () => { setTitle(''); setNotes('') } })
  }

  function move(idea: Idea) {
    const status = NEXT[idea.status]
    save.mutate({ id: idea.id, title: idea.title, notes: idea.notes, status },
      { onSuccess: () => status === 'DONE' && showToast('Forged · +15 XP') })
  }

  return (
    <>
      <div className="page-head">
        <div>
          <h1>The Workshop</h1>
          <div className="sub">Schemes and half-built contraptions, from idea to done</div>
        </div>
      </div>

      <Panel>
        <form onSubmit={pin} className="form-grid" style={{ gridTemplateColumns: 'minmax(0,2fr) minmax(0,3fr) auto' }}>
          <input aria-label="New idea" placeholder="Refactor the auth module" value={title} maxLength={120}
                 onChange={(e) => setTitle(e.target.value)} />
          <input aria-label="Notes" placeholder="Notes (optional)" value={notes} maxLength={1000}
                 onChange={(e) => setNotes(e.target.value)} />
          <button type="submit" className="primary" disabled={save.isPending}>Pin idea</button>
        </form>
        <ErrorLine error={save.error ?? remove.error ?? ideas.error} />
      </Panel>

      {ideas.isLoading && <Loading />}
      <div className="board" style={{ marginTop: 14 }}>
        {COLUMNS.map((col) => {
          const list = (ideas.data ?? []).filter((i) => i.status === col.status)
          return (
            <Panel key={col.status} title={`${col.title} · ${list.length}`}>
              {list.length === 0 && <Empty>{col.empty}</Empty>}
              {list.map((i) => (
                <div key={i.id} className={`idea-card ${i.status === 'DONE' ? 'done' : ''}`}>
                  <div className="title">{i.title}</div>
                  {i.notes && <div className="small muted">{i.notes}</div>}
                  <div className="row" style={{ marginTop: 8 }}>
                    <button className={i.status === 'DOING' ? 'primary' : ''} onClick={() => move(i)}>
                      {i.status === 'TODO' && <><IconArrowRight size={16} /> Start</>}
                      {i.status === 'DOING' && <><IconCheck size={16} /> Finish</>}
                      {i.status === 'DONE' && <><IconArrowBackUp size={16} /> Reopen</>}
                    </button>
                    <span className="row-start" style={{ gap: 2 }}>
                      <button className="ghost" aria-label="Edit idea" onClick={() => setEditing(i)}><IconPencil size={16} /></button>
                      <button className="ghost danger" aria-label="Remove idea"
                              onClick={() => window.confirm(`Remove "${i.title}"?`) && remove.mutate(i.id)}><IconTrash size={16} /></button>
                    </span>
                  </div>
                </div>
              ))}
            </Panel>
          )
        })}
      </div>

      {editing && <IdeaForm idea={editing} onClose={() => setEditing(null)} />}
      {toast}
    </>
  )
}

function IdeaForm({ idea, onClose }: { idea: Idea; onClose: () => void }) {
  const save = useSaveIdea()
  const [title, setTitle] = useState(idea.title)
  const [notes, setNotes] = useState(idea.notes)

  function submit(e: FormEvent) {
    e.preventDefault()
    if (!title.trim()) return
    save.mutate({ id: idea.id, title, notes, status: idea.status }, { onSuccess: onClose })
  }

  return (
    <Modal title="Edit idea" onClose={onClose}>
      <form onSubmit={submit} className="stack" style={{ gap: 12 }}>
        <Field label="Idea"><input value={title} maxLength={120} autoFocus required onChange={(e) => setTitle(e.target.value)} /></Field>
        <Field label="Notes"><textarea rows={4} value={notes} maxLength={1000} onChange={(e) => setNotes(e.target.value)} /></Field>
        <ErrorLine error={save.error} />
        <div className="row"><span /><button type="submit" className="primary" disabled={save.isPending}>Save</button></div>
      </form>
    </Modal>
  )
}
