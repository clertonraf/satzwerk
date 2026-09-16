package com.satzwerk.common.async

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicLong

class InProcessAsyncWorkQueue<T>(
    name: String,
    capacity: Int,
    scope: CoroutineScope,
    private val handler: suspend (T) -> Unit,
) {
    private val logger = LoggerFactory.getLogger(InProcessAsyncWorkQueue::class.java)
    private val queueName = name
    private val channel = Channel<T>(requirePositiveCapacity(capacity))
    private val droppedWorkItemCount = AtomicLong(0)

    private val worker: Job =
        scope.launch(CoroutineName("async-work-$name")) {
            for (item in channel) {
                runCatching { handler(item) }
                    .onFailure { error ->
                        when (error) {
                            is CancellationException -> throw error
                            is Exception -> logger.error("Async work item failed on queue {}", queueName, error)
                            else -> throw error
                        }
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
            logDroppedWorkItem()
            return false
        }

        return true
    }

    suspend fun stop(gracefulShutdownTimeoutMillis: Long = DEFAULT_SHUTDOWN_TIMEOUT_MILLIS) {
        channel.close()

        if (awaitWorkerCompletion(gracefulShutdownTimeoutMillis)) {
            return
        }

        val droppedBufferedItemCount = drainBufferedItems()
        worker.cancel("Timed out stopping async work queue $queueName")

        if (awaitWorkerCompletion(gracefulShutdownTimeoutMillis)) {
            logger.warn(
                "Cancelled async work queue {} after {}ms grace period and dropped {} buffered item(s)",
                queueName,
                gracefulShutdownTimeoutMillis,
                droppedBufferedItemCount,
            )
            return
        }

        logger.warn(
            "Async work queue {} did not stop within {}ms after cancellation; dropped {} buffered item(s)",
            queueName,
            gracefulShutdownTimeoutMillis,
            droppedBufferedItemCount,
        )
    }

    internal suspend fun awaitWorkerCompletion() {
        worker.join()
    }

    private suspend fun awaitWorkerCompletion(timeoutMillis: Long): Boolean {
        require(timeoutMillis > 0) { "Shutdown timeout must be greater than zero" }
        return withTimeoutOrNull(timeoutMillis) {
            worker.join()
            true
        } == true
    }

    private fun drainBufferedItems(): Long {
        var droppedItems = 0L
        while (true) {
            val result = channel.tryReceive()
            if (result.isFailure) {
                return droppedItems
            }
            droppedItems += 1
        }
    }

    private fun logDroppedWorkItem() {
        val droppedCount = droppedWorkItemCount.incrementAndGet()
        if (droppedCount == 1L || droppedCount % DROP_LOG_EVERY_NTH_ITEM == 0L) {
            logger.warn(
                "Dropping async work item because queue {} is full or closed ({} dropped so far)",
                queueName,
                droppedCount,
            )
        }
    }
}

private const val DEFAULT_SHUTDOWN_TIMEOUT_MILLIS = 1_000L
private const val DROP_LOG_EVERY_NTH_ITEM = 64L

private fun requirePositiveCapacity(capacity: Int): Int {
    require(capacity > 0) { "Queue capacity must be greater than zero" }
    return capacity
}
