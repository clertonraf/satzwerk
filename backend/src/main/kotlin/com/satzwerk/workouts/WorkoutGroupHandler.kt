package com.satzwerk.workouts

import com.satzwerk.common.RequestContext
import com.satzwerk.common.body
import com.satzwerk.common.handleErrors
import com.satzwerk.common.validateOrBadRequest
import jakarta.validation.Validator
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyValueAndAwait
import org.springframework.web.reactive.function.server.buildAndAwait

@Component
class WorkoutGroupHandler(
    private val workoutGroupService: WorkoutGroupService,
    private val validator: Validator,
) {
    suspend fun create(request: ServerRequest): ServerResponse =
        handleErrors {
            val ctx = RequestContext(request)
            val body = ctx.body<CreateGroupRequest>()
            validateOrBadRequest(validator, body) {
                val response =
                    workoutGroupService.create(
                        ctx.userId(),
                        ctx.pathId("planId"),
                        body,
                    )
                ServerResponse.status(HttpStatus.CREATED).bodyValueAndAwait(response)
            }
        }

    suspend fun update(request: ServerRequest): ServerResponse =
        handleErrors {
            val ctx = RequestContext(request)
            val body = ctx.body<UpdateGroupRequest>()
            validateOrBadRequest(validator, body) {
                val response =
                    workoutGroupService.update(
                        ctx.userId(),
                        ctx.pathId("planId"),
                        ctx.pathId("groupId"),
                        body,
                    )
                ServerResponse.ok().bodyValueAndAwait(response)
            }
        }

    suspend fun delete(request: ServerRequest): ServerResponse =
        handleErrors {
            val ctx = RequestContext(request)
            workoutGroupService.delete(
                ctx.userId(),
                ctx.pathId("planId"),
                ctx.pathId("groupId"),
            )
            ServerResponse.noContent().buildAndAwait()
        }
}
