package com.mazekine.everscale.models

import com.google.gson.annotations.Expose

/**
 * checkAddress input model
 *
 * @property address
 * @constructor Create empty Check address input
 */
data class CheckAddressInput(
    @Expose
    val address: String
) : JsonCompatibleInput()