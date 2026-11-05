import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { IconBell, IconCheck, IconLogout, IconTrash, IconUpload } from '@tabler/icons-react'
import { api } from '../api/client'
import {
  useDevices,
  useLogout,
  useMe,
  useNewPairingCode,
  usePushDevices,
  useRemovePushDevice,
  useRevokeDevice,
  useSavePushSubscription,
  useTestPush,
  useSavePreferences,
  useTavernImport,
} from '../api/hooks'
import type { Skin, Theme } from '../api/types'
import { ErrorLine, Field, Panel } from '../components/ui'
import { applySkin, SKIN_THEMES, themeFor } from '../skin/skin'

export function SettingsPage() {
  const me = useMe().data
  const save = useSavePreferences()
  const logout = useLogout()
  if (!me) return null

  function choose(skin: Skin, theme: Theme = themeFor(skin, me!.theme)) {
    applySkin({ skin, theme }) // instant, before the server confirms
    save.mutate({ skin, theme })
  }

  const themes = SKIN_THEMES[me.skin]

  return (
    <>
      <div className="page-head">
        <div>
          <h1>Settings</h1>
          <div className="sub">Logged in as {me.username}</div>
        </div>
        <button onClick={() => logout.mutate()}><IconLogout size={18} aria-hidden="true" /> Log out</button>
      </div>

      <div className="grid grid-2">
        <Panel title="Skin">
          <div className="stack" style={{ gap: 10 }}>
            <SkinOption active={me.skin === 'classic'} onClick={() => choose('classic')}
                        title="Classic Krugger" text="Krugger's Tavern as it was: pixel fonts, teal stone, scanlines." />
            <SkinOption active={me.skin === 'modern'} onClick={() => choose('modern')}
                        title="KrugWorld" text="Pirata One and Barlow Condensed, with a background of your choosing." />
            <SkinOption active={me.skin === 'mirage'} onClick={() => choose('mirage')}
                        title="Mirage" text="Modern and smooth: gradient backgrounds, glass panels, clean type." />
          </div>
          {themes.length > 0 && (
            <>
              <h2 style={{ marginTop: 16 }}>Background</h2>
              <div className="row-start wrap">
                {themes.map((t) => (
                  <button key={t.id} aria-pressed={me.theme === t.id} onClick={() => choose(me.skin, t.id)}
                          className={me.theme === t.id ? 'primary' : ''}>
                    <span style={{ width: 14, height: 14, borderRadius: '50%', background: t.swatch, border: '1px solid var(--line)' }} />
                    {t.name}
                  </button>
                ))}
              </div>
            </>
          )}
          <ErrorLine error={save.error} />
        </Panel>

        <EmailSettings />
        <WeatherSettings />
        <DeskGaby />
        <PhoneNotifications />
        <TavernImport />
      </div>
    </>
  )
}

/** The email a forgotten-passphrase link is sent to. Nothing else uses it. */
function EmailSettings() {
  const me = useMe().data
  const save = useSavePreferences()
  const [email, setEmail] = useState(me?.email ?? '')
  const changed = email.trim() !== (me?.email ?? '')

  function submit(e: FormEvent) {
    e.preventDefault()
    save.mutate({ email: email.trim() })
  }

  return (
    <Panel title="Recovery email">
      <p className="small muted" style={{ marginTop: 0 }}>
        If you forget your passphrase, a reset link is sent here. It's used for nothing else.
        {!me?.email && ' You have none set yet, so a forgotten passphrase could not be recovered.'}
      </p>
      <form onSubmit={submit} className="stack">
        <Field label="Email">
          <input type="email" autoComplete="email" value={email}
                 onChange={(e) => { setEmail(e.target.value); save.reset() }} />
        </Field>
        <ErrorLine error={save.error} />
        <div className="row-start">
          <button type="submit" className="primary" disabled={!changed || save.isPending}>
            {save.isPending ? 'Saving…' : 'Save email'}
          </button>
          {save.isSuccess && !changed && <span className="small pos" role="status"><IconCheck size={14} aria-hidden="true" /> Saved</span>}
        </div>
      </form>
    </Panel>
  )
}

function SkinOption({ active, title, text, onClick }: { active: boolean; title: string; text: string; onClick: () => void }) {
  return (
    <button onClick={onClick} aria-pressed={active}
            style={{ textAlign: 'left', justifyContent: 'space-between', borderColor: active ? 'var(--accent)' : undefined }}>
      <span>
        <span className="display" style={{ display: 'block', fontSize: 'var(--fs-h2)' }}>{title}</span>
        <span className="small muted">{text}</span>
      </span>
      {active && <IconCheck size={20} color="var(--accent)" aria-hidden="true" />}
    </button>
  )
}

