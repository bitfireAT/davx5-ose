package com.infomaniak.sync.model

import com.google.gson.annotations.SerializedName

class InfomaniakUser {
    private val id: Int = 0
    @SerializedName("user_id")
    private val userId: Int = 0
    val login: String? = null
    val email: String? = null
    private val firstname: String? = null
    private val lastname: String? = null
    @SerializedName("display_name")
    var displayName: String? = null
}
