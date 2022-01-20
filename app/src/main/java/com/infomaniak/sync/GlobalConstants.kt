package com.infomaniak.sync

object GlobalConstants {

    const val SYNC_INFOMANIAK = "https://sync.infomaniak.com"

    private const val API_ENDPOINT = "https://api.infomaniak.com/1"
    private const val LOGIN_ENDPOINT = "https://login.infomaniak.com"

    const val TOKEN_LOGIN_URL = "$LOGIN_ENDPOINT/token"
    const val PASSWORD_API_URL = "$API_ENDPOINT/profile/password"
    const val PROFILE_API_URL = "$API_ENDPOINT/profile"
}