import { NavLink, Outlet } from 'react-router-dom'
import {
  IconBook2,
  IconBuildingBank,
  IconBuildingCastle,
  IconFlask2,
  IconLogout,
  IconMap2,
  IconRepeat,
  IconSettings,
} from '@tabler/icons-react'
import type { Icon } from '@tabler/icons-react'
import { useLogout } from '../api/hooks'

const NAV: { to: string; label: string; icon: Icon; secondary?: boolean }[] = [
  { to: '/', label: 'Hall', icon: IconBuildingCastle },
  { to: '/ledger', label: 'Ledger', icon: IconBook2 },
  { to: '/budgets', label: 'Budgets', icon: IconFlask2 },
  { to: '/quests', label: 'Quests', icon: IconMap2 },
  { to: '/recurring', label: 'Recurring', icon: IconRepeat },
  { to: '/accounts', label: 'Accounts', icon: IconBuildingBank },
  { to: '/settings', label: 'Settings', icon: IconSettings },
]

export function Layout() {
  const logout = useLogout()
  return (
    <div className="shell">
      <nav className="sidebar" aria-label="Main">
        <div className="brand">
          <h1>KrugWorld</h1>
          <div className="muted small">a realm of your own</div>
        </div>
        {NAV.map(({ to, label, icon: Glyph, secondary }) => (
          <NavLink key={to} to={to} end={to === '/'} className={`nav-link ${secondary ? 'secondary' : ''}`}
                   aria-label={label}>
            <Glyph size={20} stroke={1.8} aria-hidden="true" />
            <span className="label">{label}</span>
          </NavLink>
        ))}
        <div className="spacer" />
        <button className="ghost nav-link secondary" onClick={() => logout.mutate()}>
          <IconLogout size={20} stroke={1.8} aria-hidden="true" />
          <span className="label">Log out</span>
        </button>
      </nav>
      <main className="main">
        <Outlet />
      </main>
    </div>
  )
}
