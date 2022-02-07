package com.mazekine.everscale.models

data class AddressBalanceOutput(
    val data: AddressBalanceOutputData,
    val errorMessage: String,
    val status: EVERRequestStatus
)

data class AddressBalanceOutputData(
    val accountStatus: AccountStatus,
    val accountType: AccountType,
    val address: Address,
    val balance: String,
    val createdAt: Long,
    val id: String,
    val lastTransactionHash: String,
    val lastTransactionLt: String,
    val networkBalance: String,
    val syncUTime: Long,
    val updatedAt: Long
)
