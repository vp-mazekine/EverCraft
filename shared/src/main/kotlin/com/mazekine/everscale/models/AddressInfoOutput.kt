package com.mazekine.everscale.models

data class AddressInfoOutput(
    val data: AddressInfoOutputData,
    val errorMessage: String,
    val status: EVERRequestStatus
)

data class AddressInfoOutputData(
    val accountType: AccountType,
    val address: Address,
    val balance: String,
    val confirmations: Int,
    val createdAt: Long,
    val custodians: Int,
    val custodiansPublicKeys: List<String>,
    val id: String,
    val updatedAt: Long
)
