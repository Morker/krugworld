import { Navigate, Route, Routes } from 'react-router-dom'
import { useMe } from './api/hooks'
import { Layout } from './components/Layout'
import { ErrorLine } from './components/ui'
import { AccountsPage } from './pages/AccountsPage'
import { LoginPage, RegisterPage } from './pages/AuthPages'
import { BudgetsPage } from './pages/BudgetsPage'
import { CalendarPage } from './pages/CalendarPage'
import { HallPage } from './pages/HallPage'
import { LedgerPage } from './pages/LedgerPage'
import { OraclePage } from './pages/OraclePage'
import { QuestsPage } from './pages/QuestsPage'
import { RecurringPage } from './pages/RecurringPage'
import { SettingsPage } from './pages/SettingsPage'
import { StewardPage } from './pages/StewardPage'
import { VitalsPage } from './pages/VitalsPage'
import { WorkshopPage } from './pages/WorkshopPage'
import { useAccountSkin } from './skin/skin'

export default function App() {
  const { data: me, isLoading, error } = useMe()
  useAccountSkin(me)

  if (isLoading) return null
  if (error) {
    return <div className="auth"><section className="panel"><h1>KrugWorld</h1><ErrorLine error={error} /></section></div>
  }

  // Logged out: only the door is reachable. Logged in: the door sends you inside.
  if (!me) {
    return (
      <Routes>
        <Route path="/register" element={<RegisterPage />} />
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