/** Seconds until `iso`, ticking once a second; 0 once it has passed. */
function useSecondsLeft(iso: string | undefined) {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    if (!iso) return
    const timer = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(timer)
  }, [iso])
  return iso ? Math.max(0, Math.round((Date.parse(iso) - now) / 1000)) : 0
}

function seenLabel(iso: string | null): string {
  if (!iso) return 'Never seen yet'
  const minutes = Math.round((Date.now() - Date.parse(iso)) / 60000)
  if (minutes < 2) return 'Online now'
  if (minutes < 60) return `Seen ${minutes} min ago`
  return `Seen ${new Date(iso).toLocaleString()}`
}

/**
 * Pair the desk device: a six-digit code to type on its screen. The device
 * gets its own token (never this account's password) and can be revoked here.
 */
function DeskGaby() {
  const devices = useDevices()
  const newCode = useNewPairingCode()
  const revoke = useRevokeDevice()
  const pairing = newCode.data
  const secondsLeft = useSecondsLeft(pairing?.expiresAt)
  const live = pairing && secondsLeft > 0
  const list = devices.data ?? []

  // Refresh the list while a code is showing, so a new device appears once it pairs.
  const { refetch } = devices
  useEffect(() => {
    if (!live) return
    const timer = setInterval(() => refetch(), 4000)
    return () => clearInterval(timer)
  }, [live, refetch])

  return (
    <Panel title="Desk Gaby">
      <p className="small muted" style={{ marginTop: 0 }}>
        Pair your desk device so Gaby can show today's numbers and answer out loud, without opening this app.
      </p>
      {live ? (
        <div role="status" style={{ textAlign: 'center', margin: '8px 0 12px' }}>
          <div className="display" style={{ fontSize: 'calc(var(--fs-h1) * 1.4)', letterSpacing: '0.12em' }}>
            {pairing.code.slice(0, 3)} {pairing.code.slice(3)}
          </div>
          <div className="small muted">
            Type this on the device. Expires in {Math.floor(secondsLeft / 60)}:{String(secondsLeft % 60).padStart(2, '0')}.
          </div>
        </div>
      ) : (
        <button className="primary" onClick={() => newCode.mutate()} disabled={newCode.isPending}>
          {pairing ? 'Code expired: make another' : 'Pair a device'}
        </button>
      )}
      <ErrorLine error={newCode.error} />

      {list.length > 0 && (
        <div className="list" style={{ marginTop: 12 }}>
          {list.map((d) => (
            <div key={d.id} className="list-row">
              <span className="grow">
                <b>{d.name}</b>
                <span className="small muted" style={{ display: 'block' }}>{seenLabel(d.lastSeenAt)}</span>
              </span>
              <button className="ghost" aria-label={`Remove ${d.name}`} disabled={revoke.isPending}
                      onClick={() => confirm(`Remove ${d.name}? It will need a new code to come back.`) && revoke.mutate(d.id)}>
                <IconTrash size={18} aria-hidden="true" /> Remove
              </button>
            </div>
          ))}
        </div>
      )}
      <ErrorLine error={devices.error ?? revoke.error} />
    </Panel>
  )
}

const PUSH_SUPPORTED = typeof window !== 'undefined' && 'serviceWorker' in navigator
  && 'PushManager' in window && 'Notification' in window

/** The VAPID public key comes as base64url; pushManager.subscribe wants the raw bytes. */
function base64UrlBytes(text: string): Uint8Array<ArrayBuffer> {
  const base64 = text.replace(/-/g, '+').replace(/_/g, '/')
  return Uint8Array.from(atob(base64 + '='.repeat((4 - (base64.length % 4)) % 4)), (c) => c.charCodeAt(0))
}

/**
 * Web Push: this browser asks permission, subscribes with the server's VAPID
 * key, and hands the subscription to the server, which then sends reminders
 * even while KrugWorld is closed. Each browser is turned on separately.
 */
