package com.satzwerk.common

import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.mono
import org.springframework.stereotype.Component
import org.springframework.transaction.NoTransactionException
import org.springframework.transaction.reactive.TransactionSynchronization
import org.springframework.transaction.reactive.TransactionSynchronizationManager
import org.springframework.transaction.reactive.TransactionalOperator
import org.springframework.transaction.reactive.executeAndAwait
import reactor.core.publisher.Mono

interface TransactionRunner {
    suspend fun <T> required(block: suspend () -> T): T

    suspend fun afterCommit(block: suspend () -> Unit)
}

@Component
class R2dbcTransactionRunner(
    private val transactionalOperator: TransactionalOperator,
) : TransactionRunner {
    override suspend fun <T> required(block: suspend () -> T): T = transactionalOperator.executeAndAwait { block() }

    override suspend fun afterCommit(block: suspend () -> Unit) {
        val synchronizationManager = currentSynchronizationManager()
        if (
            synchronizationManager == null ||
            !synchronizationManager.isSynchronizationActive ||
            !synchronizationManager.isActualTransactionActive
        ) {
            block()
            return
        }

        synchronizationManager.registerSynchronization(
            object : TransactionSynchronization {
                override fun afterCommit(): Mono<Void> = mono { block() }.then()
            },
        )
    }

    private suspend fun currentSynchronizationManager(): TransactionSynchronizationManager? =
        try {
            TransactionSynchronizationManager.forCurrentTransaction().awaitSingle()
        } catch (_: NoTransactionException) {
            null
        }
}
