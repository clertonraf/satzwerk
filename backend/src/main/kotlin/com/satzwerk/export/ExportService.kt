package com.satzwerk.export

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.satzwerk.common.BadRequestException
import com.satzwerk.common.ConflictException
import com.satzwerk.common.NotFoundException
import com.satzwerk.medications.MedicationLogRepository
import com.satzwerk.medications.MedicationRepository
import com.satzwerk.sessions.SetLog
import com.satzwerk.sessions.WorkoutSession
import com.satzwerk.users.UserRepository
import com.satzwerk.workouts.Exercise
import com.satzwerk.workouts.ExerciseRepository
import com.satzwerk.workouts.WorkoutExercise
import com.satzwerk.workouts.WorkoutGroup
import com.satzwerk.workouts.WorkoutPlan
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

private data class ExerciseImportResult(
    val exerciseIdMap: Map<UUID, UUID>,
    val importedCount: Int,
    val reusedCount: Int,
)

@Service
class ExportService(
    private val userRepository: UserRepository,
    private val exerciseRepository: ExerciseRepository,
    private val workoutDataPort: WorkoutDataPort,
    private val medicationRepository: MedicationRepository,
    private val medicationLogRepository: MedicationLogRepository,
    private val objectMapper: ObjectMapper,
) {
    private val translatorRegistry = ExportTranslatorRegistry(objectMapper)

    suspend fun exportForUser(userId: UUID): Any {
        val user = userRepository.findById(userId) ?: throw NotFoundException("User not found")
        return translatorRegistry.current().export(
            ExportSnapshot(
                profile = ExportProfileDto(email = user.email, displayName = user.displayName),
                exercises = exerciseRepository.findAllByUserId(userId).map(::toExerciseDto),
                workoutPlans = exportPlans(userId),
                workoutSessions = exportSessions(userId),
                medications = exportMedicationsFor(userId, medicationRepository, objectMapper),
                medicationLogs = exportMedicationLogsFor(userId, medicationRepository, medicationLogRepository),
            ),
        )
    }

    @Transactional
    suspend fun importForUser(
        userId: UUID,
        root: JsonNode,
    ): ImportSummaryDto {
        val export = translatorRegistry.forImport(root).importSnapshot(root)
        checkImportPreconditions(userId)
        val exerciseResult = importExercises(userId, export.exercises)
        val (groupIdMap, importedPlans) = importPlans(userId, export.workoutPlans, exerciseResult.exerciseIdMap)
        val (importedSessions, importedSetLogs) =
            importSessions(userId, export.workoutSessions, exerciseResult.exerciseIdMap, groupIdMap)
        val medResult =
            importMedicationsAndLogs(
                userId,
                export.medications,
                export.medicationLogs,
                MedicationImportDeps(medicationRepository, medicationLogRepository, objectMapper),
            )
        return ImportSummaryDto(
            importedExercises = exerciseResult.importedCount,
            importedWorkoutPlans = importedPlans,
            importedWorkoutSessions = importedSessions,
            importedSetLogs = importedSetLogs,
            reusedExercises = exerciseResult.reusedCount,
            importedMedications = medResult.importedCount,
            importedMedicationLogs = medResult.importedLogCount,
            reusedMedications = medResult.reusedCount,
        )
    }

    private suspend fun checkImportPreconditions(userId: UUID) {
        if (workoutDataPort.findOpenSession(userId) != null) {
            throw ConflictException("You have an open workout session. Complete or discard it before importing.")
        }
    }

    private suspend fun importExercises(
        userId: UUID,
        exercises: List<ExportExerciseDto>,
    ): ExerciseImportResult {
        val exerciseIdMap = mutableMapOf<UUID, UUID>()
        var importedCount = 0
        var reusedCount = 0
        for (exportedExercise in exercises) {
            val existing = exerciseRepository.findByUserIdAndNameIgnoreCase(userId, exportedExercise.name)
            if (existing != null) {
                exerciseIdMap[exportedExercise.id] = requireNotNull(existing.id)
                reusedCount++
            } else {
                val saved = exerciseRepository.save(toNewExercise(userId, exportedExercise))
                exerciseIdMap[exportedExercise.id] = requireNotNull(saved.id)
                importedCount++
            }
        }
        return ExerciseImportResult(exerciseIdMap, importedCount, reusedCount)
    }

    private suspend fun importPlans(
        userId: UUID,
        plans: List<ExportWorkoutPlanDto>,
        exerciseIdMap: Map<UUID, UUID>,
    ): Pair<Map<UUID, UUID>, Int> {
        val groupIdMap = mutableMapOf<UUID, UUID>()
        for (exportedPlan in plans) {
            val groupSpecs =
                exportedPlan.groups.map { exportedGroup ->
                    val exercises =
                        exportedGroup.exercises.map { exportedWe ->
                            val mappedId =
                                exerciseIdMap[exportedWe.exerciseId]
                                    ?: throw BadRequestException(
                                        "Export references unknown exercise id ${exportedWe.exerciseId}",
                                    )
                            toNewWorkoutExercise(UUID(0, 0), mappedId, exportedWe)
                        }
                    ImportGroupSpec(
                        exportedId = exportedGroup.id,
                        group = toNewWorkoutGroup(UUID(0, 0), exportedGroup),
                        exercises = exercises,
                    )
                }
            groupIdMap.putAll(
                workoutDataPort.importPlanWithGroups(toNewWorkoutPlan(userId, exportedPlan), groupSpecs),
            )
        }
        return groupIdMap to plans.size
    }

    private suspend fun importSessions(
        userId: UUID,
        sessions: List<ExportWorkoutSessionDto>,
        exerciseIdMap: Map<UUID, UUID>,
        groupIdMap: Map<UUID, UUID>,
    ): Pair<Int, Int> {
        var setLogCount = 0
        for (exportedSession in sessions) {
            val mappedGroupId =
                groupIdMap[exportedSession.workoutGroupId]
                    ?: throw BadRequestException("Export references unknown group id ${exportedSession.workoutGroupId}")
            val setLogs =
                exportedSession.setLogs.map { exportedSetLog ->
                    val mappedId =
                        exerciseIdMap[exportedSetLog.exerciseId]
                            ?: throw BadRequestException("Unknown exercise id ${exportedSetLog.exerciseId}")
                    toNewSetLog(UUID(0, 0), mappedId, exportedSetLog)
                }
            setLogCount +=
                workoutDataPort.importSessionWithSetLogs(
                    toNewWorkoutSession(userId, mappedGroupId, exportedSession),
                    setLogs,
                )
        }
        return sessions.size to setLogCount
    }

    private suspend fun exportPlans(userId: UUID): List<ExportWorkoutPlanDto> =
        workoutDataPort.findAllPlans(userId).map { plan ->
            val groups = workoutDataPort.findGroupsForPlan(requireNotNull(plan.id))
            ExportWorkoutPlanDto(
                id = requireNotNull(plan.id),
                name = plan.name,
                source = plan.source,
                isActive = plan.isActive,
                activatedAt = plan.activatedAt,
                createdAt = plan.createdAt,
                groups = groups.map { group -> exportGroup(group) },
            )
        }

    private suspend fun exportGroup(group: WorkoutGroup): ExportWorkoutGroupDto {
        val workoutExercises = workoutDataPort.findExercisesForGroup(requireNotNull(group.id))
        return ExportWorkoutGroupDto(
            id = requireNotNull(group.id),
            title = group.title,
            orderIndex = group.orderIndex,
            exercises = workoutExercises.map(::toExportWorkoutExerciseDto),
        )
    }

    private suspend fun exportSessions(userId: UUID): List<ExportWorkoutSessionDto> {
        val sessions = workoutDataPort.findAllSessions(userId)
        val logsBySession = workoutDataPort.findSetLogsBySessionIds(sessions.mapNotNull { it.id })
        return sessions.map { session ->
            toExportSessionDto(session, logsBySession[session.id] ?: emptyList())
        }
    }
}

