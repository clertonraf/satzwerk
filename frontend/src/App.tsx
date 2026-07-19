import { Navigate, Route, Routes } from 'react-router-dom'
import AppShell from '@/components/AppShell'
import ProtectedRoute from '@/components/ProtectedRoute'
import LoginPage from '@/features/auth/LoginPage'
import MeasurementsPage from '@/features/measurements/MeasurementsPage'
import MedicationsPage from '@/features/medications/MedicationsPage'
import RegisterPage from '@/features/auth/RegisterPage'
import SessionPage from '@/features/sessions/SessionPage'
import ExercisesPage from '@/features/workouts/ExercisesPage'
import PlanBuilderPage from '@/features/workouts/PlanBuilderPage'
import PlansPage from '@/features/workouts/PlansPage'
import DashboardPage from '@/pages/DashboardPage'
import HistoryPage from '@/pages/HistoryPage'
import ProfilePage from '@/pages/ProfilePage'
import { useOfflineSync } from '@/hooks/useOfflineSync'
import { useRestoreSession } from '@/hooks/useRestoreSession'

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
                  <Route path="/plans" element={<PlansPage />} />
                  <Route path="/plans/:planId" element={<PlanBuilderPage />} />
                  <Route path="/exercises" element={<ExercisesPage />} />
                  <Route path="/profile" element={<ProfilePage />} />
                  <Route path="/measurements" element={<MeasurementsPage />} />
                  <Route path="/medications" element={<MedicationsPage />} />
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
