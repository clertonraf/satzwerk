package com.satzwerk.sessions

import com.satzwerk.analytics.AnalyticsReadCache
import com.satzwerk.common.TransactionRunner
import org.springframework.stereotype.Component

@Component
class WorkoutSessionDeps(
    val setLogService: SetLogService,
    val sessionQueryRepository: SessionQueryRepository,
    val analyticsReadCache: AnalyticsReadCache,
    val transactionRunner: TransactionRunner,
)
