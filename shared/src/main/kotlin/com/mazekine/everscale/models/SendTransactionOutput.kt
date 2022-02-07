package com.mazekine.everscale.models

data class SendTransactionOutput(
    val data: TransactionOutputData,
    val errorMessage: String?,
    val status: EVERRequestStatus
)