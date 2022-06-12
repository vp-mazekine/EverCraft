package com.mazekine.libs.models

import com.google.gson.annotations.Expose

data class UserMappings(
    @Expose
    val addresses: MutableList<String> = mutableListOf(),
    @Expose
    var notifications: UserNotificationsStatus = UserNotificationsStatus()
)

data class UserMappings_0_2_2(
    @Expose
    val addresses: MutableList<String> = mutableListOf(),
    @Expose
    var firstNotice: Boolean = false
)


data class UserNotificationsStatus(
    @Expose
    var welcomeMessage: Boolean = false,
    @Expose
    var walletUpgradeRequired: Boolean = false
)