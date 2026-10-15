import { useState } from 'react'
import type { FormEvent } from 'react'
import { IconArchive, IconFlask, IconPencil, IconPlus, IconRestore } from '@tabler/icons-react'
import { money } from '../api/client'
import { useArchiveCategory, useBudgets, useCategories, useSaveCategory } from '../api/hooks'
import type { Category, CategoryKind } from '../api/types'
import { CategoryIcon, PlainIcon } from '../components/icons'
import { ICON_KEYS } from '../lib/icons'
import { Bar, Empty, ErrorLine, Field, Loading, Modal, Money, MonthPicker, Panel } from '../components/ui'
import { currentMonth } from '../lib/format'

/** Budgets are "mana": each one drains as you spend in its category. */
export function BudgetsPage() {
  const [month, setMonth] = useState(currentMonth())
  const [editing, setEditing] = useState<Category | CategoryKind | null>(null)
  const budgets = useBudgets(month)
  const categories = useCategories()
  const archive = useArchiveCategory()

  const all = categories.data ?? []
  const unbudgeted = all.filter((c) => c.kind === 'EXPENSE' && !c.archived && c.monthlyBudget == null)
  const b = budgets.data

  return (
    <>
      <div className="page-head">
        <div>
          <h1>Budgets</h1>
          <div className="sub">Your mana for the month: spend it wisely</div>
        </div>
        <MonthPicker month={month} onChange={setMonth} />
      </div>

      {budgets.isLoading && <Loading />}
      <ErrorLine error={budgets.error} />

      {b && (
        <div className="grid grid-3" style={{ marginBottom: 14 }}>
          <Panel><div className="stat"><span className="label">Budgeted</span><Money value={b.totalBudget} className="value" /></div></Panel>
          <Panel><div className="stat"><span className="label">Left</span><Money value={b.left} className={`value ${b.left < 0 ? 'neg' : 'gold'}`} /></div></Panel>
          <Panel><div className="stat"><span className="label">Days left</span><span className="value num">{b.daysLeft}</span></div></Panel>
        </div>
      )}

      <Panel title="Mana by category">
        {b && b.categories.length === 0 && (
          <Empty icon={<IconFlask size={26} />}>No budgets yet. Give a category a monthly limit below and watch its mana drain as you spend.</Empty>
        )}
        {b?.categories.map((row) => {
          const over = row.left < 0
          const category = all.find((c) => c.id === row.category.id)
          return (
            <div key={row.category.id} className="list-row" style={{ alignItems: 'flex-start' }}>
              <CategoryIcon icon={row.category.icon} color={row.category.color} />
              <div className="grow">
                <div className="row">
                  <span>{row.category.name}</span>
                  <span className={`num ${over ? 'neg' : ''}`}>
                    {over ? <>over by <Money value={-row.left} /></> : <><Money value={row.left} /> left</>}
                  </span>
                </div>
                <Bar value={Math.max(row.left, 0)} max={row.budget} color={over ? 'var(--hp)' : 'var(--mp)'}
                     label={`${row.category.name} budget left`} />
                <div className="small muted num" style={{ marginTop: -6 }}>
                  <Money value={row.spent} /> of <Money value={row.budget} /> spent
                </div>
              </div>
              {category && <button className="ghost" aria-label={`Edit ${row.category.name}`} onClick={() => setEditing(category)}><IconPencil size={16} /></button>}
            </div>
          )
        })}
        {b && b.unbudgetedSpent > 0 && (
          <p className="small muted">Plus <Money value={b.unbudgetedSpent} /> spent in categories without a budget.</p>
        )}
        {unbudgeted.length > 0 && (
          <div className="row-start wrap" style={{ marginTop: 10 }}>
            <span className="small muted">Add a budget:</span>
            {unbudgeted.map((c) => (
              <button key={c.id} className="chip" onClick={() => setEditing(c)}>
                <PlainIcon icon={c.icon} size={14} /> {c.name}
              </button>
            ))}
          </div>
        )}
      </Panel>

      <div className="grid grid-2" style={{ marginTop: 14 }}>
        {(['EXPENSE', 'INCOME'] as const).map((kind) => (
          <Panel key={kind} title={kind === 'EXPENSE' ? 'Spending categories' : 'Income categories'}
                 action={<button className="ghost" onClick={() => setEditing(kind)}><IconPlus size={16} /> Add</button>}>
            {categories.isLoading && <Loading />}
            <div className="list">
              {all.filter((c) => c.kind === kind).map((c) => (
                <div key={c.id} className="list-row" style={{ opacity: c.archived ? 0.5 : 1 }}>
                  <CategoryIcon icon={c.icon} color={c.color} />
                  <span className="grow">{c.name}{c.archived && <span className="muted small"> · archived</span>}</span>
                  {c.monthlyBudget != null && <span className="small muted num"><Money value={c.monthlyBudget} />/mo</span>}
                  {c.archived ? (
                    <button className="ghost" aria-label={`Restore ${c.name}`} onClick={() => archive.mutate({ id: c.id, restore: true })}><IconRestore size={16} /></button>
                  ) : (
                    <>
                      <button className="ghost" aria-label={`Edit ${c.name}`} onClick={() => setEditing(c)}><IconPencil size={16} /></button>
                      <button className="ghost danger" aria-label={`Archive ${c.name}`}
                              onClick={() => window.confirm(`Remove ${c.name}? If it has entries, it's archived instead so your history stays intact.`) && archive.mutate({ id: c.id })}>
                        <IconArchive size={16} />
                      </button>
                    </>
                  )}
                </div>
              ))}
            </div>
          </Panel>
        ))}
      </div>

      {editing && (
        <CategoryForm category={typeof editing === 'string' ? undefined : editing}
                      kind={typeof editing === 'string' ? editing : editing.kind}
                      onClose={() => setEditing(null)} />
      )}
    </>
  )
}

