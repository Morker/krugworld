import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import {
  IconBulb,
  IconCalendarDue,
  IconCloud,
  IconCloudRain,
  IconCloudStorm,
  IconHeartHandshake,
  IconMapPin,
  IconRosetteDiscountCheck,
  IconSnowflake,
  IconSquare,
  IconSquareCheck,
  IconSun,
} from '@tabler/icons-react'
import type { Icon } from '@tabler/icons-react'
import { useDebts, useMe, useRules } from '../api/hooks'
import { useLifeToday, useSetWorkoutDone } from '../api/lifeHooks'
import type { Rule } from '../api/types'
import { dayLabel, percent, relativeDay, time, today } from '../lib/format'
import { CategoryIcon } from './icons'
import { SealDialog } from './SealDialog'
import { Bar, Empty, Money, Panel } from './ui'

// ---------------- weather (Open-Meteo: free, no key, called from the browser) ----------------

function weatherLook(code: number): { label: string; icon: Icon } {
  if (code === 0) return { label: 'Clear sky', icon: IconSun }
  if ([1, 2, 3, 45, 48].includes(code)) return { label: code > 3 ? 'Foggy' : 'Partly cloudy', icon: IconCloud }
  if ([71, 73, 75, 77, 85, 86].includes(code)) return { label: 'Snowy', icon: IconSnowflake }
  if ([95, 96, 99].includes(code)) return { label: 'Thunderstorm', icon: IconCloudStorm }
  if (code >= 51 && code <= 82) return { label: 'Rainy', icon: IconCloudRain }
  return { label: 'Unsettled', icon: IconCloud }
}

/** A one-line weather reading for the Hall's title bar. */
export function WeatherChip() {
  const me = useMe().data
  const place = me?.weather
  const unit = me?.tempUnit ?? 'C'
  const weather = useQuery({
    queryKey: ['weather', place?.lat, place?.lon, unit],
    enabled: !!place,
    staleTime: 15 * 60_000,
    queryFn: async () => {
      const u = new URL('https://api.open-meteo.com/v1/forecast')
      u.searchParams.set('latitude', String(place!.lat))
      u.searchParams.set('longitude', String(place!.lon))
      u.searchParams.set('current', 'temperature_2m,weather_code')
      u.searchParams.set('daily', 'temperature_2m_max,temperature_2m_min')
      u.searchParams.set('forecast_days', '1')
      u.searchParams.set('timezone', 'auto')
      u.searchParams.set('temperature_unit', unit === 'F' ? 'fahrenheit' : 'celsius')
      const res = await fetch(u)
      if (!res.ok) throw new Error('weather')
      return (await res.json()) as {
        current: { temperature_2m: number; weather_code: number }
        daily: { temperature_2m_max: number[]; temperature_2m_min: number[] }
      }
    },
  })

  if (!place) {
    return (
      <Link to="/settings" className="mini small">
        <IconMapPin size={16} aria-hidden="true" /> Set your location for weather
      </Link>
    )
  }
  const w = weather.data
  const look = w ? weatherLook(w.current.weather_code) : null
  return (
    <div className="mini" title={place.place}>
      {look ? <look.icon size={26} stroke={1.6} color="var(--accent)" aria-hidden="true" /> : <IconCloud size={26} aria-hidden="true" />}
      {w && look ? (
        <div>
          <div className="num" style={{ lineHeight: 1.1 }}>{Math.round(w.current.temperature_2m)}°{unit} <span className="muted small">{look.label}</span></div>
          <div className="small muted ellipsis">
            {Math.round(w.daily.temperature_2m_min[0])}–{Math.round(w.daily.temperature_2m_max[0])}° · {place.place.split(',')[0]}
          </div>
        </div>
      ) : (
        <span className="small muted">{weather.isError ? 'Skies clouded from view' : 'Reading the skies…'}</span>
      )}
    </div>
  )
}

// ---------------- today: meals, training and what's coming, in one panel ----------------

export function TodayPanel() {
  const life = useLifeToday()
  const tick = useSetWorkoutDone()
  const t = life.data
  if (!t) return null
  const over = t.calories > t.calorieGoal
  return (
    <Panel title="Today" action={<Link to="/vitals" className="small">Vitals</Link>}>
      <div className="row small">
        <span>Feast</span>
        <span className={`num ${over ? 'neg' : 'gold'}`}>{t.calories.toLocaleString()} <span className="muted">/ {t.calorieGoal.toLocaleString()} kcal</span></span>
      </div>
      <Bar value={t.calories} max={t.calorieGoal} color={over ? 'var(--hp)' : 'var(--ok)'} thin label="Calories today" />

      <div className="today-label">Training</div>
      {t.workouts.length === 0 && <div className="small muted">Rest day: nothing planned.</div>}
      {t.workouts.map((w) => (
        <button key={w.id} className="ghost today-tick" aria-pressed={w.done}
                onClick={() => tick.mutate({ id: w.id, date: today(), done: !w.done })}>
          {w.done ? <IconSquareCheck size={18} color="var(--ok)" /> : <IconSquare size={18} />}
          <span style={{ textDecoration: w.done ? 'line-through' : 'none' }}>{w.name}</span>
        </button>
      ))}

      <div className="today-label row">
        <span>Coming up</span>
        <Link to="/calendar" className="small">Calendar</Link>
      </div>
      {t.upcoming.length === 0 && <div className="small muted">Nothing scheduled.</div>}
      {t.upcoming.slice(0, 3).map((e) => (
        <div key={e.id} className="row small" style={{ padding: '2px 0' }}>
          <span className="ellipsis">{e.title}</span>
          <span className="muted" style={{ whiteSpace: 'nowrap' }}>{relativeDay(e.occursOn)}{e.atTime ? ` ${time(e.atTime)}` : ''}</span>
        </div>
      ))}
      {t.ideasInProgress > 0 && (
        <div className="small muted" style={{ marginTop: 6 }}>
          <IconBulb size={14} aria-hidden="true" /> <Link to="/workshop">{t.ideasInProgress} idea{t.ideasInProgress > 1 ? 's' : ''} on the anvil</Link>
        </div>
      )}
    </Panel>
  )
}

