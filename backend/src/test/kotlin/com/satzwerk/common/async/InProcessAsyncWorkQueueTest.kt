package com.satzwerk.common.async

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
}
