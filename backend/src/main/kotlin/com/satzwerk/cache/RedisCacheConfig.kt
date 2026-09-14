package com.satzwerk.cache

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory
import org.springframework.data.redis.core.ReactiveStringRedisTemplate

@Configuration
class RedisCacheConfig {
    @Bean
    fun reactiveStringRedisTemplate(connectionFactory: ReactiveRedisConnectionFactory): ReactiveStringRedisTemplate =
        ReactiveStringRedisTemplate(connectionFactory)
}
