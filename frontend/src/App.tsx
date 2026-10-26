import { lazy } from 'react'
import type { ComponentType } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import { useMe } from './api/hooks'
import { Layout } from './components/Layout'
import { ErrorLine, Loading } from './components/ui'
import { ForgotPage, LoginPage, RegisterPage, ResetPage } from './pages/AuthPages'
import { HallPage } from './pages/HallPage'
import { useAccountSkin } from './skin/skin'

// The Hall is the landing page, so it ships with the app. Every other page is
// its own chunk, fetched the first time you open it (Layout shows skeleton
// bars meanwhile), which keeps the first load small on a phone.
function page<K extends string>(load: () => Promise<Record<K, ComponentType>>, name: K) {
  return lazy(() => load().then((m) => ({ default: m[name] })))
}

const LedgerPage = page(() => import('./pages/LedgerPage'), 'LedgerPage')
const BudgetsPage = page(() => import('./pages/BudgetsPage'), 'BudgetsPage')
const QuestsPage = page(() => import('./pages/QuestsPage'), 'QuestsPage')
const RecurringPage = page(() => import('./pages/RecurringPage'), 'RecurringPage')
const DebtsPage = page(() => import('./pages/DebtsPage'), 'DebtsPage')
const AccountsPage = page(() => import('./pages/AccountsPage'), 'AccountsPage')
const CalendarPage = page(() => import('./pages/CalendarPage'), 'CalendarPage')
const VitalsPage = page(() => import('./pages/VitalsPage'), 'VitalsPage')
const WorkshopPage = page(() => import('./pages/WorkshopPage'), 'WorkshopPage')
const StewardPage = page(() => import('./pages/StewardPage'), 'StewardPage')
const OraclePage = page(() => import('./pages/OraclePage'), 'OraclePage')
const SettingsPage = page(() => import('./pages/SettingsPage'), 'SettingsPage')

export default function App() {
  const { data: me, isLoading, error } = useMe()
  useAccountSkin(me)

  if (isLoading) return <div className="auth"><Loading rows={2} /></div>
  if (error) {
    return <div className="auth"><section className="panel"><h1>KrugWorld</h1><ErrorLine error={error} /></section></div>
  }

  // Logged out: only the door is reachable. Logged in: the door sends you inside.
  if (!me) {
    return (
      <Routes>
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/forgot-password" element={<ForgotPage />} />
        <Route path="/reset-password" element={<ResetPage />} />
        <Route path="*" element={<LoginPage />} />
      </Routes>
    )
  }

  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<HallPage />} />
        <Route path="ledger" element={<LedgerPage />} />
        <Route path="budgets" element={<BudgetsPage />} />
        <Route path="quests" element={<QuestsPage />} />
        <Route path="recurring" element={<RecurringPage />} />
        <Route path="debts" element={<DebtsPage />} />
        <Route path="accounts" element={<AccountsPage />} />
        <Route path="calendar" element={<CalendarPage />} />
        <Route path="vitals" element={<VitalsPage />} />
        <Route path="workshop" element={<WorkshopPage />} />
        <Route path="steward" element={<StewardPage />} />
        <Route path="oracle" element={<OraclePage />} />
        <Route path="settings" element={<SettingsPage />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
