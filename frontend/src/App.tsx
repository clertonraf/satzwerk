import { Navigate, Route, Routes } from 'react-router-dom'
import AppShell from '@/components/AppShell'
import ProtectedRoute from '@/components/ProtectedRoute'
import LoginPage from '@/features/auth/LoginPage'
import RegisterPage from '@/features/auth/RegisterPage'
import SessionPage from '@/features/sessions/SessionPage'
import ExercisesPage from '@/features/workouts/ExercisesPage'
import PlanBuilderPage from '@/features/workouts/PlanBuilderPage'
import PlansPage from '@/features/workouts/PlansPage'
import DashboardPage from '@/pages/DashboardPage'
import HistoryPage from '@/pages/HistoryPage'
import ProfilePage from '@/pages/ProfilePage'
import { useOfflineSync } from '@/hooks/useOfflineSync'

export default function App() {
  useOfflineSync()

  return (
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
                <Route path="*" element={<Navigate to="/" replace />} />
              </Routes>
            </AppShell>
          </ProtectedRoute>
        }
      />
    </Routes>
  )
}
