package com.satzwerk.common.async

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

class InProcessAsyncWorkQueue<T>(
    name: String,
    capacity: Int,
    scope: CoroutineScope,
    private val handler: suspend (T) -> Unit,
) {
    private val logger = LoggerFactory.getLogger(InProcessAsyncWorkQueue::class.java)
    private val queueName = name
    private val channel = Channel<T>(requirePositiveCapacity(capacity))

    private val worker: Job =
        scope.launch(CoroutineName("async-work-$name")) {
            for (item in channel) {
                runCatching { handler(item) }
                    .onFailure { error ->
                        if (error is CancellationException) {
                            throw error
                        }
                        logger.error("Async work item failed on queue {}", queueName, error)
                    }
            }
        }

    init {
        worker.invokeOnCompletion { error ->
            channel.close(error)
        }
    }

    fun submit(item: T): Boolean {
        val result = channel.trySend(item)
        if (result.isFailure) {
            logger.warn("Dropping async work item because queue {} is full or closed", queueName)
            return false
        }

        return true
    }

    suspend fun stop() {
        channel.close()
        worker.join()
    }

    internal suspend fun awaitWorkerCompletion() {
        worker.join()
    }
}

private fun requirePositiveCapacity(capacity: Int): Int {
    require(capacity > 0) { "Queue capacity must be greater than zero" }
    return capacity
}
