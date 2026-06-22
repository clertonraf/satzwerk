import { useMemo } from 'react'
import axios from 'axios'
import { useQuery } from '@tanstack/react-query'
import { buildGroupStatsMap, buildWorkoutGroupCatalog } from '@/lib/domainBuilders'
import { exerciseService } from '@/services/exerciseService'
import { queryKeys } from '@/services/queryKeys'
import { sessionService, type ExerciseReferenceWeights, type WorkoutSession } from '@/services/sessionService'

interface UseSessionQueriesInput {
  session: WorkoutSession | null
  isSessionLoading: boolean
}

export function useSessionQueries({ session, isSessionLoading }: UseSessionQueriesInput) {
  const exercisesQuery = useQuery({
    queryKey: queryKeys.exercises.all(),
    queryFn: () => exerciseService.list(),
  })

  const startOptionsQuery = useQuery({
    queryKey: queryKeys.sessions.startOptions(),
    queryFn: async () => {
      try {
        return await sessionService.getStartOptions()
      } catch (error) {
        if (axios.isAxiosError(error) && error.response?.status === 404) {
          return null
        }
        throw error
      }
    },
    enabled: !session && !isSessionLoading,
  })

  const openPlanDetailQuery = useQuery({
    queryKey: queryKeys.sessions.openPlanDetail(session?.id ?? ''),
    queryFn: () => sessionService.getOpenPlanDetail(),
    enabled: !!session && !isSessionLoading,
  })

  const historyQuery = useQuery({
    queryKey: queryKeys.sessions.history(),
    queryFn: () => sessionService.history(),
    enabled: !session && !isSessionLoading,
    retry: false,
  })

  const referenceWeightsQuery = useQuery({
    queryKey: queryKeys.sessions.referenceWeights(session?.id ?? ''),
    queryFn: () => sessionService.getReferenceWeights(session!.id),
    enabled: !!session,
  })

  const planDetails = useMemo(() => {
    if (session) {
      return openPlanDetailQuery.data ? [openPlanDetailQuery.data] : []
    }
    return startOptionsQuery.data ? [startOptionsQuery.data] : []
  }, [session, openPlanDetailQuery.data, startOptionsQuery.data])

  const groupCatalog = useMemo(() => buildWorkoutGroupCatalog(planDetails), [planDetails])

  const groupOptions = useMemo(
    () => Object.values(groupCatalog).sort((a, b) => a.group.orderIndex - b.group.orderIndex),
    [groupCatalog],
  )

  const groupStatsMap = useMemo(() => buildGroupStatsMap(historyQuery.data ?? []), [historyQuery.data])

  const exercisesById = useMemo(
    () => new Map((exercisesQuery.data ?? []).map((exercise) => [exercise.id, exercise])),
    [exercisesQuery.data],
  )

  const referenceWeightsMap = useMemo<Map<string, ExerciseReferenceWeights>>(
    () => new Map((referenceWeightsQuery.data ?? []).map((rw) => [rw.exerciseId, rw])),
    [referenceWeightsQuery.data],
  )

  const isCatalogLoading =
    (session ? openPlanDetailQuery.isLoading : startOptionsQuery.isLoading) || exercisesQuery.isLoading

  const queryError = session
    ? (openPlanDetailQuery.error ?? exercisesQuery.error)
    : (startOptionsQuery.error ?? exercisesQuery.error)

  const isHistoryLoading = historyQuery.isLoading
  const isHistoryAvailable = historyQuery.data !== undefined
  const startOptionsData = startOptionsQuery.data
  const isReferenceWeightsLoading = referenceWeightsQuery.isLoading

  return {
    groupCatalog,
    groupOptions,
    groupStatsMap,
    exercisesById,
    referenceWeightsMap,
    isCatalogLoading,
    queryError,
    isHistoryLoading,
    isHistoryAvailable,
    startOptionsData,
    isReferenceWeightsLoading,
  }
}
