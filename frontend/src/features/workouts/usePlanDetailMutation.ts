import { useMutation, useQueryClient } from '@tanstack/react-query'
import { queryKeys } from '@/services/queryKeys'

export function usePlanDetailMutation<TVariables, TData = unknown>(
  planId: string,
  mutationFn: (variables: TVariables) => Promise<TData>,
  onSuccess?: () => void | Promise<void>
) {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn,
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: queryKeys.plans.all() }),
        queryClient.invalidateQueries({ queryKey: queryKeys.plans.detail(planId) }),
      ])
      await onSuccess?.()
    },
  })
}
