package com.satzwerk.export

import com.satzwerk.common.ErrorHandlerOption
import com.satzwerk.common.RequestContext
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

    suspend fun import(request: ServerRequest): ServerResponse =
        handleErrors(ErrorHandlerOption.WithConflict) {
            val ctx = RequestContext(request)
            val dto = ctx.body<UserDataExportDto>()
            val summary = exportService.importForUser(ctx.userId(), dto)
            ServerResponse.ok().bodyValueAndAwait(summary)
        }
}
