# [FE] Frontend Bootstrap

**Type:** AFK
**Blocked by:** None — can start immediately

## What to build

Scaffold the React frontend from scratch. The result is a running Vite app with all tooling, routing, state, and layout scaffolding in place — a clean base for every subsequent frontend slice.

- Vite + React + TypeScript
- TailwindCSS + shadcn/ui configured
- React Router v6 with a placeholder route structure (auth routes, dashboard, plan builder, session, history)
- React Query (`QueryClient` provider)
- Zustand store stub (auth slice placeholder)
- AppShell component: header + `MobileBottomNav` for mobile, sidebar for desktop
- Dark mode first (Tailwind `dark:` classes, class-based toggle)
- ESLint + Prettier configured
- Vitest + React Testing Library wired up
- `vite-plugin-pwa` installed (manifest only, no service worker logic yet — that is #09)

Folder structure:

```
src/
 ├── app/
 ├── pages/
 ├── components/
 ├── features/
 │    ├── auth/
 │    ├── workouts/
 │    ├── sessions/
 │    ├── analytics/
 │    └── settings/
 ├── services/
 ├── hooks/
 ├── store/
 ├── lib/
 ├── types/
 └── styles/
```

## Acceptance criteria

- [ ] `npm run dev` starts the app with no console errors
- [ ] AppShell renders with bottom nav on mobile viewport and sidebar on desktop
- [ ] React Router navigates between placeholder pages without errors
- [ ] React Query `QueryClient` and Zustand store are accessible via providers
- [ ] Dark mode toggle works
- [ ] `npm run test` passes with at least one smoke test
- [ ] ESLint and Prettier pass with zero violations
- [ ] No auth logic, no API calls — scaffolding only
