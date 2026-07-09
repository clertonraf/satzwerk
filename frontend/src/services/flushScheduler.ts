import type { FlushResult } from './offlineQueue'

/**
 * Decides when to call flush() based on connectivity transitions.
 *
 * Calls flush() exactly once each time the device transitions from offline → online.
 * Stays dormant while online (no prior offline period) or while still offline.
 * Safe to call multiple times with the same value — idempotent per connectivity state.
 */
export interface FlushScheduler {
  onConnectivityChange(isOnline: boolean): Promise<FlushResult | null>
}

/**
 * Creates a stateful scheduler that triggers [flush] on each offline → online transition.
 *
 * @param flush - the function to call when a reconnect is detected; must be idempotent.
 */
export function createFlushScheduler(flush: () => Promise<FlushResult>): FlushScheduler {
  let wasOffline = false
  return {
    async onConnectivityChange(isOnline: boolean): Promise<FlushResult | null> {
      if (!isOnline) {
        wasOffline = true
        return null
      }
      if (!wasOffline) {
        return null
      }
      wasOffline = false
      return flush()
    },
  }
}
