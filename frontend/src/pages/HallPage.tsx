import { useState } from 'react'
import { Link } from 'react-router-dom'
import { IconArrowDownLeft, IconArrowUpRight, IconArrowsLeftRight, IconBolt, IconFlame, IconHeart, IconLock, IconPlus } from '@tabler/icons-react'
import { useCharacter } from '../api/hooks'
import type { CharacterSheet, Entry } from '../api/types'
import { EntryForm } from '../components/EntryForm'
import { MarketCard, TodayRow, WeatherCard } from '../components/HallWidgets'
import { BadgeIcon, PlainIcon } from '../components/icons'
import { Bar, Empty, ErrorLine, Loading, Money, Panel } from '../components/ui'
import { useToast } from '../components/useToast'
import { monthLabel, percent, signed, currentMonth } from '../lib/format'

function greeting(): string {
  const h = new Date().getHours()
  return h < 5 ? 'Burning the midnight oil' : h < 12 ? 'Good morning' : h < 18 ? 'Good afternoon' : 'Good evening'
}

/** The dashboard: your life as an RPG character sheet. */
export function HallPage() {
  const { data: sheet, error, isLoading } = useCharacter()
  const [adding, setAdding] = useState(false)
  const { toast, showToast } = useToast()

  if (isLoading) return <Loading />
  if (!sheet) return <ErrorLine error={error} />

  return (
    <>
      <div className="page-head">
        <div>
          <h1>The Hall</h1>
          <div className="sub">{greeting()}, {sheet.username} · {monthLabel(currentMonth())}</div>
        </div>
        <button className="primary" onClick={() => setAdding(true)}>
          <IconPlus size={18} aria-hidden="true" /> Log entry
        </button>
      </div>

      <div className="grid grid-2" style={{ marginBottom: 14 }}>
        <WeatherCard />
        <MarketCard />
      </div>

      <div className="grid sheet">
        <CharacterCard sheet={sheet} />
        <div className="stack">
          <VitalStats sheet={sheet} />
          <ActiveQuests sheet={sheet} />
        </div>
      </div>

      <div className="grid sheet" style={{ marginTop: 14 }}>
        <StreakCard sheet={sheet} />
        <LootLog loot={sheet.loot} />
      </div>

      <TodayRow />

      {adding && (
        <EntryForm onClose={() => setAdding(false)} onSaved={(isNew) => isNew && showToast('Logged · +5 XP')} />
      )}
      {toast}
    </>
  )
}

function CharacterCard({ sheet }: { sheet: CharacterSheet }) {
  const ring = percent(sheet.levelXp, sheet.levelXpNeeded)
  return (
    <Panel>
      <div style={{ textAlign: 'center' }}>
        <div className="avatar-ring"
             style={{ background: `conic-gradient(var(--accent) 0 ${ring}%, var(--track) ${ring}% 100%)` }}>
          <div className="avatar" aria-hidden="true">{sheet.username.charAt(0).toUpperCase()}</div>
        </div>
        <span className="level-pill">Lv {sheet.level}</span>
        <h2 style={{ marginTop: 8, marginBottom: 0 }}>{sheet.username}</h2>
        <div className="muted">{sheet.title}</div>
        <div className="row small muted" style={{ marginTop: 10 }}>
          <span>XP</span>
          <span className="num">{sheet.levelXp} / {sheet.levelXpNeeded}</span>
        </div>
        <Bar value={sheet.levelXp} max={sheet.levelXpNeeded} color="var(--accent)" label="XP to next level" />
      </div>
      <div className="badges" style={{ marginTop: 6 }}>
        {sheet.badges.map((b) => (
          <span key={b.id} className={`badge ${b.earned ? '' : 'locked'}`}
                title={`${b.name}${b.earned ? '' : ' (locked)'}: ${b.description}`}>
            {b.earned ? <BadgeIcon icon={b.icon} /> : <IconLock size={18} aria-hidden="true" />}
            <span className="sr-only">{b.name}{b.earned ? '' : ', locked'}</span>
          </span>
        ))}
      </div>
    </Panel>
  )
}