// --- Top-level factory helpers (do not count towards ExportService function limit) ---

private fun toExerciseDto(e: Exercise) =
    ExportExerciseDto(
        id = requireNotNull(e.id),
        name = e.name,
        muscleGroup = e.muscleGroup,
        description = e.description,
        videoUrl = e.videoUrl,
        equipment = e.equipment,
        createdAt = e.createdAt,
    )

private fun toExportWorkoutExerciseDto(we: WorkoutExercise) =
    ExportWorkoutExerciseDto(
        id = requireNotNull(we.id),
        exerciseId = we.exerciseId,
        sets = we.sets,
        reps = we.reps,
        toFailure = we.toFailure,
        advancedTechnique = we.advancedTechnique,
        orderIndex = we.orderIndex,
    )

private fun toExportSessionDto(
    session: WorkoutSession,
    setLogs: List<SetLog>,
) = ExportWorkoutSessionDto(
    id = requireNotNull(session.id),
    workoutGroupId = session.workoutGroupId,
    startedAt = session.startedAt,
    completedAt = session.completedAt,
    notes = session.notes,
    setLogs = setLogs.map(::toExportSetLogDto),
)

private fun toExportSetLogDto(sl: SetLog) =
    ExportSetLogDto(
        id = requireNotNull(sl.id),
        exerciseId = sl.exerciseId,
        setNumber = sl.setNumber,
        weight = sl.weight,
        reps = sl.reps,
        loggedAt = sl.loggedAt,
        isPr = sl.isPr,
    )

private fun toNewExercise(
    userId: UUID,
    dto: ExportExerciseDto,
) = Exercise(
    userId = userId,
    name = dto.name,
    muscleGroup = dto.muscleGroup,
    description = dto.description,
    videoUrl = dto.videoUrl,
    equipment = dto.equipment,
)

private fun toNewWorkoutPlan(
    userId: UUID,
    dto: ExportWorkoutPlanDto,
) = WorkoutPlan(
    userId = userId,
    name = dto.name,
    source = dto.source,
    isActive = false,
    activatedAt = null,
    createdAt = dto.createdAt,
)

private fun toNewWorkoutGroup(
    planId: UUID,
    dto: ExportWorkoutGroupDto,
) = WorkoutGroup(workoutPlanId = planId, title = dto.title, orderIndex = dto.orderIndex)

private fun toNewWorkoutExercise(
    groupId: UUID,
    exerciseId: UUID,
    dto: ExportWorkoutExerciseDto,
) = WorkoutExercise(
    workoutGroupId = groupId,
    exerciseId = exerciseId,
    sets = dto.sets,
    reps = dto.reps,
    toFailure = dto.toFailure,
    advancedTechnique = dto.advancedTechnique,
    orderIndex = dto.orderIndex,
)

private fun toNewWorkoutSession(
    userId: UUID,
    groupId: UUID,
    dto: ExportWorkoutSessionDto,
) = WorkoutSession(
    userId = userId,
    workoutGroupId = groupId,
    startedAt = dto.startedAt,
    completedAt = dto.completedAt,
    notes = dto.notes,
)

private fun toNewSetLog(
    sessionId: UUID,
    exerciseId: UUID,
    dto: ExportSetLogDto,
) = SetLog(
    workoutSessionId = sessionId,
    exerciseId = exerciseId,
    setNumber = dto.setNumber,
    weight = dto.weight,
    reps = dto.reps,
    loggedAt = dto.loggedAt,
    isPr = dto.isPr,
)
