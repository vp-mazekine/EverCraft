package com.mazekine.everscale.models

data class TransactionToSign(
    val multisigTxId: String? = null,
    val txId: String? = null,
    val messageHash: String? = null,
    val txHash: String? = null
)