function PhoneNotifications() {
  const devices = usePushDevices()
  const save = useSavePushSubscription()
  const remove = useRemovePushDevice()
  const test = useTestPush()
  // undefined: still looking; null: the service worker isn't running here (e.g. npm run dev).
  const [registration, setRegistration] = useState<ServiceWorkerRegistration | null | undefined>(undefined)
  const [endpoint, setEndpoint] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [problem, setProblem] = useState<unknown>(null)

  useEffect(() => {
    if (!PUSH_SUPPORTED) return
    let cancelled = false
    navigator.serviceWorker.getRegistration().then(async (reg) => {
      const sub = reg ? await reg.pushManager.getSubscription() : null
      if (cancelled) return
      setRegistration(reg ?? null)
      setEndpoint(sub?.endpoint ?? null)
    })
    return () => { cancelled = true }
  }, [])

  const list = devices.data ?? []
  const here = list.find((d) => d.endpoint === endpoint)
  const blocked = PUSH_SUPPORTED && Notification.permission === 'denied'

  async function turnOn() {
    if (!registration) return
    setBusy(true)
    setProblem(null)
    try {
      if (await Notification.requestPermission() !== 'granted') {
        setProblem(new Error("Notifications weren't allowed. You can allow them in this site's settings."))
        return
      }
      const { publicKey } = await api<{ publicKey: string }>('/api/push/key')
      const sub = await registration.pushManager.getSubscription()
        ?? await registration.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: base64UrlBytes(publicKey) })
      await save.mutateAsync(sub.toJSON())
      setEndpoint(sub.endpoint)
    } catch (e) {
      setProblem(e)
    } finally {
      setBusy(false)
    }
  }

  async function turnOff() {
    setBusy(true)
    setProblem(null)
    try {
      const sub = await registration?.pushManager.getSubscription()
      await sub?.unsubscribe()
      if (here) await remove.mutateAsync(here.id)
      setEndpoint(null)
    } catch (e) {
      setProblem(e)
    } finally {
      setBusy(false)
    }
  }

  return (
    <Panel title="Notifications">
      <p className="small muted" style={{ marginTop: 0 }}>
        Gaby sends a raven to your phone: an event within the hour, meals to log at 2 pm and 10 pm,
        training not done by 7 pm, a streak at risk by 9 pm, spending to log at 11 pm, and a budget
        running low or over.
      </p>

      {!PUSH_SUPPORTED ? (
        <p className="small">This browser can't receive notifications. On iPhone, add KrugWorld to the Home Screen first.</p>
      ) : registration === null ? (
        <p className="small">The app's service worker isn't running here. Reload the page; in local dev it never runs.</p>
      ) : blocked ? (
        <p className="small">Notifications are blocked for this site. Allow them in the browser's site settings, then come back.</p>
      ) : here ? (
        <div className="row-start wrap">
          <span className="small"><IconCheck size={16} aria-hidden="true" /> On for this device ({here.label})</span>
          <button onClick={turnOff} disabled={busy}>Turn off here</button>
        </div>
      ) : (
        <button className="primary" onClick={turnOn} disabled={busy || registration === undefined}>
          <IconBell size={18} aria-hidden="true" /> Turn on for this device
        </button>
      )}
      <ErrorLine error={problem} />

      {list.length > 0 && (
        <>
          <div className="list" style={{ marginTop: 12 }}>
            {list.map((d) => (
              <div key={d.id} className="list-row">
                <span className="grow">
                  <b>{d.label}</b>{d.id === here?.id && <span className="small muted"> (this device)</span>}
                  <span className="small muted" style={{ display: 'block' }}>
                    {d.lastSentAt ? `Last raven ${new Date(d.lastSentAt).toLocaleString()}` : 'No raven sent yet'}
                  </span>
                </span>
                <button className="ghost" aria-label={`Remove ${d.label}`} disabled={remove.isPending || busy}
                        onClick={() => (d.id === here?.id ? turnOff() : remove.mutate(d.id))}>
                  <IconTrash size={18} aria-hidden="true" /> Remove
                </button>
              </div>
            ))}
          </div>
          <div className="row-start wrap" style={{ marginTop: 12 }}>
            <button onClick={() => test.mutate()} disabled={test.isPending}>Send a test</button>
            {test.data && (
              <span className="small muted" role="status">
                {test.data.sent === test.data.devices
                  ? `Sent to ${test.data.sent} device${test.data.sent === 1 ? '' : 's'}.`
                  : `Sent to ${test.data.sent} of ${test.data.devices} devices; the others were gone and are removed.`}
              </span>
            )}
          </div>
        </>
      )}
      <ErrorLine error={devices.error ?? remove.error ?? test.error} />
    </Panel>
  )
}

