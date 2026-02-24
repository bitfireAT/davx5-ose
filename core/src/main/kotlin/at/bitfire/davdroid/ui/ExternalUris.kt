/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.net.toUri

/**
 * Links to to external pages (Web site, manual, social media etc.)
 */
object ExternalUris {

    /**
     * URLs of the DAVx5 homepage
     */
    @Suppress("unused")     // build variants
    object Homepage {

        val baseUrl
            get() = "https://www.davx5.com".toUri()

        const val PATH_FAQ = "faq"
        const val PATH_FAQ_SYNC_NOT_RUN = "synchronization-is-not-run-as-expected"
        const val PATH_FAQ_LOCATION_PERMISSION = "wifi-ssid-restriction-location-permission"
        const val PATH_OPEN_SOURCE = "donate"
        const val PATH_PRIVACY = "privacy"
        const val PATH_TESTED_SERVICES = "tested-with"

        const val PATH_ORGANIZATIONS = "organizations"
        const val PATH_ORGANIZATIONS_DAVX5_SELECT = "davx5-select"
        const val PATH_ORGANIZATIONS_MANAGED = "managed-davx5"
        const val PATH_ORGANIZATIONS_TRY_IT = "try-it-for-free"
    }


    /**
     * URLs of the DAVx5 Manual
     */
    object Manual {

        val baseUrl
            get() = "https://manual.davx5.com".toUri()

        const val PATH_ACCOUNTS_COLLECTIONS = "accounts_collections.html"
        const val FRAGMENT_SERVICE_DISCOVERY = "how-does-service-discovery-work"

        const val PATH_INTRODUCTION = "introduction.html"
        const val FRAGMENT_AUTHENTICATION_METHODS = "authentication-methods"

        const val PATH_SETTINGS = "settings.html"
        const val FRAGMENT_APP_SETTINGS = "app-wide-settings"
        const val FRAGMENT_ACCOUNT_SETTINGS = "account-settings"

        const val PATH_WEBDAV_PUSH = "webdav_push.html"
        const val PATH_WEBDAV_MOUNTS = "webdav_mounts.html"

    }


    /**
     * URLs of DAVx5 social sites
     */
    object Social {

        val discussionsUrl
            get() = "https://github.com/bitfireAT/davx5-ose/discussions".toUri()

        const val fediverseHandle = "@davx5app@fosstodon.org"
        val fediverseUrl
            get() = "https://fosstodon.org/@davx5app".toUri()

    }


    // helpers

    /**
     * Appends query parameters for anonymized usage statistics:
     *
     * - current package name and version (like "at.bitfire.davdroid/4.5.9")
     * - Android version (like "16.1")
     * - screen name (like "WifiPermissionsScreen")
     *
     * Can be used by the called Website to get an idea of which versions etc. are currently used.
     *
     * @param context   used to determine package name and version (optional)
     * @param screen    info about from where the URL was opened, like a specific screen (optional)
     */
    fun Uri.Builder.withStatParams(
        context: Context? = null,
        screen: String? = null
    ): Uri.Builder {
        if (context != null) {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            appendQueryParameter("pk_campaign", "${context.packageName}/${PackageInfoCompat.getLongVersionCode(packageInfo)}")
            appendQueryParameter("android-version", Build.VERSION.RELEASE)
        }

        if (screen != null)
            appendQueryParameter("pk_kwd", screen)

        return this
    }

}