const COLORS = ['#e5606a', '#59a8f0', '#e2b453', '#b48ef0', '#f0a35e', '#62c99a', '#94a1b5', '#5fd3c8']

function CategoryForm({ category, kind, onClose }: { category?: Category; kind: CategoryKind; onClose: () => void }) {
  const save = useSaveCategory()
  const [name, setName] = useState(category?.name ?? '')
  const [icon, setIcon] = useState(category?.icon ?? 'coin')
  const [color, setColor] = useState(category?.color ?? COLORS[0])
  const [budget, setBudget] = useState(category?.monthlyBudget != null ? String(category.monthlyBudget) : '')
  const [problem, setProblem] = useState<string | null>(null)

  function submit(e: FormEvent) {
    e.preventDefault()
    if (!name.trim()) return setProblem('Give it a name.')
    if (budget && !(Number(budget) > 0)) return setProblem('A budget needs to be above zero, or left empty.')
    setProblem(null)
    save.mutate(
      { id: category?.id, name, kind, icon, color, monthlyBudget: kind === 'EXPENSE' && budget ? money(budget) : null },
      { onSuccess: onClose },
    )
  }

  return (
    <Modal title={category ? `Edit ${category.name}` : `New ${kind === 'EXPENSE' ? 'spending' : 'income'} category`} onClose={onClose}>
      <form onSubmit={submit} className="stack" style={{ gap: 12 }}>
        <div className="form-grid">
          <Field label="Name"><input value={name} maxLength={40} autoFocus onChange={(e) => setName(e.target.value)} /></Field>
          {kind === 'EXPENSE' && (
            <Field label="Monthly budget (RM, optional)">
              <input inputMode="decimal" value={budget} placeholder="600" onChange={(e) => setBudget(e.target.value)} />
            </Field>
          )}
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
        <Field label="Colour">
          <div className="row-start wrap" style={{ gap: 6 }}>
            {COLORS.map((c) => (
              <button key={c} type="button" aria-label={`Colour ${c}`} aria-pressed={color === c} onClick={() => setColor(c)}
                      style={{ width: 30, height: 30, padding: 0, background: c, borderColor: color === c ? 'var(--text)' : c }} />
            ))}
          </div>
        </Field>
        {problem && <p className="field-error" role="alert">{problem}</p>}
        <ErrorLine error={save.error} />
        <div className="row">
          <CategoryIcon icon={icon} color={color} />
          <button type="submit" className="primary" disabled={save.isPending}>{save.isPending ? 'Saving…' : 'Save'}</button>
        </div>
      </form>
    </Modal>
  )
}
