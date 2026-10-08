import { useEffect } from 'react'
import type { Me, Skin, Theme } from '../api/types'

// The skin lives on <html> as data attributes; skins.css does the rest.
// It's also kept in localStorage so the login page (before we know who you
// are) and the first paint after reload already wear your skin.

const KEY = 'krugworld.skin'

type SkinChoice = { skin: Skin; theme: Theme }

export function readStoredSkin(): SkinChoice {
  try {
    const saved = JSON.parse(localStorage.getItem(KEY) ?? 'null') as SkinChoice | null
    if (saved?.skin && saved?.theme) return saved
  } catch {
    // ignore unreadable or blocked storage
  }
  return { skin: 'modern', theme: 'steel' }
}

export function applySkin({ skin, theme }: SkinChoice) {
  document.documentElement.dataset.skin = skin
  document.documentElement.dataset.theme = theme
  try {
    localStorage.setItem(KEY, JSON.stringify({ skin, theme }))
  } catch {
    // storage may be unavailable (private mode); the skin still applies
  }
}

/** Keeps <html> in step with the logged-in account's saved preference. */
export function useAccountSkin(me: Me | null | undefined) {
  useEffect(() => {
    if (me) applySkin({ skin: me.skin, theme: me.theme })
  }, [me])
}

export const THEMES: { id: Theme; name: string; swatch: string }[] = [
  { id: 'steel', name: "Knight's steel", swatch: '#151a22' },
  { id: 'oak', name: 'Tavern oak', swatch: '#1b1410' },
  { id: 'forest', name: "Ranger's forest", swatch: '#0f1c17' },
  { id: 'parchment', name: 'Parchment', swatch: '#f3ead6' },
]
