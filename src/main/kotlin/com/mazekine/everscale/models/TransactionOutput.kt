package com.mazekine.everscale.models

data class TransactionOutput(
    val data: TransactionOutputData?,
    val errorMessage: String,
    val status: EVERRequestStatus
)

data class TransactionOutputData(
    val aborted: Boolean,
    val account: Address?,
    val balanceChange: String,
    val bounce: Boolean,
    val createdAt: Long,
    val data: TransactionData?,
    val direction: TransactionDirection,
    val error: String?,
    val fee: String,
    val id: String,
    val messageHash: String,
    val multisigTransactionId: String? = null,
    val originalOutputs: List<Transaction>,
    val originalValue: String,
    val sender: Address?,
    val status: TransactionStatus,
    val transactionHash: String?,
    val transactionLt: String?,
    val transactionTimeout: Long?,
    val transactionTimestamp: Long,
    val updatedAt: Long,
    val value: String
)