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
import { Bar, Empty, Panel } from './ui'

// ---------------- weather (Open-Meteo: free, no key, called from the browser) ----------------

function weatherLook(code: number): { label: string; icon: Icon } {
  if (code === 0) return { label: 'Clear sky', icon: IconSun }
  if ([1, 2, 3, 45, 48].includes(code)) return { label: code > 3 ? 'Foggy' : 'Partly cloudy', icon: IconCloud }
  if ([71, 73, 75, 77, 85, 86].includes(code)) return { label: 'Snowy', icon: IconSnowflake }
  if ([95, 96, 99].includes(code)) return { label: 'Thunderstorm', icon: IconCloudStorm }
  if (code >= 51 && code <= 82) return { label: 'Rainy', icon: IconCloudRain }
  return { label: 'Unsettled', icon: IconCloud }
}

export function WeatherCard() {
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
      <Panel title="The skies">
        <p className="muted small" style={{ margin: 0 }}>
          <IconMapPin size={14} aria-hidden="true" /> <Link to="/settings">Set your location</Link> to see the weather here.
        </p>
      </Panel>
    )
  }
  const w = weather.data
  const look = w ? weatherLook(w.current.weather_code) : null
  return (
    <Panel title="The skies">
      {weather.isError && <p className="muted small">The skies are clouded from view right now.</p>}
      {!w && !weather.isError && <p className="muted small">Reading the skies…</p>}
      {w && look && (
        <div className="row-start">
          <look.icon size={40} stroke={1.5} color="var(--accent)" aria-hidden="true" />
          <div>
            <div className="num" style={{ fontSize: '1.6em', lineHeight: 1 }}>{Math.round(w.current.temperature_2m)}°{unit}</div>
            <div className="small muted">
              {look.label} · {Math.round(w.daily.temperature_2m_min[0])}–{Math.round(w.daily.temperature_2m_max[0])}° · {place.place}
            </div>
          </div>
        </div>
      )}
    </Panel>
  )
}

// ---------------- markets (CoinGecko: Bitcoin, and Tether Gold as a gold-per-ounce price) ----------------

type Prices = Record<'bitcoin' | 'tether-gold', { usd: number; usd_24h_change: number }>

export function MarketCard() {
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
  return (
    <Panel title="The markets">
      {prices.isError && <p className="muted small">The market criers are silent right now.</p>}
      {!p && !prices.isError && <p className="muted small">Checking the markets…</p>}
      {p && (
        <div className="ticker">
          <Price label="Gold / oz" value={p['tether-gold']?.usd} change={p['tether-gold']?.usd_24h_change} />
          <Price label="Bitcoin" value={p.bitcoin?.usd} change={p.bitcoin?.usd_24h_change} />
        </div>
      )}
    </Panel>
  )
}

function Price({ label, value, change }: { label: string; value?: number; change?: number }) {
  if (value === undefined) return null
  const up = (change ?? 0) >= 0
  const Arrow = up ? IconArrowUpRight : IconArrowDownRight
  return (
    <div className="item">
      <span className="small muted">{label}</span>
      <span className="num gold" style={{ fontSize: '1.3em' }}>${value.toLocaleString('en-US', { maximumFractionDigits: 0 })}</span>
      {change !== undefined && (
        <span className={`small num ${up ? 'pos' : 'neg'}`}><Arrow size={14} aria-hidden="true" /> {Math.abs(change).toFixed(2)}% 24h</span>
      )}
    </div>
  )
}

// ---------------- today: meals, training, what's coming ----------------

export function TodayRow() {
  const life = useLifeToday()
  const tick = useSetWorkoutDone()
  const t = life.data
  if (!t) return null
  const over = t.calories > t.calorieGoal
  return (
    <div className="grid grid-3" style={{ marginTop: 14 }}>
      <Panel title="Today's feast" action={<Link to="/vitals" className="small">Vitals</Link>}>
        <div className="row">
          <span className={`num ${over ? 'neg' : 'gold'}`} style={{ fontSize: '1.4em' }}>{t.calories.toLocaleString()} kcal</span>
          <span className="muted small">of {t.calorieGoal.toLocaleString()}</span>
        </div>
        <Bar value={t.calories} max={t.calorieGoal} color={over ? 'var(--hp)' : 'var(--ok)'} label="Calories today" />
      </Panel>

      <Panel title="Today's training" action={<Link to="/vitals" className="small">Plan</Link>}>
        {t.workouts.length === 0 ? (
          <Empty>No training set for today.</Empty>
        ) : (
          t.workouts.map((w) => (
            <button key={w.id} className="ghost" style={{ width: '100%', justifyContent: 'flex-start' }} aria-pressed={w.done}
                    onClick={() => tick.mutate({ id: w.id, date: today(), done: !w.done })}>
              {w.done ? <IconSquareCheck size={20} color="var(--ok)" /> : <IconSquare size={20} />}
              <span style={{ textDecoration: w.done ? 'line-through' : 'none' }}>{w.name}</span>
            </button>
          ))
        )}
      </Panel>

      <Panel title="Coming up" action={<Link to="/calendar" className="small">Calendar</Link>}>
        {t.upcoming.length === 0 ? (
          <Empty>Nothing scheduled.</Empty>
        ) : (
          <div className="list">
            {t.upcoming.map((e) => (
              <div key={e.id} className="list-row" style={{ padding: '4px 2px' }}>
                <span className="grow title">{e.title}</span>
                <span className="small muted">{relativeDay(e.occursOn)}{e.atTime ? ` ${time(e.atTime)}` : ''}</span>
              </div>
            ))}
          </div>
        )}
        {t.ideasInProgress > 0 && (
          <p className="small muted" style={{ margin: '8px 0 0' }}>
            <IconBulb size={14} aria-hidden="true" /> <Link to="/workshop">{t.ideasInProgress} idea{t.ideasInProgress > 1 ? 's' : ''} on the anvil</Link>
          </p>
        )}
      </Panel>
    </div>
  )
}
