import { useState } from 'react'
import type { FormEvent } from 'react'
import { IconChevronLeft, IconChevronRight, IconFlame, IconPencil, IconPlus, IconSquare, IconSoup, IconSquareCheck, IconSwords, IconTrash } from '@tabler/icons-react'
import { useMe, useSavePreferences } from '../api/hooks'
import { useAddMeal, useDeleteMeal, useDeleteWorkout, useMeals, useSaveWorkout, useSetWorkoutDone, useWorkouts } from '../api/lifeHooks'
import type { Weekday, Workout } from '../api/types'
import { Bar, Empty, ErrorLine, Field, Loading, Modal, Panel } from '../components/ui'
import { useToast } from '../components/useToast'
import { addDays, dayLabel, parseDate, today } from '../lib/format'

const WEEKDAYS: { id: Weekday; short: string }[] = [
  { id: 'MONDAY', short: 'Mon' }, { id: 'TUESDAY', short: 'Tue' }, { id: 'WEDNESDAY', short: 'Wed' },
  { id: 'THURSDAY', short: 'Thu' }, { id: 'FRIDAY', short: 'Fri' }, { id: 'SATURDAY', short: 'Sat' },
  { id: 'SUNDAY', short: 'Sun' },
]

/** Feast and training: what you ate against your goal, and what you trained. */
export function VitalsPage() {
  const [date, setDate] = useState(today())
  const { toast, showToast } = useToast()

  return (
    <>
      <div className="page-head">
        <div>
          <h1>Vitals</h1>
          <div className="sub">Feast and training</div>
        </div>
        <div className="row-start">
          <button className="ghost" aria-label="Previous day" onClick={() => setDate(addDays(date, -1))}><IconChevronLeft size={18} /></button>
          <span className="num" style={{ minWidth: 110, textAlign: 'center' }}>{dayLabel(date)}</span>
          <button className="ghost" aria-label="Next day" onClick={() => setDate(addDays(date, 1))}><IconChevronRight size={18} /></button>
          {date !== today() && <button onClick={() => setDate(today())}>Today</button>}
        </div>
      </div>

      <div className="grid grid-2">
        <Feast date={date} onLogged={() => showToast('Meal logged')} />
        <Training date={date} onDone={(xp) => xp && showToast('Trained · +10 XP')} />
      </div>
      {toast}
    </>
  )
}

function Feast({ date, onLogged }: { date: string; onLogged: () => void }) {
  const day = useMeals(date)
  const add = useAddMeal()
  const remove = useDeleteMeal()
  const me = useMe().data
  const saveGoal = useSavePreferences()
  const [name, setName] = useState('')
  const [cal, setCal] = useState('')
  const [problem, setProblem] = useState<string | null>(null)

  function submit(e: FormEvent) {
    e.preventDefault()
    const calories = Number(cal)
    if (!name.trim()) return setProblem('What did you eat?')
    if (!Number.isInteger(calories) || calories < 0) return setProblem('Calories must be a whole number.')
    setProblem(null)
    add.mutate({ name, calories, eatenOn: date }, {
      onSuccess: () => {
        setName('')
        setCal('')
        onLogged()
      },
    })
  }

  function editGoal() {
    const answer = window.prompt('Daily calorie goal (500–10000)', String(me?.calorieGoal ?? 2000))
    const goal = Number(answer)
    if (answer && Number.isInteger(goal) && goal >= 500 && goal <= 10000) saveGoal.mutate({ calorieGoal: goal })
  }

  const d = day.data
  const over = d ? d.total > d.goal : false
  const peak = d ? Math.max(d.goal, ...d.week.map((w) => w.calories)) : 1

  return (
    <Panel title="The feast" action={<button className="ghost small" onClick={editGoal}><IconPencil size={14} /> Goal</button>}>
      {day.isLoading && <Loading />}
      {d && (
        <>
          <div className="row">
            <span className={`num ${over ? 'neg' : 'gold'}`} style={{ fontSize: '1.6em' }}>{d.total.toLocaleString()} kcal</span>
            <span className="muted">of {d.goal.toLocaleString()}</span>
          </div>
          <Bar value={d.total} max={d.goal} color={over ? 'var(--hp)' : 'var(--ok)'} label="Calories against goal" />

          <form onSubmit={submit} className="form-grid" style={{ gridTemplateColumns: 'minmax(0,2fr) minmax(0,1fr) auto' }}>
            <input aria-label="Meal" placeholder="Nasi lemak" value={name} maxLength={100} onChange={(e) => setName(e.target.value)} />
            <input aria-label="Calories" inputMode="numeric" placeholder="kcal" value={cal} onChange={(e) => setCal(e.target.value)} />
            <button type="submit" disabled={add.isPending} aria-label="Add meal"><IconPlus size={16} /></button>
          </form>
          {problem && <p className="field-error" role="alert">{problem}</p>}
          <ErrorLine error={add.error ?? remove.error} />

          {d.meals.length === 0 ? (
            <Empty icon={<IconSoup size={26} />}>No feasting recorded for this day. The larder is quiet.</Empty>
          ) : (
            <div className="list" style={{ marginTop: 8 }}>
              {d.meals.map((m) => (
                <div key={m.id} className="list-row">
                  <span className="grow title">{m.name}</span>
                  <span className="num">{m.calories} kcal</span>
                  <button className="ghost danger" aria-label={`Remove ${m.name}`} onClick={() => remove.mutate(m.id)}><IconTrash size={16} /></button>
                </div>
              ))}
            </div>
          )}

          <h2 style={{ marginTop: 16 }}>Last 7 days</h2>
          <div className="week-chart" aria-label="Calories over the last 7 days">
            {d.week.map((w) => (
              <div key={w.date} className="col" title={`${dayLabel(w.date)}: ${w.calories} kcal`}>
                <div className={`barv ${w.calories > d.goal ? 'over' : ''}`} style={{ height: `${(w.calories / peak) * 100}%` }} />
                <span className="small muted">{parseDate(w.date).toLocaleDateString('en-MY', { weekday: 'narrow' })}</span>
              </div>
            ))}
          </div>
        </>
      )}
    </Panel>
  )
}

