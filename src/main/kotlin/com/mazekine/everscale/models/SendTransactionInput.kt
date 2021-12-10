package com.mazekine.everscale.models

import java.util.*

data class SendTransactionInput(
    val bounce: Boolean = false,
    val fromAddress: String,
    val id: UUID = UUID.randomUUID(),
    val outputs: List<SendTransactionData>
) : JsonCompatibleInput()

data class SendTransactionData(
    val outputType: TransactionSendOutputType,
    val recipientAddress: String,
    val value: String
) : JsonCompatibleInput()