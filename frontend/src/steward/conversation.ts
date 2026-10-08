import { useSyncExternalStore } from 'react'
import type { StewardTurn } from '../api/types'

// One conversation shared by the docked panel and the Steward page, kept in
// this tab's sessionStorage (the server keeps no chat state).

const KEY = 'krugworld.steward'

export const GREETING: StewardTurn = {
  role: 'assistant',
  text: 'Well met, traveller! Barnaby, at thy service. I keep thy ledger, thy almanac, thy feast and thy training. What wouldst thou have done?',
  subtitle: "Hi, I'm Barnaby. I can log spending, add events, track meals and workouts, and more. What do you need?",
}

type State = { turns: StewardTurn[]; pending: boolean; repliedAt: number }

function load(): StewardTurn[] {
  try {
    const saved = JSON.parse(sessionStorage.getItem(KEY) ?? 'null') as StewardTurn[] | null
    if (Array.isArray(saved) && saved.length) return saved
  } catch {
    // unreadable or blocked storage: start fresh
  }
  return [GREETING]
}

let state: State = { turns: load(), pending: false, repliedAt: 0 }
const listeners = new Set<() => void>()

function set(next: Partial<State>) {
  state = { ...state, ...next }
  try {
    sessionStorage.setItem(KEY, JSON.stringify(state.turns.slice(-40)))
  } catch {
    // storage unavailable: the chat still works for this visit
  }
  listeners.forEach((l) => l())
}

export const conversation = {
  ask(text: string) {
    set({ turns: [...state.turns, { role: 'user', text }], pending: true })
  },
  answer(turn: StewardTurn) {
    set({ turns: [...state.turns, turn], pending: false, repliedAt: Date.now() })
  },
  reset() {
    set({ turns: [GREETING], pending: false })
  },
  /** What to send as history: everything before the newest message, minus the greeting. */
  historyBeforeLast(): StewardTurn[] {
    return state.turns.slice(1, -1)
  },
}

export function useConversation(): State {
  return useSyncExternalStore(
    (l) => {
      listeners.add(l)
      return () => listeners.delete(l)
    },
    () => state,
  )
}
