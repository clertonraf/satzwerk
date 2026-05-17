package com.satzwerk.workouts

import com.fasterxml.jackson.databind.node.IntNode
import com.fasterxml.jackson.databind.node.TextNode
import com.satzwerk.auth.AuthResponse
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.reactive.function.BodyInserters
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class PlanImportIntegrationTest {
    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")

        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.r2dbc.url") {
                "r2dbc:postgresql://${postgres.host}:${postgres.getMappedPort(5432)}/${postgres.databaseName}"
            }
            registry.add("spring.r2dbc.username", postgres::getUsername)
            registry.add("spring.r2dbc.password", postgres::getPassword)
            registry.add("spring.flyway.url", postgres::getJdbcUrl)
            registry.add("spring.flyway.user", postgres::getUsername)
            registry.add("spring.flyway.password", postgres::getPassword)
        }
    }

    @Autowired
    lateinit var client: WebTestClient

    @MockitoBean
    lateinit var kraftLogParserClient: KraftLogParserClient

    private lateinit var authToken: String

    @BeforeEach
    fun setup() {
        val suffix = UUID.randomUUID()
        authToken = registerAndLogin("import-$suffix@test.com", "password123", "Import User")
    }

    @Test
    fun `import creates plan with IMPORTED source and inactive status`() {
        mockParserResponse(parserResponse(workoutCount = 1, exercisesPerWorkout = 1))

        mockMultipartImportRequest(authToken)
            .expectStatus().isCreated
            .expectBody()
            .jsonPath("$.id").isNotEmpty
            .jsonPath("$.name").isEqualTo("Push Pull Legs")
            .jsonPath("$.source").isEqualTo("IMPORTED")
            .jsonPath("$.isActive").isEqualTo(false)
    }

    @Test
    fun `import creates exercises and groups from parsed response`() {
        mockParserResponse(parserResponse(workoutCount = 2, exercisesPerWorkout = 3))

        val planId =
            mockMultipartImportRequest(authToken)
                .expectStatus().isCreated
                .expectBody(WorkoutPlanResponse::class.java)
                .returnResult()
                .responseBody!!
                .id

        client
            .get()
            .uri("/api/plans/$planId")
            .header("Authorization", "Bearer $authToken")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.groups.length()").isEqualTo(2)
            .jsonPath("$.groups[0].exercises.length()").isEqualTo(3)
            .jsonPath("$.groups[1].exercises.length()").isEqualTo(3)
    }

    @Test
    fun `import deduplicates exercises by name`() {
        createExercise(authToken, "Bench Press", "CHEST")
        mockParserResponse(
            KraftLogParserResponse(
                workouts =
                    listOf(
                        ParsedWorkout(
                            name = "Treino A",
                            bodyParts = listOf("CHEST"),
                            exercises =
                                listOf(
                                    ParsedExercise(
                                        exercise = "bench press",
                                        sets = 4,
                                        reps = IntNode.valueOf(8),
                                    ),
                                ),
                        ),
                    ),
            ),
        )

        val planId =
            mockMultipartImportRequest(authToken)
                .expectStatus().isCreated
                .expectBody(WorkoutPlanResponse::class.java)
                .returnResult()
                .responseBody!!
                .id

        client
            .get()
            .uri("/api/exercises")
            .header("Authorization", "Bearer $authToken")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.length()").isEqualTo(1)

        client
            .get()
            .uri("/api/plans/$planId")
            .header("Authorization", "Bearer $authToken")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.groups[0].exercises[0].exerciseId").exists()
    }

    @Test
    fun `import maps reps F to toFailure true`() {
        mockParserResponse(
            KraftLogParserResponse(
                workouts =
                    listOf(
                        ParsedWorkout(
                            name = "Treino A",
                            bodyParts = listOf("CORE"),
                            exercises =
                                listOf(
                                    ParsedExercise(
                                        exercise = "Plank",
                                        sets = 4,
                                        reps = TextNode.valueOf("F"),
                                    ),
                                ),
                        ),
                    ),
            ),
        )

        val planId =
            mockMultipartImportRequest(authToken)
                .expectStatus().isCreated
                .expectBody(WorkoutPlanResponse::class.java)
                .returnResult()
                .responseBody!!
                .id

        client
            .get()
            .uri("/api/plans/$planId")
            .header("Authorization", "Bearer $authToken")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.groups[0].exercises[0].reps").isEqualTo(0)
            .jsonPath("$.groups[0].exercises[0].toFailure").isEqualTo(true)
    }

    @Test
    fun `import maps unknown technique to null`() {
        mockParserResponse(
            KraftLogParserResponse(
                workouts =
                    listOf(
                        ParsedWorkout(
                            name = "Treino A",
                            bodyParts = listOf("CHEST"),
                            exercises =
                                listOf(
                                    ParsedExercise(
                                        exercise = "Bench Press",
                                        advancedTechnique = "Unknown Technique",
                                        sets = 4,
                                        reps = IntNode.valueOf(8),
                                    ),
                                ),
                        ),
                    ),
            ),
        )

        val planId =
            mockMultipartImportRequest(authToken)
                .expectStatus().isCreated
                .expectBody(WorkoutPlanResponse::class.java)
                .returnResult()
                .responseBody!!
                .id

        client
            .get()
            .uri("/api/plans/$planId")
            .header("Authorization", "Bearer $authToken")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.groups[0].exercises[0].advancedTechnique").doesNotExist()
    }

    @Test
    fun `import returns 401 for unauthenticated request`() {
        mockParserResponse(parserResponse(workoutCount = 1, exercisesPerWorkout = 1))

        mockMultipartImportRequest()
            .expectStatus().isUnauthorized
    }

    private fun mockMultipartImportRequest(token: String? = null): WebTestClient.ResponseSpec {
        val bodyBuilder = MultipartBodyBuilder()
        bodyBuilder.part("file", "dummy xlsx".toByteArray())
            .filename("Push_Pull-Legs.xlsx")
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))

        return client
            .post()
            .uri("/api/plans/import")
            .apply {
                token?.let { header("Authorization", "Bearer $it") }
            }.contentType(MediaType.MULTIPART_FORM_DATA)
            .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
            .exchange()
    }

    private fun mockParserResponse(response: KraftLogParserResponse) {
        runBlocking {
            whenever(kraftLogParserClient.parse(any())).thenReturn(response)
        }
    }

    private fun parserResponse(
        workoutCount: Int,
        exercisesPerWorkout: Int,
    ): KraftLogParserResponse =
        KraftLogParserResponse(
            workouts =
                (1..workoutCount).map { workoutIndex ->
                    ParsedWorkout(
                        name = "Treino $workoutIndex",
                        bodyParts = listOf("CHEST", "TRICEPS"),
                        exercises =
                            (1..exercisesPerWorkout).map { exerciseIndex ->
                                ParsedExercise(
                                    exercise = "Exercise $workoutIndex.$exerciseIndex",
                                    sets = 4,
                                    reps = IntNode.valueOf(8),
                                )
                            },
                    )
                },
        )

    private fun createExercise(
        token: String,
        name: String,
        muscleGroup: String,
    ): UUID {
        val response =
            client
                .post()
                .uri("/api/exercises")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(
                    mapOf(
                        "name" to name,
                        "muscleGroup" to muscleGroup,
                    ),
                ).exchange()
                .expectStatus().isCreated
                .expectBody(ExerciseResponse::class.java)
                .returnResult()
                .responseBody!!

        return response.id
    }

    private fun registerAndLogin(
        email: String,
        password: String,
        displayName: String,
    ): String {
        val response =
            client
                .post()
                .uri("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(
                    mapOf(
                        "email" to email,
                        "password" to password,
                        "displayName" to displayName,
                    ),
                ).exchange()
                .expectStatus().isCreated
                .expectBody(AuthResponse::class.java)
                .returnResult()
                .responseBody!!

        return response.accessToken
    }
}
