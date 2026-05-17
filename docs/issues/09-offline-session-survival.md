# [FE] Offline Session Survival

**Type:** AFK
**Blocked by:** #07 Workout Session Tracking

## What to build

PWA offline support scoped to **session survival**: if the user loses network mid-workout, the app must not lose their in-progress `WorkoutSession` or `SetLog` entries. Full offline-first (i.e. starting a new session without ever being online) is **out of scope** (see ADR `0003-offline-scoped-to-session-survival.md`).

- Workbox service worker (via `vite-plugin-pwa`) with a `StaleWhileRevalidate` strategy for static assets
- Cache the active `WorkoutPlan` (the plan + its groups + exercises) in IndexedDB (Dexie) when a session is started — so the exercise list remains available offline
- Write `SetLog` entries to IndexedDB first; sync to the backend in the background when connectivity returns
- Background sync via the Background Sync API (with a Workbox `BackgroundSyncPlugin` queue as fallback)
- Zustand active session slice (from #07) acts as the in-memory layer; IndexedDB is the durable layer beneath it
- On reconnect: flush the queue, then invalidate React Query cache to reflect server state

## Acceptance criteria

- [ ] App is installable as a PWA (passes Lighthouse PWA checklist)
- [ ] If the network drops mid-session, the user can continue logging sets
- [ ] Set logs written offline are synced to the backend when connectivity is restored
- [ ] The active WorkoutPlan's exercise list is available offline once a session has been started online
- [ ] No set logs are lost after an offline period followed by a browser refresh
- [ ] Starting a brand-new session while offline is blocked with a clear message (out of scope per ADR)
- [ ] Synced set logs match what was logged offline exactly (no duplicates, no drops)
