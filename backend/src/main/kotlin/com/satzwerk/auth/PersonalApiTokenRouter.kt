package com.satzwerk.auth

import com.satzwerk.common.RequestContext
import com.satzwerk.common.handleErrors
import com.satzwerk.common.requireJwtSession
import com.satzwerk.common.validateOrBadRequest
import jakarta.validation.Validator
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyValueAndAwait
import org.springframework.web.reactive.function.server.buildAndAwait
import org.springframework.web.reactive.function.server.coRouter

@Configuration
class PersonalApiTokenRouter {
    @Bean
    fun personalApiTokenRoutes(
        personalApiTokenService: PersonalApiTokenService,
        validator: Validator,
    ) = coRouter {
        "/api/tokens".nest {
            POST("") { request ->
                handleErrors {
                    requireJwtSession(request)
                    val ctx = RequestContext(request)
                    val userId = ctx.userId()
                    val body = ctx.body(CreatePersonalApiTokenRequest::class.java)
                    validateOrBadRequest(validator, body) {
                        val (token, raw) = personalApiTokenService.create(userId, body.name, body.scopes)
                        ServerResponse.status(HttpStatus.CREATED).bodyValueAndAwait(
                            CreatedPersonalApiTokenResponse(
                                id = requireNotNull(token.id),
                                name = token.name,
                                scopes = token.scopes(),
                                createdAt = token.createdAt,
                                token = raw,
                            ),
                        )
                    }
                }
            }
            GET("") { request ->
                handleErrors {
                    requireJwtSession(request)
                    val ctx = RequestContext(request)
                    val tokens = personalApiTokenService.list(ctx.userId())
                    ServerResponse.ok().bodyValueAndAwait(
                        tokens.map { t ->
                            PersonalApiTokenResponse(
                                id = requireNotNull(t.id),
                                name = t.name,
                                scopes = t.scopes(),
                                createdAt = t.createdAt,
                                lastUsedAt = t.lastUsedAt,
                            )
                        },
                    )
                }
            }
            DELETE("/{id}") { request ->
                handleErrors {
                    requireJwtSession(request)
                    val ctx = RequestContext(request)
                    personalApiTokenService.revoke(ctx.userId(), ctx.pathId("id"))
                    ServerResponse.noContent().buildAndAwait()
                }
            }
        }
    }
}
