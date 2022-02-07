package com.mazekine.everscale.models

import com.google.gson.Gson

open class JsonCompatibleInput {
    fun toJson() = Gson().toJson(this)
}