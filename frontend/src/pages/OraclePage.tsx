import { useState } from 'react'
import type { FormEvent } from 'react'
import { IconEye } from '@tabler/icons-react'
import { Bar, Field, Panel } from '../components/ui'

const KEY = 'krugworld.oracle-key'

type Day = { day: string; cost: number; tokens: number }
type Gaze = { days: Day[]; totalCost: number; totalTokens: number }

type Bucket = { starting_at?: string; results?: Record<string, unknown>[] }

// Pages through an Anthropic Admin API report. Called straight from this
// browser: the admin key never reaches KrugWorld's server.
async function fetchAll(url: URL, key: string): Promise<Bucket[]> {
  const out: Bucket[] = []
  let page: string | null = null
  for (let i = 0; i < 20; i++) {
    if (page) url.searchParams.set('page', page)
    const res = await fetch(url, {
      headers: {
        'x-api-key': key,
        'anthropic-version': '2023-06-01',
        'anthropic-dangerous-direct-browser-access': 'true',
      },
    })
    if (!res.ok) {
      const body = await res.json().catch(() => null)
      throw new Error(body?.error?.message ?? `Anthropic answered HTTP ${res.status}`)
    }
    const json = await res.json()
    out.push(...(json.data ?? []))
    if (!json.has_more || !json.next_page) break
    page = json.next_page
  }
  return out
}

function tokensIn(result: Record<string, unknown>): number {
  return Object.entries(result).reduce((sum, [k, v]) => (/token/i.test(k) && typeof v === 'number' ? sum + v : sum), 0)
}

/** Oracle's Eye: what your Anthropic organisation spent on Claude, day by day. */
export function OraclePage() {
  const [key, setKey] = useState(() => {
    try {
      return localStorage.getItem(KEY) ?? ''
    } catch {
      return ''
    }
  })
  const [days, setDays] = useState(7)
  const [gaze, setGaze] = useState<Gaze | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  async function look(e: FormEvent) {
    e.preventDefault()
    if (!key.trim()) return setError('Enter an Admin API key first.')
    try {
      localStorage.setItem(KEY, key.trim())
    } catch {
      // not remembered, still usable now
    }
    setError(null)
    setLoading(true)
    try {
      const end = new Date()
      const start = new Date(end.getTime() - days * 86_400_000)
      const range = (path: string) => {
        const u = new URL(`https://api.anthropic.com/v1/organizations/${path}`)
        u.searchParams.set('starting_at', start.toISOString())
        u.searchParams.set('ending_at', end.toISOString())
        u.searchParams.set('bucket_width', '1d')
        return u
      }
      const [usage, cost] = await Promise.all([
        fetchAll(range('usage_report/messages'), key.trim()),
        fetchAll(range('cost_report'), key.trim()),
      ])
      const byDay = new Map<string, Day>()
      const get = (d: string) => byDay.get(d) ?? byDay.set(d, { day: d, cost: 0, tokens: 0 }).get(d)!
      usage.forEach((b) => (b.results ?? []).forEach((r) => { get((b.starting_at ?? '').slice(0, 10)).tokens += tokensIn(r) }))
      // Cost amounts are in cents.
      cost.forEach((b) => (b.results ?? []).forEach((r) => {
        const cents = parseFloat(String(r.amount ?? ''))
        if (!Number.isNaN(cents)) get((b.starting_at ?? '').slice(0, 10)).cost += cents / 100
      }))
      const list = [...byDay.values()].sort((a, b) => a.day.localeCompare(b.day))
      setGaze({ days: list, totalCost: list.reduce((s, d) => s + d.cost, 0), totalTokens: list.reduce((s, d) => s + d.tokens, 0) })
    } catch (err) {
      setError(err instanceof Error ? err.message : 'The oracle could not see.')
    } finally {
      setLoading(false)
    }
  }

  const peak = gaze ? Math.max(0.0001, ...gaze.days.map((d) => d.cost)) : 1

  return (
    <>
      <div className="page-head">
        <div>
          <h1>Oracle's Eye</h1>
          <div className="sub">What your Claude usage has cost, day by day</div>
        </div>
      </div>

      <div className="grid grid-2">
        <Panel title="Consult the oracle">
          <form onSubmit={look} className="stack" style={{ gap: 12 }}>
            <Field label="Anthropic Admin API key">
              <input type="password" autoComplete="off" placeholder="sk-ant-admin01-…" value={key} onChange={(e) => setKey(e.target.value)} />
            </Field>
            <Field label="Period">
              <select value={days} onChange={(e) => setDays(Number(e.target.value))}>
                <option value={7}>Last 7 days</option>
                <option value={14}>Last 14 days</option>
                <option value={30}>Last 30 days</option>
              </select>
            </Field>
            <button type="submit" className="primary" disabled={loading}>
              <IconEye size={18} aria-hidden="true" /> {loading ? 'The oracle stares into the void…' : 'Gaze'}
            </button>
            {error && <p className="field-error" role="alert">{error}</p>}
            <p className="small muted" style={{ margin: 0 }}>
              Needs an <b>Admin</b> key (sk-ant-admin…), which only organisations have; regular keys can't see usage.
              The key is kept in this browser only and sent straight to Anthropic, never to KrugWorld's server.
              The Console's <a href="https://platform.claude.com/usage" target="_blank" rel="noreferrer">usage page</a> always works too.
            </p>
          </form>
        </Panel>

        <Panel title="The vision">
          {!gaze && <p className="muted">Gaze to see your spending.</p>}
          {gaze && (
            <>
              <div className="grid grid-2" style={{ marginBottom: 10 }}>
                <div className="stat"><span className="label">Spent</span><span className="value num gold">${gaze.totalCost.toFixed(2)}</span></div>
                <div className="stat"><span className="label">Tokens</span><span className="value num">{gaze.totalTokens.toLocaleString()}</span></div>
              </div>
              {gaze.days.length === 0 && <p className="muted">No usage in this period.</p>}
              {gaze.days.map((d) => (
                <div key={d.day}>
                  <div className="row small"><span>{d.day}</span><span className="num">${d.cost.toFixed(2)} · {d.tokens.toLocaleString()} tok</span></div>
                  <Bar value={d.cost} max={peak} color="var(--mp)" thin label={`Cost on ${d.day}`} />
                </div>
              ))}
            </>
          )}
        </Panel>
      </div>
    </>
  )
}
