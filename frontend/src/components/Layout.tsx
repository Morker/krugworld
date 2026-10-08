import { Link, NavLink, Outlet, useLocation } from 'react-router-dom'
import { IconLogout } from '@tabler/icons-react'
import { useLogout } from '../api/hooks'
import { groupFor, NAV } from './nav'

export function Layout() {
  const logout = useLogout()
  const { pathname } = useLocation()
  const current = groupFor(pathname)

  return (
    <div className="shell">
      {/* Desktop: grouped sidebar. Phones: the same element becomes a bottom bar of groups. */}
      <nav className="sidebar" aria-label="Main">
        <div className="brand">
          <h1>KrugWorld</h1>
          <div className="muted small">a realm of your own</div>
        </div>

        {NAV.map((group) => (
          <div key={group.id} className="nav-group">
            {group.label && <div className="nav-group-label">{group.label}</div>}
            {group.items.map(({ to, label, icon: Glyph }) => (
              <NavLink key={to} to={to} end={to === '/'} className="nav-link" aria-label={label}>
                <Glyph size={20} stroke={1.8} aria-hidden="true" />
                <span className="label">{label}</span>
              </NavLink>
            ))}
            {/* Phone tab for the whole group: goes to its first page, lit while you're anywhere in it. */}
            <Link to={group.items[0].to} aria-label={group.label ?? group.items[0].label}
                  className={`nav-group-tab ${current?.id === group.id ? 'active' : ''}`}>
              <group.icon size={22} stroke={1.8} aria-hidden="true" />
              <span>{group.label ?? group.items[0].label}</span>
            </Link>
          </div>
        ))}

        <div className="spacer" />
        <button className="ghost nav-link logout" onClick={() => logout.mutate()}>
          <IconLogout size={20} stroke={1.8} aria-hidden="true" />
          <span className="label">Log out</span>
        </button>
      </nav>

      <main className="main">
        {/* Phones: the current group's pages as tabs along the top. */}
        {current && current.items.length > 1 && (
          <nav className="subnav" aria-label={`${current.label} pages`}>
            {current.items.map(({ to, label }) => (
              <NavLink key={to} to={to} className="chip">{label}</NavLink>
            ))}
          </nav>
        )}
        <Outlet />
      </main>
    </div>
  )
}
