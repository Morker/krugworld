import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import {
  IconArrowDownRight,
  IconArrowUpRight,
  IconBulb,
  IconCloud,
  IconCloudRain,
  IconCloudStorm,
  IconMapPin,
  IconSnowflake,
  IconSquare,
  IconSquareCheck,
  IconSun,
} from '@tabler/icons-react'
import type { Icon } from '@tabler/icons-react'
import { useMe } from '../api/hooks'
import { useLifeToday, useSetWorkoutDone } from '../api/lifeHooks'
import { relativeDay, time, today } from '../lib/format'
import { Bar, Panel } from './ui'

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

// ---------------- markets (CoinGecko: Bitcoin, and Tether Gold as a gold-per-ounce price) ----------------

type Prices = Record<'bitcoin' | 'tether-gold', { usd: number; usd_24h_change: number }>

export function MarketChip() {
  const prices = useQuery({
    queryKey: ['markets'],
    staleTime: 10 * 60_000,
    queryFn: async () => {
      const res = await fetch('https://api.coingecko.com/api/v3/simple/price?ids=bitcoin,tether-gold&vs_currencies=usd&include_24hr_change=true')
      if (!res.ok) throw new Error('markets')
      return (await res.json()) as Prices
    },
  })
  const p = prices.data
  if (!p) {
    return <div className="mini small muted">{prices.isError ? 'Markets quiet' : 'Checking markets…'}</div>
  }
  return (
    <div className="mini">
      <Price label="Gold" value={p['tether-gold']?.usd} change={p['tether-gold']?.usd_24h_change} />
      <span className="mini-divider" />
      <Price label="BTC" value={p.bitcoin?.usd} change={p.bitcoin?.usd_24h_change} />
    </div>
  )
}

function Price({ label, value, change }: { label: string; value?: number; change?: number }) {
  if (value === undefined) return null
  const up = (change ?? 0) >= 0
  const Arrow = up ? IconArrowUpRight : IconArrowDownRight
  return (
    <div>
      <div className="small muted" style={{ lineHeight: 1.1 }}>{label}</div>
      <div className="num">
        <span className="gold">${value.toLocaleString('en-US', { maximumFractionDigits: 0 })}</span>
        {change !== undefined && (
          <span className={`small ${up ? 'pos' : 'neg'}`}> <Arrow size={12} aria-hidden="true" />{Math.abs(change).toFixed(1)}%</span>
        )}
      </div>
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
