package com.satzwerk.measurements

import com.satzwerk.PostgresTestContainer
import com.satzwerk.auth.AuthResponse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import java.time.LocalDate
import java.util.UUID

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class MeasurementIntegrationTest : PostgresTestContainer() {
    @Autowired
    lateinit var client: WebTestClient

    private lateinit var authToken: String
    private val today: LocalDate = LocalDate.of(2026, 1, 15)

    @BeforeEach
    fun setup() {
        val suffix = UUID.randomUUID()
        authToken = registerAndLogin("measurement-$suffix@test.com", "password123", "Measurement User")
    }

    @Test
    fun `POST measurements creates new entry and returns it`() {
        client
            .post()
            .uri("/api/measurements")
            .header("Authorization", "Bearer $authToken")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "measurementDate" to today.toString(),
                    "shoulders" to 120.50,
                    "weightKg" to 82.30,
                ),
            ).exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.id").isNotEmpty
            .jsonPath("$.measurementDate").isEqualTo(today.toString())
            .jsonPath("$.shoulders").isEqualTo(120.50)
            .jsonPath("$.weightKg").isEqualTo(82.30)
            .jsonPath("$.chest").isEmpty
    }

    @Test
    fun `POST measurements for same date performs partial merge preserving existing fields`() {
        client
            .post()
            .uri("/api/measurements")
            .header("Authorization", "Bearer $authToken")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "measurementDate" to today.toString(),
                    "shoulders" to 120.50,
                    "chest" to 100.00,
                ),
            ).exchange()
            .expectStatus().isOk

        client
            .post()
            .uri("/api/measurements")
            .header("Authorization", "Bearer $authToken")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "measurementDate" to today.toString(),
                    "weightKg" to 82.30,
                ),
            ).exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.shoulders").isEqualTo(120.50)
            .jsonPath("$.chest").isEqualTo(100.00)
            .jsonPath("$.weightKg").isEqualTo(82.30)
    }

    @Test
    fun `GET measurements returns all entries sorted by date DESC`() {
        val date1 = LocalDate.of(2026, 1, 10)
        val date2 = LocalDate.of(2026, 1, 15)
        val date3 = LocalDate.of(2026, 1, 5)

        listOf(date1, date2, date3).forEach { date ->
            client
                .post()
                .uri("/api/measurements")
                .header("Authorization", "Bearer $authToken")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(mapOf("measurementDate" to date.toString(), "weightKg" to 80.00))
                .exchange()
                .expectStatus().isOk
        }

        client
            .get()
            .uri("/api/measurements")
            .header("Authorization", "Bearer $authToken")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.length()").isEqualTo(3)
            .jsonPath("$[0].measurementDate").isEqualTo(date2.toString())
            .jsonPath("$[1].measurementDate").isEqualTo(date1.toString())
            .jsonPath("$[2].measurementDate").isEqualTo(date3.toString())
    }

    @Test
    fun `GET measurements returns only entries for the authenticated user`() {
        val suffix = UUID.randomUUID()
        val otherToken = registerAndLogin("other-$suffix@test.com", "password123", "Other User")

        client
            .post()
            .uri("/api/measurements")
            .header("Authorization", "Bearer $authToken")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("measurementDate" to today.toString(), "weightKg" to 80.00))
            .exchange()
            .expectStatus().isOk

        client
            .get()
            .uri("/api/measurements")
            .header("Authorization", "Bearer $otherToken")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.length()").isEqualTo(0)
    }

    @Test
    fun `DELETE measurements by date removes the entry and returns 204`() {
        client
            .post()
            .uri("/api/measurements")
            .header("Authorization", "Bearer $authToken")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("measurementDate" to today.toString(), "weightKg" to 80.00))
            .exchange()
            .expectStatus().isOk

        client
            .delete()
            .uri("/api/measurements/$today")
            .header("Authorization", "Bearer $authToken")
            .exchange()
            .expectStatus().isNoContent

        client
            .get()
            .uri("/api/measurements")
            .header("Authorization", "Bearer $authToken")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.length()").isEqualTo(0)
    }

    @Test
    fun `DELETE measurements by date returns 404 when entry does not exist`() {
        client
            .delete()
            .uri("/api/measurements/$today")
            .header("Authorization", "Bearer $authToken")
            .exchange()
            .expectStatus().isNotFound
    }

    @Test
    fun `POST measurements returns 400 when a measurement value is negative`() {
        client
            .post()
            .uri("/api/measurements")
            .header("Authorization", "Bearer $authToken")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "measurementDate" to today.toString(),
                    "shoulders" to -1.0,
                ),
            ).exchange()
            .expectStatus().isBadRequest
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
