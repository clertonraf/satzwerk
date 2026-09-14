package com.satzwerk

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class DockerComposeConfigTest {
    @Test
    fun `redis service configures bounded LRU cache memory`() {
        val composePath = Path.of("..", "docker-compose.yml").normalize()
        val compose = Files.readString(composePath)
        val redisSection =
            Regex("""(?ms)^  redis:\n(.*?)(?:^  [a-z]|\z)""")
                .find(compose)
                ?.value

        assertNotNull(redisSection)
        assertTrue(redisSection!!.contains("--maxmemory"))
        assertTrue(redisSection.contains("256mb"))
        assertTrue(redisSection.contains("--maxmemory-policy"))
        assertTrue(redisSection.contains("allkeys-lru"))
    }
}
