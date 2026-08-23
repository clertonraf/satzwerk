import type { ReactNode } from 'react'
import { Route, Routes } from 'react-router-dom'
import AppShell from '@/components/AppShell'
import AnalyticsPage from '@/pages/AnalyticsPage'
import DashboardPage from '@/pages/DashboardPage'
import HistoryPage from '@/pages/HistoryPage'
import PlansPage from '@/pages/PlansPage'
import ProfilePage from '@/pages/ProfilePage'

function withShell(content: ReactNode) {
  return <AppShell>{content}</AppShell>
}

export default function AppRouter() {
  return (
    <Routes>
      <Route path="/" element={withShell(<DashboardPage />)} />
      <Route path="/analytics" element={withShell(<AnalyticsPage />)} />
      <Route path="/history" element={withShell(<HistoryPage />)} />
      <Route path="/plans" element={withShell(<PlansPage />)} />
      <Route path="/profile" element={withShell(<ProfilePage />)} />
    </Routes>
  )
}
