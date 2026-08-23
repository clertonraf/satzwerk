import { useMutation, useQueryClient, type InvalidateQueryFilters } from '@tanstack/react-query'
import { queryKeys } from '@/services/queryKeys'

export type PlanMutationScope = 'metadata' | 'structure'

interface UsePlanDetailMutationOptions {
  onSuccess?: () => void | Promise<void>
  scope?: PlanMutationScope
}

export function getPlanMutationInvalidationFilters(
  planId: string,
  scope: PlanMutationScope
): readonly InvalidateQueryFilters[] {
  if (scope === 'metadata') {
    return [
      { queryKey: queryKeys.plans.all(), exact: true },
      { queryKey: queryKeys.plans.metadata(planId) },
    ] as const
  }

  return [{ queryKey: queryKeys.plans.structure(planId) }] as const
}

export function usePlanDetailMutation<TVariables, TData = unknown>(
  planId: string,
  mutationFn: (variables: TVariables) => Promise<TData>,
  options: UsePlanDetailMutationOptions = {}
) {
  const queryClient = useQueryClient()
  const { onSuccess, scope = 'structure' } = options

  return useMutation({
    mutationFn,
    onSuccess: async () => {
      await Promise.all(
        getPlanMutationInvalidationFilters(planId, scope).map((filters) =>
          queryClient.invalidateQueries(filters)
        )
      )
      await onSuccess?.()
    },
  })
}
