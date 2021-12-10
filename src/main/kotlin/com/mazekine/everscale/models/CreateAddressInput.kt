package com.mazekine.everscale.models

/**
 * createAddress request input
 *
 * @property accountType Type of the created wallet
 * @property confirmations Number of confirmations required to release the transaction
 * @property custodians Total number of custodians in the wallet (including 1 signature of the API itself)
 * @property custodiansPublicKeys Less 1 signature of the API
 * @property workchainId Typically 0
 * @constructor Create empty Create address input
 */
data class CreateAddressInput (
    val accountType: AccountType,
    val confirmations: Int,
    val custodians: Int,
    val custodiansPublicKeys: List<String>,
    val workchainId: Int = 0
) : JsonCompatibleInput()