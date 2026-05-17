# Offline support is scoped to active session survival only

The original plan described a full offline-first PWA with IndexedDB, background sync, cached workout plans, and queued submissions. This was narrowed to a single use case: a WorkoutSession that was started online can be completed offline and synced when connectivity returns. All other screens (history, plan management, dashboard) require a live connection.

## Considered Options

- **Full offline-first** — IndexedDB as local database, background sync for all writes, service worker caching for all routes. Rejected for MVP: conflict resolution, sync state UI, and stale-data handling are substantial engineering work with low immediate return.
- **Session survival only** — chosen. Covers the only scenario that genuinely matters at the gym (losing signal mid-workout). Everything else degrades gracefully to an offline error state.
