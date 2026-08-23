package com.satzwerk.export

import com.fasterxml.jackson.databind.JsonNode
import com.satzwerk.common.ConflictException
import com.satzwerk.common.RequestContext
import com.satzwerk.common.body
import com.satzwerk.common.handleErrors
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyValueAndAwait
import org.springframework.web.reactive.function.server.coRouter

@Configuration
class ExportRouter {
    @Bean
    fun exportRoutes(exportService: ExportService) =
        coRouter {
            "/api".nest {
                GET("/export") { request ->
                    handleErrors {
                        val ctx = RequestContext(request)
                        val export = exportService.exportForUser(ctx.userId())
                        ServerResponse.ok()
                            .header(
                                HttpHeaders.CONTENT_DISPOSITION,
                                ContentDisposition.attachment().filename("satzwerk-export.json").build().toString(),
                            )
                            .bodyValueAndAwait(export)
                    }
                }
                POST("/import") { request ->
                    handleErrors(extra = mapOf(ConflictException::class to HttpStatus.CONFLICT)) {
                        val ctx = RequestContext(request)
                        val exportBody = ctx.body<JsonNode>()
                        val summary = exportService.importForUser(ctx.userId(), exportBody)
                        ServerResponse.ok().bodyValueAndAwait(summary)
                    }
                }
            }
        }
}
