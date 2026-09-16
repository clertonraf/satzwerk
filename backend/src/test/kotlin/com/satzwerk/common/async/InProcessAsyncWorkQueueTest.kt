package com.satzwerk.common.async

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class InProcessAsyncWorkQueueTest {
    @Test
    fun `submit processes work on a background worker`(): Unit =
        runBlocking {
            val processed = CopyOnWriteArrayList<String>()
            val processedLatch = CountDownLatch(1)

            withQueue(
                handler = { item ->
                    processed += item
                    processedLatch.countDown()
                },
            ) { queue ->
                assertTrue(queue.submit("invalidate-user"))

                assertTrue(processedLatch.await(2, TimeUnit.SECONDS))
                assertEquals(listOf("invalidate-user"), processed)
            }
        }

    @Test
    fun `submit drops work when the bounded queue is full`(): Unit =
        runBlocking {
            val workerStarted = CountDownLatch(1)
            val releaseWorker = CountDownLatch(1)
            val processed = CopyOnWriteArrayList<String>()

            withQueue(
                capacity = 1,
                handler = { item ->
                    processed += item
                    workerStarted.countDown()
                    releaseWorker.await(2, TimeUnit.SECONDS)
                },
            ) { queue ->
                assertTrue(queue.submit("first"))
                assertTrue(workerStarted.await(2, TimeUnit.SECONDS))

                assertTrue(queue.submit("second"))
                assertFalse(queue.submit("third"))
                assertEquals(listOf("first"), processed)

                releaseWorker.countDown()
            }

            assertEquals(listOf("first", "second"), processed)
        }

    @Test
    fun `worker keeps processing later items after one handler failure`(): Unit =
        runBlocking {
            val processed = CopyOnWriteArrayList<String>()
            val firstAttemptDone = CountDownLatch(1)
            val secondAttemptDone = CountDownLatch(1)

            withQueue(
                handler = { item ->
                    if (item == "first") {
                        firstAttemptDone.countDown()
                        error("boom")
                    }

                    processed += item
                    secondAttemptDone.countDown()
                },
            ) { queue ->
                assertTrue(queue.submit("first"))
                assertTrue(queue.submit("second"))

                assertTrue(firstAttemptDone.await(2, TimeUnit.SECONDS))
                assertTrue(secondAttemptDone.await(2, TimeUnit.SECONDS))
            }

            assertEquals(listOf("second"), processed)
        }

    @Test
    fun `submit rejects work after the worker scope is cancelled`(): Unit =
        runBlocking {
            val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
            val parentJob = SupervisorJob()
            val scope = CoroutineScope(parentJob + dispatcher)
            val queue =
                InProcessAsyncWorkQueue(
                    name = "test-queue",
                    capacity = 1,
                    scope = scope,
                    handler = { _: String -> },
                )

            try {
                parentJob.cancel()
                queue.awaitWorkerCompletion()

                assertFalse(queue.submit("late"))
            } finally {
                queue.stop()
                scope.cancel()
                dispatcher.close()
            }
        }

    @Test
    fun `stop cancels a blocked worker after the grace timeout`(): Unit =
        runBlocking {
            val handlerStarted = CountDownLatch(1)
            val blocker = CompletableDeferred<Unit>()

            val elapsed =
                measureElapsed {
                    withQueue(
                        handler = { _: String ->
                            handlerStarted.countDown()
                            blocker.await()
                        },
                    ) { queue ->
                        assertTrue(queue.submit("first"))
                        assertTrue(handlerStarted.await(2, TimeUnit.SECONDS))

                        queue.stop(gracefulShutdownTimeoutMillis = 50)
                        assertFalse(queue.submit("late"))
                    }
                }

            assertTrue(elapsed <= 500L)
        }

    private suspend fun withQueue(
        capacity: Int = 4,
        handler: suspend (String) -> Unit,
        block: suspend (InProcessAsyncWorkQueue<String>) -> Unit,
    ) {
        val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val queue =
            InProcessAsyncWorkQueue(
                name = "test-queue",
                capacity = capacity,
                scope = scope,
                handler = handler,
            )

        try {
            block(queue)
        } finally {
            queue.stop()
            scope.cancel()
            dispatcher.close()
        }
    }

    private suspend fun measureElapsed(block: suspend () -> Unit): Long {
        val startedAt = System.nanoTime()
        block()
        return (System.nanoTime() - startedAt).toDuration(DurationUnit.NANOSECONDS).inWholeMilliseconds
    }
}
