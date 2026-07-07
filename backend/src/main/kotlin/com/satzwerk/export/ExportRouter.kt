package com.satzwerk.export

import com.satzwerk.common.RequestContext
import com.satzwerk.common.body
import com.satzwerk.common.handleErrors
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
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
                    handleErrors(withConflict = true) {
                        val ctx = RequestContext(request)
                        val dto = ctx.body<UserDataExportDto>()
                        val summary = exportService.importForUser(ctx.userId(), dto)
                        ServerResponse.ok().bodyValueAndAwait(summary)
                    }
                }
            }
        }
}
