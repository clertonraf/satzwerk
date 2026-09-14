package com.satzwerk.common

import org.springframework.stereotype.Component
import org.springframework.transaction.reactive.TransactionalOperator
import org.springframework.transaction.reactive.executeAndAwait

interface TransactionRunner {
    suspend fun <T> required(block: suspend () -> T): T
}

@Component
class R2dbcTransactionRunner(
    private val transactionalOperator: TransactionalOperator,
) : TransactionRunner {
    override suspend fun <T> required(block: suspend () -> T): T = transactionalOperator.executeAndAwait { block() }
}
