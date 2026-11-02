import {
  IconBook2,
  IconBuildingBank,
  IconBuildingCastle,
  IconBulb,
  IconCalendarEvent,
  IconCoins,
  IconEye,
  IconFlask2,
  IconHeartbeat,
  IconMap2,
  IconMessageChatbot,
  IconPlant2,
  IconRepeat,
  IconSettings,
  IconShieldCheck,
  IconSparkles,
} from '@tabler/icons-react'
import type { Icon } from '@tabler/icons-react'

export type NavItem = { to: string; label: string; icon: Icon }
export type NavGroup = { id: string; label: string | null; icon: Icon; items: NavItem[] }

// The whole app's map. Finance pages are grouped together, life pages
// together; on phones each group is one tab in the bottom bar.
export const NAV: NavGroup[] = [
  { id: 'hall', label: null, icon: IconBuildingCastle, items: [{ to: '/', label: 'Hall', icon: IconBuildingCastle }] },
  {
    id: 'finance',
    label: 'Finance',
    icon: IconCoins,
    items: [
      { to: '/ledger', label: 'Ledger', icon: IconBook2 },
      { to: '/budgets', label: 'Budgets', icon: IconFlask2 },
      { to: '/quests', label: 'Quests', icon: IconMap2 },
      { to: '/recurring', label: 'Recurring', icon: IconRepeat },
      { to: '/accounts', label: 'Accounts', icon: IconBuildingBank },
    ],
  },
  {
    id: 'life',
    label: 'Life',
    icon: IconPlant2,
    items: [
      { to: '/calendar', label: 'Calendar', icon: IconCalendarEvent },
      { to: '/vitals', label: 'Vitals', icon: IconHeartbeat },
      { to: '/workshop', label: 'Workshop', icon: IconBulb },
    ],
  },
  {
    id: 'arcane',
    label: 'Arcane',
    icon: IconSparkles,
    items: [
      { to: '/steward', label: 'Steward', icon: IconMessageChatbot },
      { to: '/oracle', label: "Oracle's Eye", icon: IconEye },
    ],
  },
  { id: 'settings', label: null, icon: IconSettings, items: [{ to: '/settings', label: 'Settings', icon: IconSettings }] },
]

const ADMIN_GROUP: NavGroup = {
  id: 'admin',
  label: null,
  icon: IconShieldCheck,
  items: [{ to: '/admin', label: 'Admin', icon: IconShieldCheck }],
}

/** The map as this account sees it: admins get one more stop, just before Settings. */
export function navFor(admin: boolean): NavGroup[] {
  return admin ? [...NAV.slice(0, -1), ADMIN_GROUP, ...NAV.slice(-1)] : NAV
}

export function groupFor(pathname: string, groups: NavGroup[]): NavGroup | undefined {
  return groups.find((g) => g.items.some((i) => (i.to === '/' ? pathname === '/' : pathname.startsWith(i.to))))
}
