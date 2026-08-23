package com.satzwerk.workouts

import com.fasterxml.jackson.databind.node.IntNode
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.codec.multipart.FilePart
import java.util.UUID

class PlanImportServiceTest {
    private val planParser: PlanParser = mock()
    private val workoutPlanRepository: WorkoutPlanRepository = mock()
    private val workoutGroupRepository: WorkoutGroupRepository = mock()
    private val workoutExerciseRepository: WorkoutExerciseRepository = mock()
    private val exerciseResolver: ExerciseResolver = mock()
    private val planImportParsingAdapters: PlanImportParsingAdapters = mock()
    private val service =
        PlanImportService(
            planParser = planParser,
            workoutPlanRepository = workoutPlanRepository,
            workoutGroupRepository = workoutGroupRepository,
            workoutExerciseRepository = workoutExerciseRepository,
            exerciseResolver = exerciseResolver,
            planImportParsingAdapters = planImportParsingAdapters,
        )

    @Test
    fun `import delegates filename normalization and technique parsing to dedicated adapters`(): Unit =
        runBlocking {
            val userId = UUID.randomUUID()
            val planId = UUID.randomUUID()
            val groupId = UUID.randomUUID()
            val exerciseId = UUID.randomUUID()
            val filePart = mockFilePart()
            val parsedResponse = parsedResponse()

            whenever(planParser.parse(filePart)).thenReturn(parsedResponse)
            whenever(planImportParsingAdapters.normalizeFilename("Push_Pull-Legs.xlsx")).thenReturn("Push Pull Legs")
            whenever(planImportParsingAdapters.parseTechnique("Rest Pause Cluster"))
                .thenReturn(AdvancedTechnique.REST_PAUSE.name)
            whenever(exerciseResolver.resolve(userId, mapOf("Bench Press" to "CHEST"))).thenReturn(
                mapOf(
                    "bench press" to
                        Exercise(
                            id = exerciseId,
                            userId = userId,
                            name = "Bench Press",
                            muscleGroup = "CHEST",
                        ),
                ),
            )
            stubPersistence(userId, planId, groupId)
            whenever(workoutExerciseRepository.saveAll(any<Iterable<WorkoutExercise>>())).thenReturn(flowOf())

            val response = service.import(userId, filePart)

            assertEquals("Push Pull Legs", response.name)
            verify(planImportParsingAdapters).normalizeFilename("Push_Pull-Legs.xlsx")
            verify(planImportParsingAdapters).parseTechnique("Rest Pause Cluster")

            val captor = argumentCaptor<Iterable<WorkoutExercise>>()
            verify(workoutExerciseRepository).saveAll(captor.capture())
            val savedExercises = captor.firstValue.toList()
            assertEquals(1, savedExercises.size)
            assertEquals(AdvancedTechnique.REST_PAUSE.name, savedExercises[0].advancedTechnique)
        }

    private fun mockFilePart(): FilePart =
        mock<FilePart>().also {
            whenever(it.filename()).thenReturn("Push_Pull-Legs.xlsx")
        }

    private fun parsedResponse(): SatzwerkParserResponse =
        SatzwerkParserResponse(
            workouts =
                listOf(
                    ParsedWorkout(
                        name = "Treino A",
                        bodyParts = listOf("CHEST"),
                        exercises =
                            listOf(
                                ParsedExercise(
                                    exercise = "Bench Press",
                                    advancedTechnique = "Rest Pause Cluster",
                                    sets = 4,
                                    reps = IntNode.valueOf(8),
                                ),
                            ),
                    ),
                ),
        )

    private suspend fun stubPersistence(
        userId: UUID,
        planId: UUID,
        groupId: UUID,
    ) {
        whenever(workoutPlanRepository.save(any())).thenReturn(
            WorkoutPlan(
                id = planId,
                userId = userId,
                name = "Push Pull Legs",
                source = WorkoutSource.IMPORTED.name,
                isActive = false,
            ),
        )
        whenever(workoutGroupRepository.saveAll(any<Iterable<WorkoutGroup>>())).thenReturn(
            flowOf(
                WorkoutGroup(
                    id = groupId,
                    workoutPlanId = planId,
                    title = "CHEST",
                    orderIndex = 0,
                ),
            ),
        )
    }
}
