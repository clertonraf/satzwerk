import { Navigate, Route, Routes, useParams } from 'react-router-dom'
import AppShell from '@/components/AppShell'
import ProtectedRoute from '@/components/ProtectedRoute'
import LoginPage from '@/features/auth/LoginPage'
import HealthPage from '@/features/health/HealthPage'
import RegisterPage from '@/features/auth/RegisterPage'
import SessionPage from '@/features/sessions/SessionPage'
import PlanBuilderPage from '@/features/workouts/PlanBuilderPage'
import WorkoutsPage from '@/features/workouts/WorkoutsPage'
import DashboardPage from '@/pages/DashboardPage'
import HistoryPage from '@/pages/HistoryPage'
import SettingsPage from '@/pages/SettingsPage'
import { useOfflineSync } from '@/hooks/useOfflineSync'
import { useRestoreSession } from '@/hooks/useRestoreSession'

function PlanBuilderRedirect() {
  const { planId } = useParams<{ planId: string }>()
  return <Navigate to={`/workouts/plans/${planId}`} replace />
}

export default function App() {
  const { flushError, dismissFlushError } = useOfflineSync()
  useRestoreSession()

  return (
    <>
      {flushError !== null ? (
        <div
          role="alert"
          className="fixed top-0 left-0 right-0 z-50 flex items-center justify-between bg-destructive px-4 py-2 text-sm text-destructive-foreground"
        >
          <span>{flushError}</span>
          <button
            type="button"
            aria-label="Dismiss"
            className="ml-4 font-semibold underline"
            onClick={dismissFlushError}
          >
            Dismiss
          </button>
        </div>
      ) : null}
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route
          path="/*"
          element={
            <ProtectedRoute>
              <AppShell>
                <Routes>
                  <Route path="/" element={<DashboardPage />} />
                  <Route path="/history" element={<HistoryPage />} />
                  <Route path="/session" element={<SessionPage />} />
                  <Route path="/workouts" element={<WorkoutsPage />} />
                  <Route path="/workouts/exercises" element={<WorkoutsPage />} />
                  <Route path="/workouts/plans/:planId" element={<PlanBuilderPage />} />
                  <Route path="/plans/:planId" element={<PlanBuilderRedirect />} />
                  <Route path="/health" element={<HealthPage />} />
                  <Route path="/health/measurements" element={<HealthPage />} />
                  <Route path="/settings" element={<SettingsPage />} />
                  {/* Legacy redirects — preserve existing deep links */}
                  <Route path="/plans" element={<Navigate to="/workouts" replace />} />
                  <Route path="/exercises" element={<Navigate to="/workouts/exercises" replace />} />
                  <Route path="/medications" element={<Navigate to="/health" replace />} />
                  <Route path="/measurements" element={<Navigate to="/health/measurements" replace />} />
                  <Route path="/profile" element={<Navigate to="/settings" replace />} />
                  <Route path="*" element={<Navigate to="/" replace />} />
                </Routes>
              </AppShell>
            </ProtectedRoute>
          }
        />
      </Routes>
    </>
  )
}
