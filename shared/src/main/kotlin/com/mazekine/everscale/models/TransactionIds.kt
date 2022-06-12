package com.mazekine.everscale.models

data class TransactionIds (
    val id: String,
    val messageHash: String,
    val transactionHash: String? = null,
    val msigId: String? = null
)