// ---------------- recurring entries waiting for the user's confirmation ----------------

/** Only shows up when something is due: bills you chose to confirm yourself. */
export function AwaitingSeal({ onSealed }: { onSealed: () => void }) {
  const rules = useRules()
  const [sealing, setSealing] = useState<Rule | null>(null)
  const due = (rules.data ?? []).filter((r) => r.awaitingConfirmation)
  if (due.length === 0) return null
  return (
    <Panel title="Awaiting your seal" className="seal-panel"
           action={<Link to="/recurring" className="small">Recurring</Link>}>
      <div className="list">
        {due.slice(0, 3).map((r) => (
          <div key={r.id} className="list-row" style={{ padding: '4px 2px' }}>
            <CategoryIcon icon={r.category.icon} color={r.category.color} size={16} />
            <span className="grow">
              <span className="title" style={{ display: 'block' }}>{r.note || r.category.name}</span>
              <span className="small muted">{dayLabel(r.nextRunOn)}</span>
            </span>
            <span className={`num ${r.type === 'INCOME' ? 'pos' : ''}`}>
              {r.type === 'INCOME' ? '+' : '−'}<Money value={r.amount} />
            </span>
            <button className="seal-btn" aria-label={`Confirm ${r.note || r.category.name}`} onClick={() => setSealing(r)}>
              <IconRosetteDiscountCheck size={18} aria-hidden="true" />
            </button>
          </div>
        ))}
      </div>
      {due.length > 3 && <div className="small muted" style={{ marginTop: 4 }}>and {due.length - 3} more</div>}
      {sealing && <SealDialog rule={sealing} onClose={() => setSealing(null)} onSealed={onSealed} />}
    </Panel>
  )
}

// ---------------- debts: the tug of war between what you're owed and what you owe ----------------

export function DebtsPanel() {
  const debts = useDebts()
  if (!debts.data) return <Panel title="Debts"><span className="small muted">Counting IOUs…</span></Panel>
  const open = debts.data.filter((d) => !d.settledOn)
  const owed = open.filter((d) => d.direction === 'THEY_OWE').reduce((s, d) => s + d.amount, 0)
  const owe = open.filter((d) => d.direction === 'I_OWE').reduce((s, d) => s + d.amount, 0)
  // Overdue first, then whatever falls due soonest.
  const top = [...open].sort((a, b) => Number(b.overdue) - Number(a.overdue)
    || (a.dueOn ?? '9999').localeCompare(b.dueOn ?? '9999')).slice(0, 3)

  return (
    <Panel title="Debts" action={<Link to="/debts" className="small">All debts</Link>}>
      {open.length === 0 ? (
        <Empty icon={<IconHeartHandshake size={22} />}>
          <span className="small">No debts on the books.</span>
          <div><Link to="/debts" className="small">Record one</Link></div>
        </Empty>
      ) : (
        <>
          <div className="row small">
            <span>Owed to you <Money value={owed} className="pos" /></span>
            <span>You owe <Money value={owe} className="neg" /></span>
          </div>
          <div className="tug" role="img" aria-label={`Owed to you ${Math.round(percent(owed, owed + owe))}%, you owe the rest`}>
            <div style={{ width: `${percent(owed, owed + owe)}%` }} />
          </div>
          <div className="list">
            {top.map((d) => (
              <div key={d.id} className="list-row" style={{ padding: '3px 2px' }}>
                <span className="debt-face small-face" aria-hidden="true"
                      style={{ borderColor: d.direction === 'THEY_OWE' ? 'var(--ok)' : 'var(--hp)' }}>
                  {d.person.charAt(0).toUpperCase()}
                </span>
                <span className="grow title">{d.person}</span>
                {d.dueOn && (
                  <span className={`small ${d.overdue ? 'neg' : 'muted'}`} style={{ whiteSpace: 'nowrap' }}>
                    <IconCalendarDue size={13} aria-hidden="true" /> {d.overdue ? 'overdue' : relativeDay(d.dueOn)}
                  </span>
                )}
                <Money value={d.amount} className={d.direction === 'THEY_OWE' ? 'pos' : 'neg'} />
              </div>
            ))}
          </div>
        </>
      )}
    </Panel>
  )
}