function VitalStats({ sheet }: { sheet: CharacterSheet }) {
  const { budget } = sheet
  // Health = how much of this month's money you still hold.
  const hpMax = Math.max(sheet.gold, 0) + sheet.spentThisMonth
  return (
    <Panel title="Vital stats">
      <div className="row">
        <span className="row-start"><IconHeart size={18} color="var(--hp)" aria-hidden="true" /> Gold</span>
        <Money value={sheet.gold} className="gold" />
      </div>
      <Bar value={Math.max(sheet.gold, 0)} max={hpMax} color="var(--hp)" label="Gold remaining this month" />

      <div className="row">
        <span className="row-start"><IconBolt size={18} color="var(--mp)" aria-hidden="true" /> Budget mana</span>
        {budget.totalBudget > 0 ? (
          <span className="num">
            <Money value={budget.left} /> left <span className="muted">· {budget.daysLeft} days</span>
          </span>
        ) : (
          <Link to="/budgets" className="small">Set a budget</Link>
        )}
      </div>
      <Bar value={Math.max(budget.left, 0)} max={budget.totalBudget} color="var(--mp)" label="Budget left this month" />

      <div className="row small muted">
        <span>This month: <span className="pos num">{signed(sheet.incomeThisMonth, false)}</span> in</span>
        <span><span className="neg num">{signed(sheet.spentThisMonth, true)}</span> out</span>
      </div>
    </Panel>
  )
}

function ActiveQuests({ sheet }: { sheet: CharacterSheet }) {
  return (
    <Panel title="Active quests" action={<Link to="/quests" className="small">All quests</Link>}>
      {sheet.quests.length === 0 ? (
        <Empty>
          No quest yet. Saving for something?
          <div><Link to="/quests" className="btn">Start a quest</Link></div>
        </Empty>
      ) : (
        sheet.quests.map((q) => (
          <div key={q.id} style={{ marginBottom: 6 }}>
            <div className="row">
              <span className="row-start"><span className="gold"><PlainIcon icon={q.icon} /></span> {q.name}</span>
              <span className="num gold">+{q.xpReward} XP</span>
            </div>
            <Bar value={q.savedAmount} max={q.targetAmount} color="var(--ok)" thin label={`${q.name} progress`} />
            <div className="small muted num" style={{ marginTop: -6 }}>
              <Money value={q.savedAmount} /> / <Money value={q.targetAmount} />
            </div>
          </div>
        ))
      )}
    </Panel>
  )
}

function StreakCard({ sheet }: { sheet: CharacterSheet }) {
  const { streak } = sheet
  const remaining = 7 - (streak.current % 7)
  return (
    <Panel title="Streak">
      <div className="flames" aria-label={`Logged on ${streak.last7.filter(Boolean).length} of the last 7 days`}>
        {streak.last7.map((lit, i) => (
          <IconFlame key={i} size={22} className={lit ? 'lit' : 'unlit'} aria-hidden="true" />
        ))}
      </div>
      <div className="small muted" style={{ marginTop: 6 }}>
        {streak.current > 0
          ? `${streak.current} day${streak.current === 1 ? '' : 's'} · ${remaining === 7 ? 'week complete!' : `${remaining} to a full week`}`
          : 'Log something today to light a flame.'}
      </div>
      <div className="small muted">Longest: {streak.longest}</div>
    </Panel>
  )
}

export function LootLog({ loot }: { loot: Entry[] }) {
  return (
    <Panel title="Loot log" action={<Link to="/ledger" className="small">Full ledger</Link>}>
      {loot.length === 0 ? (
        <Empty>Your ledger is empty. Log your first coin for a badge.</Empty>
      ) : (
        <div className="list">
          {loot.map((e) => <LootRow key={e.id} entry={e} />)}
        </div>
      )}
    </Panel>
  )
}

function LootRow({ entry }: { entry: Entry }) {
  const out = entry.type === 'EXPENSE'
  const Arrow = entry.type === 'TRANSFER' ? IconArrowsLeftRight : out ? IconArrowUpRight : IconArrowDownLeft
  const label = entry.note || entry.category?.name || 'Transfer'
  return (
    <div className="list-row" style={{ padding: '5px 2px' }}>
      <Arrow size={18} color={entry.type === 'TRANSFER' ? 'var(--mp)' : out ? 'var(--hp)' : 'var(--ok)'} aria-hidden="true" />
      <span className="grow title">
        {label}
        {entry.category && entry.note && <span className="muted"> · {entry.category.name}</span>}
      </span>
      <span className={`num ${entry.type === 'INCOME' ? 'pos' : ''}`}>
        {entry.type === 'TRANSFER' ? signed(entry.amount, false).slice(1) : signed(entry.amount, out)}
      </span>
    </div>
  )
}
