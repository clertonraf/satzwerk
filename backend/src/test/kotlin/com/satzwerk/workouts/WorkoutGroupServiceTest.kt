package com.satzwerk.workouts

import com.satzwerk.analytics.AnalyticsReadCache
import com.satzwerk.common.TransactionRunner
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.util.UUID

class WorkoutGroupServiceTest {
    private val userId = UUID.randomUUID()
    private val planId = UUID.randomUUID()
    private val groupId = UUID.randomUUID()
    private val inlineTransactionRunner =
        object : TransactionRunner {
            override suspend fun <T> required(block: suspend () -> T): T = block()

            override suspend fun afterCommit(block: suspend () -> Unit) {
                block()
            }
        }

    @Test
    fun `create invalidates group cache after commit`(): Unit =
        runBlocking {
            val workoutPlanService = planService()
            val workoutGroupReadCache: WorkoutGroupReadCache = mock()
            val service =
                WorkoutGroupService(
                    workoutPlanService = workoutPlanService,
                    workoutGroupRepository =
                        mock {
                            onBlocking { save(any()) } doReturn
                                WorkoutGroup(id = groupId, workoutPlanId = planId, title = "Treino A", orderIndex = 0)
                        },
                    workoutGroupReadCache = workoutGroupReadCache,
                    analyticsReadCache = mock(),
                    transactionRunner = inlineTransactionRunner,
                )

            service.create(userId, planId, CreateGroupRequest(title = "Treino A", orderIndex = 0))

            verify(workoutGroupReadCache).invalidatePlan(userId, planId)
        }

    @Test
    fun `update invalidates group cache after commit`(): Unit =
        runBlocking {
            val workoutGroupReadCache: WorkoutGroupReadCache = mock()
            val service =
                WorkoutGroupService(
                    workoutPlanService = planService(),
                    workoutGroupRepository =
                        mock {
                            onBlocking { save(any()) } doAnswer { it.arguments[0] as WorkoutGroup }
                        },
                    workoutGroupReadCache = workoutGroupReadCache,
                    analyticsReadCache = mock(),
                    transactionRunner = inlineTransactionRunner,
                )

            service.update(userId, planId, groupId, UpdateGroupRequest(title = "Treino B"))

            verify(workoutGroupReadCache).invalidatePlan(userId, planId)
        }

    @Test
    fun `delete invalidates analytics and group caches after commit`(): Unit =
        runBlocking {
            val workoutGroupReadCache: WorkoutGroupReadCache = mock()
            val analyticsReadCache: AnalyticsReadCache = mock()
            val service =
                WorkoutGroupService(
                    workoutPlanService = planService(),
                    workoutGroupRepository = mock(),
                    workoutGroupReadCache = workoutGroupReadCache,
                    analyticsReadCache = analyticsReadCache,
                    transactionRunner = inlineTransactionRunner,
                )

            service.delete(userId, planId, groupId)
            verify(workoutGroupReadCache).invalidatePlan(userId, planId)
            verify(workoutGroupReadCache).invalidatePlan(userId, planId)
            verify(analyticsReadCache).invalidateUser(userId)
        }

    private fun planService(): WorkoutPlanService =
        mock {
            onBlocking { getRequiredPlan(userId, planId) } doReturn
                WorkoutPlan(id = planId, userId = userId, name = "PPL")
            onBlocking { getRequiredGroup(userId, planId, groupId) } doReturn
                WorkoutGroup(id = groupId, workoutPlanId = planId, title = "Treino A", orderIndex = 0)
        }
}