function Training({ date, onDone }: { date: string; onDone: (xp: boolean) => void }) {
  const list = useWorkouts(date)
  const tick = useSetWorkoutDone()
  const remove = useDeleteWorkout()
  const [editing, setEditing] = useState<Workout | 'new' | null>(null)
  const workouts = list.data ?? []
  const planned = workouts.filter((w) => w.scheduled)
  const others = workouts.filter((w) => !w.scheduled)

  function row(w: Workout) {
    return (
      <div key={w.id} className="list-row">
        <button className="ghost" aria-pressed={w.done} aria-label={`${w.done ? 'Untick' : 'Tick'} ${w.name}`}
                onClick={() => tick.mutate({ id: w.id, date, done: !w.done }, { onSuccess: (r) => onDone(r.done) })}>
          {w.done ? <IconSquareCheck size={22} color="var(--ok)" /> : <IconSquare size={22} />}
        </button>
        <div className="grow">
          <div className="title">{w.name}</div>
          <div className="small muted">
            {w.days.length === 0 ? 'Any day' : WEEKDAYS.filter((d) => w.days.includes(d.id)).map((d) => d.short).join(' · ')}
            {' · '}{w.totalDone} done
          </div>
        </div>
        {w.streak > 0 && <span className="num" title="Streak of planned days"><IconFlame size={16} color="var(--hp)" /> {w.streak}</span>}
        <button className="ghost" aria-label={`Edit ${w.name}`} onClick={() => setEditing(w)}><IconPencil size={16} /></button>
        <button className="ghost danger" aria-label={`Delete ${w.name}`}
                onClick={() => window.confirm(`Delete "${w.name}" and its history?`) && remove.mutate(w.id)}><IconTrash size={16} /></button>
      </div>
    )
  }

  return (
    <Panel title="Training grounds" action={<button className="ghost" onClick={() => setEditing('new')}><IconPlus size={16} /> Add</button>}>
      {list.isLoading && <Loading />}
      <ErrorLine error={list.error ?? tick.error ?? remove.error} />
      {list.data && workouts.length === 0 && (
        <Empty icon={<IconSwords size={26} />}>
          No training set. The dungeon awaits thy strength.
          <div><button onClick={() => setEditing('new')}>Add a workout</button></div>
        </Empty>
      )}
      {planned.length > 0 && <div className="list">{planned.map(row)}</div>}
      {others.length > 0 && (
        <>
          <div className="day-head">Not planned for {dayLabel(date)}</div>
          <div className="list" style={{ opacity: 0.75 }}>{others.map(row)}</div>
        </>
      )}
      {editing && <WorkoutForm workout={editing === 'new' ? undefined : editing} onClose={() => setEditing(null)} />}
    </Panel>
  )
}

function WorkoutForm({ workout, onClose }: { workout?: Workout; onClose: () => void }) {
  const save = useSaveWorkout()
  const [name, setName] = useState(workout?.name ?? '')
  const [days, setDays] = useState<Weekday[]>(workout?.days ?? [])

  function submit(e: FormEvent) {
    e.preventDefault()
    if (!name.trim()) return
    save.mutate({ id: workout?.id, name, days }, { onSuccess: onClose })
  }

  return (
    <Modal title={workout ? 'Edit workout' : 'New workout'} onClose={onClose}>
      <form onSubmit={submit} className="stack" style={{ gap: 12 }}>
        <Field label="Name">
          <input value={name} maxLength={80} autoFocus required placeholder="Morning run" onChange={(e) => setName(e.target.value)} />
        </Field>
        <Field label="Days (none = any day)">
          <div className="day-chips">
            {WEEKDAYS.map((d) => (
              <button key={d.id} type="button" aria-pressed={days.includes(d.id)} className={days.includes(d.id) ? 'primary' : ''}
                      onClick={() => setDays(days.includes(d.id) ? days.filter((x) => x !== d.id) : [...days, d.id])}>
                {d.short}
              </button>
            ))}
          </div>
        </Field>
        <ErrorLine error={save.error} />
        <div className="row"><span className="small muted">+10 XP each time you tick it off</span>
          <button type="submit" className="primary" disabled={save.isPending}>{save.isPending ? 'Saving…' : 'Save'}</button></div>
      </form>
    </Modal>
  )
}
