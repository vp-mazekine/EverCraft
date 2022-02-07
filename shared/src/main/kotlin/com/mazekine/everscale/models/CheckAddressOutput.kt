package com.mazekine.everscale.models

data class CheckAddressOutput(
    val status: String,
    val data: CheckAddressResult
)

data class CheckAddressResult(
    val valid: Boolean
)