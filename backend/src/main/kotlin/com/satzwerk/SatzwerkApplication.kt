package com.satzwerk

import com.satzwerk.config.JwtProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableConfigurationProperties(JwtProperties::class)
@EnableScheduling
class SatzwerkApplication

@Suppress("SpreadOperator")
fun main(args: Array<String>) {
    runApplication<SatzwerkApplication>(*args)
}
