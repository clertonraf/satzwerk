import { QueryClient } from '@tanstack/react-query'
import { describe, expect, it } from 'vitest'
import * as planDetailMutationModule from '../usePlanDetailMutation'
import { queryKeys } from '@/services/queryKeys'

type PlanKeyFactory = (id: string) => readonly string[]

function getPlanKeyFactory(name: string): PlanKeyFactory | undefined {
  return (queryKeys.plans as Record<string, PlanKeyFactory | undefined>)[name]
}

function getInvalidationTargets() {
  return (planDetailMutationModule as Record<string, unknown>).getPlanMutationInvalidationFilters as
    | ((planId: string, scope: 'metadata' | 'structure') => readonly { queryKey: readonly string[]; exact?: boolean }[])
    | undefined
}

describe('usePlanDetailMutation invalidation scopes', () => {
  it('defines separate cache identities for workout plan metadata and structure', () => {
    const metadataKey = getPlanKeyFactory('metadata')?.('plan-1')
    const structureKey = getPlanKeyFactory('structure')?.('plan-1')

    expect(metadataKey).toEqual(['plans', 'metadata', 'plan-1'])
    expect(structureKey).toEqual(['plans', 'structure', 'plan-1'])
    expect(metadataKey).not.toEqual(structureKey)
  })

  it('invalidates only the workout plan structure seam for structural mutations', async () => {
    const queryClient = new QueryClient()
    queryClient.setQueryData(queryKeys.plans.all(), [])
    queryClient.setQueryData(queryKeys.plans.metadata('plan-1'), { id: 'plan-1' })
    queryClient.setQueryData(queryKeys.plans.structure('plan-1'), { groups: [] })

    await Promise.all(
      (getInvalidationTargets()?.('plan-1', 'structure') ?? []).map((filters) =>
        queryClient.invalidateQueries(filters)
      )
    )

    expect(queryClient.getQueryState(queryKeys.plans.all())?.isInvalidated).toBe(false)
    expect(queryClient.getQueryState(queryKeys.plans.metadata('plan-1'))?.isInvalidated).toBe(false)
    expect(queryClient.getQueryState(queryKeys.plans.structure('plan-1'))?.isInvalidated).toBe(true)
  })

  it('invalidates workout plan metadata without touching the structure seam', async () => {
    const queryClient = new QueryClient()
    queryClient.setQueryData(queryKeys.plans.all(), [])
    queryClient.setQueryData(queryKeys.plans.metadata('plan-1'), { id: 'plan-1' })
    queryClient.setQueryData(queryKeys.plans.structure('plan-1'), { groups: [] })

    await Promise.all(
      (getInvalidationTargets()?.('plan-1', 'metadata') ?? []).map((filters) =>
        queryClient.invalidateQueries(filters)
      )
    )

    expect(queryClient.getQueryState(queryKeys.plans.all())?.isInvalidated).toBe(true)
    expect(queryClient.getQueryState(queryKeys.plans.metadata('plan-1'))?.isInvalidated).toBe(true)
    expect(queryClient.getQueryState(queryKeys.plans.structure('plan-1'))?.isInvalidated).toBe(false)
  })
})
