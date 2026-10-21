import { useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import { IconCalendarEvent, IconPencil, IconPlus, IconTrash } from '@tabler/icons-react'
import { useDeleteEvent, useEvents, useSaveEvent } from '../api/lifeHooks'
import type { CalendarEvent } from '../api/types'
import { Empty, ErrorLine, Field, Modal, MonthPicker, Panel } from '../components/ui'
import { addDays, currentMonth, dayLabel, iso, parseDate, time, today } from '../lib/format'

const DOW = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun']

/** The Almanac: a month at a glance, and the chosen day's events. */
export function CalendarPage() {
  const [month, setMonth] = useState(currentMonth())
  const [selected, setSelected] = useState(today())
  const [editing, setEditing] = useState<CalendarEvent | 'new' | null>(null)

  // Six Monday-first weeks covering the month, like a wall calendar.
  const days = useMemo(() => {
    const first = parseDate(`${month}-01`)
    const offset = (first.getDay() + 6) % 7
    const start = addDays(iso(first), -offset)
    return Array.from({ length: 42 }, (_, i) => addDays(start, i))
  }, [month])

  const events = useEvents(days[0], days[41])
  const remove = useDeleteEvent()
  const byDay = useMemo(() => {
    const map = new Map<string, CalendarEvent[]>()
    ;(events.data ?? []).forEach((e) => map.set(e.occursOn, [...(map.get(e.occursOn) ?? []), e]))
    return map
  }, [events.data])
  const chosen = byDay.get(selected) ?? []

  function pickMonth(m: string) {
    setMonth(m)
    setSelected(m === currentMonth() ? today() : `${m}-01`)
  }

  return (
    <>
      <div className="page-head">
        <div>
          <h1>Calendar</h1>
          <div className="sub">The almanac of your days</div>
        </div>
        <div className="row-start wrap">
          <MonthPicker month={month} onChange={pickMonth} />
          <button onClick={() => { setMonth(currentMonth()); setSelected(today()) }}>Today</button>
        </div>
      </div>

      <div className="grid cal-layout">
        <Panel>
          <div className="cal-grid">
            {DOW.map((d) => <div key={d} className="cal-dow">{d}</div>)}
            {days.map((d) => {
              const list = byDay.get(d) ?? []
              const classes = ['cal-day', d.slice(0, 7) !== month && 'other', d === today() && 'today', d === selected && 'selected']
              return (
                <button key={d} className={classes.filter(Boolean).join(' ')} onClick={() => setSelected(d)}
                        aria-label={`${dayLabel(d)}${list.length ? `, ${list.length} event${list.length > 1 ? 's' : ''}` : ''}`}>
                  <span className="n">{Number(d.slice(8))}</span>
                  <span className="dots">{list.slice(0, 4).map((e) => <span key={e.id} className="dot" />)}</span>
                </button>
              )
            })}
          </div>
          <ErrorLine error={events.error ?? remove.error} />
        </Panel>

        <Panel title={dayLabel(selected)}
               action={<button className="ghost" onClick={() => setEditing('new')}><IconPlus size={16} /> Add</button>}>
          {chosen.length === 0 ? (
            <Empty icon={<IconCalendarEvent size={26} />}>
              A quiet stretch. Nothing planned yet.
              <div><button onClick={() => setEditing('new')}>Add an event</button></div>
            </Empty>
          ) : (
            <div className="list">
              {chosen.map((e) => (
                <div key={e.id} className="list-row">
                  <span className="num gold" style={{ minWidth: 46 }}>{time(e.atTime) || 'All day'}</span>
                  <span className="grow title">{e.title}</span>
                  <button className="ghost" aria-label="Edit event" onClick={() => setEditing(e)}><IconPencil size={16} /></button>
                  <button className="ghost danger" aria-label="Delete event"
                          onClick={() => window.confirm(`Delete "${e.title}"?`) && remove.mutate(e.id)}>
                    <IconTrash size={16} />
                  </button>
                </div>
              ))}
            </div>
          )}
        </Panel>
      </div>

      {editing && (
        <EventForm event={editing === 'new' ? undefined : editing} date={selected} onClose={() => setEditing(null)} />
      )}
    </>
  )
}

function EventForm({ event, date, onClose }: { event?: CalendarEvent; date: string; onClose: () => void }) {
  const save = useSaveEvent()
  const [title, setTitle] = useState(event?.title ?? '')
  const [day, setDay] = useState(event?.occursOn ?? date)
  const [at, setAt] = useState(time(event?.atTime ?? null))
  const [problem, setProblem] = useState<string | null>(null)

  function submit(e: FormEvent) {
    e.preventDefault()
    if (!title.trim()) return setProblem('What is it?')
    setProblem(null)
    save.mutate({ id: event?.id, title, occursOn: day, atTime: at || null }, { onSuccess: onClose })
  }

  return (
    <Modal title={event ? 'Edit event' : 'New event'} onClose={onClose}>
      <form onSubmit={submit} className="stack" style={{ gap: 12 }}>
        <Field label="What">
          <input value={title} maxLength={200} autoFocus placeholder="Dentist" onChange={(e) => setTitle(e.target.value)} />
        </Field>
        <div className="form-grid">
          <Field label="Day"><input type="date" value={day} onChange={(e) => setDay(e.target.value)} required /></Field>
          <Field label="Time (optional)"><input type="time" value={at} onChange={(e) => setAt(e.target.value)} /></Field>
        </div>
        {problem && <p className="field-error" role="alert">{problem}</p>}
        <ErrorLine error={save.error} />
        <div className="row"><span /><button type="submit" className="primary" disabled={save.isPending}>{save.isPending ? 'Saving…' : 'Save'}</button></div>
      </form>
    </Modal>
  )
}
