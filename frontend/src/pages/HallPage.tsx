import { useId, useState } from 'react'
import { Link } from 'react-router-dom'
import { IconArrowDownLeft, IconArrowUpRight, IconArrowsLeftRight, IconBolt, IconHeart, IconLock, IconPlus } from '@tabler/icons-react'
import { useCharacter } from '../api/hooks'
import type { CharacterSheet, Entry } from '../api/types'
import { EntryForm } from '../components/EntryForm'
import { AwaitingSeal, DebtsPanel, TodayPanel, WeatherChip } from '../components/HallWidgets'
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
      {/* The weather rides in the title bar rather than taking a row of their own. */}
      <div className="page-head">
        <div>
          <h1>The Hall</h1>
          <div className="sub">{greeting()}, {sheet.username} · {monthLabel(currentMonth())}</div>
        </div>
        <div className="head-widgets">
          <WeatherChip />
          <button className="primary" onClick={() => setAdding(true)}>
            <IconPlus size={18} aria-hidden="true" /> Log entry
          </button>
        </div>
      </div>

      {/*
        Read it like a character sheet: on the left, who you are and what you're
        after (hero + quests); along the top, the state of your purse (vitals and
        debts, side by side); below, what wants doing today, then what's happened.
      */}
      <div className="hall">
        <div className="stack hall-hero">
          <CharacterCard sheet={sheet} />
          <ActiveQuests sheet={sheet} />
        </div>
        <div className="hall-realm">
          <div className="realm-grid">
            <VitalStats sheet={sheet} />
            <DebtsPanel />
          </div>
        </div>
        <div className="stack hall-deeds">
          <AwaitingSeal onSealed={() => showToast('Sealed · +5 XP')} />
          <TodayPanel />
        </div>
        <div className="hall-chronicle">
          <LootLog loot={sheet.loot.slice(0, 5)} />
        </div>
      </div>

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
        <div className="avatar-wrap">
          <div className="avatar-ring"
               style={{ background: `conic-gradient(var(--accent) 0 ${ring}%, var(--track) ${ring}% 100%)` }}>
            <div className="avatar" aria-hidden="true">{sheet.username.charAt(0).toUpperCase()}</div>
          </div>
          <StreakFlame streak={sheet.streak} />
        </div>
        <span className="level-pill">Lv {sheet.level}</span>
        <h2 style={{ marginTop: 6, marginBottom: 0 }}>{sheet.username}</h2>
        <div className="muted small">{sheet.title}</div>
        <div className="row small muted" style={{ marginTop: 6 }}>
          <span>XP</span>
          <span className="num">{sheet.levelXp} / {sheet.levelXpNeeded}</span>
        </div>
        <Bar value={sheet.levelXp} max={sheet.levelXpNeeded} color="var(--accent)" label="XP to next level" />
      </div>
      <div className="badges">
        {sheet.badges.map((b) => (
          <span key={b.id} className={`badge ${b.earned ? '' : 'locked'}`}
                title={`${b.name}${b.earned ? '' : ' (locked)'}: ${b.description}`}>
            {b.earned ? <BadgeIcon icon={b.icon} size={17} /> : <IconLock size={14} aria-hidden="true" />}
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
          <div key={q.id} style={{ marginBottom: 4 }}>
            <div className="row">
              <span className="row-start"><span className="gold"><PlainIcon icon={q.icon} /></span> {q.name}</span>
              <span className="num gold">+{q.xpReward} XP</span>
            </div>
            <Bar value={q.savedAmount} max={q.targetAmount} color="var(--ok)" thin label={`${q.name} progress`} />
            <div className="small muted num" style={{ marginTop: -5 }}>
              <Money value={q.savedAmount} /> / <Money value={q.targetAmount} />
            </div>
          </div>
        ))
      )}
    </Panel>
  )
}

/**
 * The streak as one flame on the hero's portrait, its day count burnt into it.
 * Burning: you've done something today. Flickering low: the streak is alive
 * but today is still empty. Cold: no streak.
 */
function StreakFlame({ streak }: { streak: CharacterSheet['streak'] }) {
  const id = useId()
  const today = streak.last7[streak.last7.length - 1]
  const state = streak.current === 0 ? 'cold' : today ? 'lit' : 'low'
  const days = `${streak.current}-day streak`
  const hint = state === 'cold' ? 'Do something today to light it.'
    : state === 'low' ? 'Do something today to keep it burning.' : 'Burning bright today.'
  const label = `${days}. ${hint} Best ${streak.longest}. Active ${streak.last7.filter(Boolean).length} of the last 7 days.`
  return (
    <span className={`streak-flame ${state}`} title={label} role="img" aria-label={label}>
      <svg viewBox="0 0 32 40" width="34" height="42" aria-hidden="true">
        <defs>
          <linearGradient id={`${id}-outer`} x1="0" y1="1" x2="0" y2="0">
            <stop offset="0" className="flame-stop-a" />
            <stop offset="1" className="flame-stop-b" />
          </linearGradient>
        </defs>
        <path className="flame-outer" fill={`url(#${id}-outer)`}
              d="M16 1C18 8 26 12 27 23C28 33 22 39 16 39S4 33 5 24C5.5 18 9 15 10 10C12 14 13 16 14 16C13 10 14 5 16 1Z" />
        <path className="flame-core"
              d="M16 17C18 21 23 24 23 30C23 35 20 38 16 38S9 35 9 31C9 27 12 25 13 22C14.5 24 15 25 16 25C15.5 22 15.5 19 16 17Z" />
      </svg>
      <span className="streak-count num">{streak.current}</span>
    </span>
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
    <div className="list-row" style={{ padding: '4px 2px' }}>
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
