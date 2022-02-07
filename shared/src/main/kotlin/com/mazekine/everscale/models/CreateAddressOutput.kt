package com.mazekine.everscale.models

data class CreateAddressOutput(
    val data: Address,
    val errorMessage: String,
    val status: EVERRequestStatus
)