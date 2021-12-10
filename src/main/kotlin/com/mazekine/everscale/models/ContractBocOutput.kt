package com.mazekine.everscale.models

data class ContractBocOutput (
    val result: ContractBocOutputResult
)

data class ContractBocOutputResult(
    val boc: String
)