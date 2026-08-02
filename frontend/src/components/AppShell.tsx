import { useEffect, useState, type ReactNode } from 'react'
import { Dumbbell, Heart, Home, MoonStar, Settings, SunMedium } from 'lucide-react'
import { NavLink, useLocation, useMatch } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'

interface AppShellProps {
  children: ReactNode
}

const navigationItems = [
  { to: '/', label: 'Dashboard', icon: Home },
  { to: '/workouts', label: 'Workouts', icon: Dumbbell },
  { to: '/health', label: 'Health', icon: Heart },
  { to: '/settings', label: 'Settings', icon: Settings },
] as const

const PAGE_TITLES: Record<string, string> = {
  ...Object.fromEntries(navigationItems.map(({ to, label }) => [to, label])),
  '/workouts/exercises': 'Workouts',
  '/workouts/history': 'Workouts',
  '/health/measurements': 'Health',
  '/session': 'Session',
  // Legacy redirect paths — titles shown during the brief render before <Navigate> fires
  '/plans': 'Workouts',
  '/exercises': 'Workouts',
  '/history': 'Workouts',
  '/medications': 'Health',
  '/measurements': 'Health',
  '/profile': 'Settings',
}

function usePageTitle(): string {
  const { pathname } = useLocation()
  const isCanonicalPlanBuilder = useMatch('/workouts/plans/:planId')
  const isLegacyPlanBuilder = useMatch('/plans/:planId')
  if (isCanonicalPlanBuilder || isLegacyPlanBuilder) return 'Plan Builder'
  return PAGE_TITLES[pathname] ?? 'Satzwerk'
}

export default function AppShell({ children }: AppShellProps) {
  const [isDarkMode, setIsDarkMode] = useState(true)
  const pageTitle = usePageTitle()

  useEffect(() => {
    document.documentElement.classList.toggle('dark', isDarkMode)
  }, [isDarkMode])

  useEffect(() => {
    document.title = pageTitle === 'Satzwerk' ? 'Satzwerk' : `${pageTitle} | Satzwerk`
  }, [pageTitle])

  useEffect(() => {
    return () => {
      document.title = 'Satzwerk'
    }
  }, [])

  return (
    <div className="min-h-screen bg-background text-foreground">
      <div className="mx-auto flex min-h-screen max-w-6xl bg-background">
        <aside className="hidden w-72 border-r border-border bg-card/60 md:flex md:flex-col">
          <div className="flex items-center justify-between border-b border-border px-6 py-5">
            <div>
              <p className="text-lg font-semibold tracking-tight">Satzwerk</p>
              <p className="text-sm text-muted-foreground">Workout tracker</p>
            </div>
            <Button
              type="button"
              variant="ghost"
              size="icon"
              aria-label="Toggle theme"
              onClick={() => setIsDarkMode((value) => !value)}
            >
              {isDarkMode ? <SunMedium className="size-4" /> : <MoonStar className="size-4" />}
            </Button>
          </div>

          <nav aria-label="Desktop navigation" className="flex flex-1 flex-col gap-2 p-4">
            {navigationItems.map(({ to, label, icon: Icon }) => (
              <NavItem key={to} to={to} label={label} icon={<Icon className="size-4" />} />
            ))}
          </nav>
        </aside>

        <div className="flex min-w-0 flex-1 flex-col">
          <header className="flex items-center justify-between border-b border-border px-4 py-4 md:px-8">
            <div>
              <p className="text-base font-semibold tracking-tight md:hidden">Satzwerk</p>
              <p className="text-sm text-muted-foreground">{pageTitle}</p>
            </div>
            <Button
              type="button"
              variant="outline"
              size="sm"
              className="md:hidden"
              onClick={() => setIsDarkMode((value) => !value)}
            >
              {isDarkMode ? 'Light' : 'Dark'} mode
            </Button>
          </header>

          <main className="flex-1 px-4 py-6 pb-24 md:px-8 md:py-8 md:pb-8">{children}</main>
        </div>
      </div>

      <nav
        aria-label="Main navigation"
        className="fixed inset-x-0 bottom-0 z-20 border-t border-border bg-background/95 px-2 py-2 backdrop-blur md:hidden dark:bg-background/95"
      >
        <div className="mx-auto grid max-w-md grid-cols-5 gap-1">
          {navigationItems.map(({ to, label, icon: Icon }) => (
            <NavItem key={to} to={to} label={label} icon={<Icon className="size-5" />} compact />
          ))}
        </div>
      </nav>
    </div>
  )
}

interface NavItemProps {
  compact?: boolean
  icon: ReactNode
  label: string
  to: string
}

function NavItem({ compact = false, icon, label, to }: NavItemProps) {
  return (
    <NavLink
      to={to}
      end={to === '/'}
      className={({ isActive }) =>
        cn(
          'flex items-center rounded-xl text-sm font-medium transition-colors',
          compact ? 'flex-col gap-1 px-2 py-2 text-xs' : 'gap-3 px-4 py-3',
          isActive
            ? 'bg-primary text-primary-foreground shadow-sm'
            : 'text-muted-foreground hover:bg-accent hover:text-accent-foreground'
        )
      }
    >
      <span aria-hidden="true">{icon}</span>
      <span>{label}</span>
    </NavLink>
  )
}
