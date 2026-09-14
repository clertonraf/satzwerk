package com.satzwerk.export

import com.fasterxml.jackson.databind.ObjectMapper
import com.satzwerk.common.TransactionRunner
import com.satzwerk.medications.MedicationLogRepository
import com.satzwerk.medications.MedicationRepository
import org.springframework.stereotype.Component

@Component
class ExportSupportDeps(
    val medicationRepository: MedicationRepository,
    val medicationLogRepository: MedicationLogRepository,
    val objectMapper: ObjectMapper,
    val transactionRunner: TransactionRunner,
)