/** Bring a Krugger's Tavern ledger across, once. */
function TavernImport() {
  const run = useTavernImport()
  const [problem, setProblem] = useState<string | null>(null)

  async function onFile(file: File | undefined) {
    if (!file) return
    setProblem(null)
    try {
      run.mutate(JSON.parse(await file.text()))
    } catch {
      setProblem("That file isn't JSON. Save the page from step 2 as a .json file and try again.")
    }
  }

  const done = run.data
  return (
    <Panel title="Import from Krugger's Tavern">
      <ol className="small muted" style={{ paddingLeft: 18, margin: '0 0 12px' }}>
        <li>Log in to Krugger's Tavern in this browser.</li>
        <li>Open <a href="https://tavern.veteryr.online/api/kv" target="_blank" rel="noreferrer">tavern.veteryr.online/api/kv</a> and save the page (Ctrl+S) as a .json file.</li>
        <li>Choose that file here. Everything comes across: the ledger (into a new "Tavern purse" account), standing dues
          (as recurring entries), the almanac, the feast log, training and the workshop board.</li>
      </ol>
      <label className="btn" style={{ cursor: 'pointer' }}>
        <IconUpload size={18} aria-hidden="true" /> {run.isPending ? 'Importing…' : 'Choose file'}
        <input type="file" accept=".json,application/json" className="sr-only" onChange={(e) => onFile(e.target.files?.[0])} />
      </label>
      {problem && <p className="field-error" role="alert">{problem}</p>}
      <ErrorLine error={run.error} />
      {done && (
        <p role="status">
          Imported {done.entries} ledger entries, {done.recurring} recurring dues, {done.events} events,
          {' '}{done.meals} meals, {done.workouts} workouts and {done.ideas} ideas
          {done.categoriesCreated > 0 && `, with ${done.categoriesCreated} new categories`}
          {done.skipped > 0 && ` (${done.skipped} unreadable rows skipped)`}.
        </p>
      )}
    </Panel>
  )
}

type Found = { name: string; admin1?: string; country?: string; latitude: number; longitude: number }

/** Where the Hall's weather is for: search a place (Open-Meteo, no key) or use this device's location. */
function WeatherSettings() {
  const me = useMe().data
  const save = useSavePreferences()
  const [query, setQuery] = useState('')
  const [found, setFound] = useState<Found[] | null>(null)
  const [problem, setProblem] = useState<string | null>(null)

  async function search(e: FormEvent) {
    e.preventDefault()
    if (!query.trim()) return
    setProblem(null)
    try {
      const res = await fetch(`https://geocoding-api.open-meteo.com/v1/search?count=5&name=${encodeURIComponent(query.trim())}`)
      const json = await res.json()
      setFound(json.results ?? [])
    } catch {
      setProblem('Could not search places right now.')
    }
  }

  function choose(place: string, lat: number, lon: number) {
    save.mutate({ weather: { place, lat: Number(lat.toFixed(5)), lon: Number(lon.toFixed(5)) } })
    setFound(null)
    setQuery('')
  }

  function here() {
    setProblem(null)
    if (!navigator.geolocation) return setProblem('This browser cannot share its location.')
    navigator.geolocation.getCurrentPosition(
      (pos) => choose('Your location', pos.coords.latitude, pos.coords.longitude),
      () => setProblem('Location was not shared. Search for your city instead.'),
      { timeout: 8000 },
    )
  }

  if (!me) return null
  return (
    <Panel title="Weather">
      <p className="small muted" style={{ marginTop: 0 }}>
        {me.weather ? <>Showing the skies over <b>{me.weather.place}</b>.</> : 'No place set yet.'}
      </p>
      <form onSubmit={search} className="row-start">
        <input aria-label="Search a place" placeholder="Kuala Lumpur" value={query} onChange={(e) => setQuery(e.target.value)} />
        <button type="submit">Search</button>
      </form>
      {found && found.length === 0 && <p className="small muted">No such place found.</p>}
      {found && found.length > 0 && (
        <div className="list" style={{ marginTop: 6 }}>
          {found.map((f) => {
            const label = [f.name, f.admin1, f.country].filter(Boolean).join(', ')
            return (
              <button key={`${f.latitude},${f.longitude}`} className="ghost" style={{ justifyContent: 'flex-start' }}
                      onClick={() => choose(label, f.latitude, f.longitude)}>{label}</button>
            )
          })}
        </div>
      )}
      <div className="row-start wrap" style={{ marginTop: 10 }}>
        <button onClick={here}>Use my location</button>
        {me.weather && <button className="ghost" onClick={() => save.mutate({ weather: { place: '' } })}>Clear</button>}
        <span style={{ flex: 1 }} />
        <div className="segmented" role="group" aria-label="Temperature unit">
          <button aria-pressed={me.tempUnit === 'C'} onClick={() => save.mutate({ tempUnit: 'C' })}>°C</button>
          <button aria-pressed={me.tempUnit === 'F'} onClick={() => save.mutate({ tempUnit: 'F' })}>°F</button>
        </div>
      </div>
      {problem && <p className="field-error" role="alert">{problem}</p>}
      <ErrorLine error={save.error} />
    </Panel>
  )
}
