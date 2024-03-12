/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/
package at.bitfire.davdroid

import android.net.Uri
import androidx.core.net.toUri

/**
 * Brand-specific constants like (non-theme) colors, homepage URLs etc.
 */
object Constants {

    const val DAVDROID_GREEN_RGBA = 0xFF8bc34a.toInt()

    val HOMEPAGE_URL = "https://www.davx5.com".toUri()
    const val HOMEPAGE_PATH_FAQ = "faq"
    const val HOMEPAGE_PATH_FAQ_SYNC_NOT_RUN = "synchronization-is-not-run-as-expected"
    const val HOMEPAGE_PATH_FAQ_LOCATION_PERMISSION = "wifi-ssid-restriction-location-permission"
    const val HOMEPAGE_PATH_OPEN_SOURCE = "donate"
    const val HOMEPAGE_PATH_PRIVACY = "privacy"
    const val HOMEPAGE_PATH_TESTED_SERVICES = "tested-with"

    val MANUAL_URL = "https://manual.davx5.com".toUri()
    const val MANUAL_PATH_WEBDAV_MOUNTS = "webdav_mounts.html"

    val COMMUNITY_URL = "https://github.com/bitfireAT/davx5-ose/discussions".toUri()

    val FEDIVERSE_HANDLE = "@davx5app@fosstodon.org"
    val FEDIVERSE_URL = "https://fosstodon.org/@davx5app".toUri()

    /**
     * Appends query parameters for anonymized usage statistics (app ID, version).
     * Can be used by the called Website to get an idea of which versions etc. are currently used.
     *
     * @param context   optional info about from where the URL was opened (like a specific Activity)
     */
    fun Uri.Builder.withStatParams(context: String? = null): Uri.Builder {
        appendQueryParameter("pk_campaign", BuildConfig.APPLICATION_ID)
        appendQueryParameter("app-version", BuildConfig.VERSION_NAME)

        if (context != null)
            appendQueryParameter("pk_kwd", context)

        return this
    }

}