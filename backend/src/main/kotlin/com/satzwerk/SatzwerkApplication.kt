package com.satzwerk

import com.satzwerk.config.JwtProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableConfigurationProperties(JwtProperties::class)
class SatzwerkApplication

@Suppress("SpreadOperator")
fun main(args: Array<String>) {
    runApplication<SatzwerkApplication>(*args)
}
