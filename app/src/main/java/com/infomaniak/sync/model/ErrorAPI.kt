package com.infomaniak.sync.model

import com.google.gson.annotations.SerializedName

class ErrorAPI {
    @SerializedName("error_code")
    val errorCode: Int = 0
    @SerializedName("error_type")
    val errorType: String? = null
    val error: String? = null
    val reason: String? = null
}
