package com.satzwerk.export

import com.satzwerk.common.body
import com.satzwerk.common.handleErrors
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyValueAndAwait

@Component
class ExportHandler(
    private val exportService: ExportService,
) {
    suspend fun export(request: ServerRequest): ServerResponse =
        handleErrors(request) { ctx ->
            val export = exportService.exportForUser(ctx.userId())
            ServerResponse.ok()
                .header(
                    HttpHeaders.CONTENT_DISPOSITION,
                    ContentDisposition.attachment().filename("satzwerk-export.json").build().toString(),
                )
                .bodyValueAndAwait(export)
        }

    suspend fun import(request: ServerRequest): ServerResponse =
        handleErrors(request, withConflict = true) { ctx ->
            val dto = ctx.body<UserDataExportDto>()
            val summary = exportService.importForUser(ctx.userId(), dto)
            ServerResponse.ok().bodyValueAndAwait(summary)
        }
}
