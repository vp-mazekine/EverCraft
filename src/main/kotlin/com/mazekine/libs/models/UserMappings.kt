package com.mazekine.libs.models

import com.google.gson.annotations.Expose

data class UserMappings(
    @Expose
    val addresses: MutableList<String> = mutableListOf(),
    @Expose
    var firstNotice: Boolean = false
)
