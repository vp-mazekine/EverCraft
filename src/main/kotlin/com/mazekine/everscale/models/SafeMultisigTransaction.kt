package com.mazekine.everscale.models

data class SafeMultisigTransactions(
    val transactions: List<SafeMultisigTransaction>? = null
)

data class SafeMultisigTransaction(
    val id: String,
    val confirmationsMask: String,
    val signsRequired: Int,
    val signsReceived: Int,
    val creator: String,
    val index: Int,
    val dest: String,
    val value: String,
    val sendFlags: Int,
    val payload: String,
    val bounce: Boolean
)
