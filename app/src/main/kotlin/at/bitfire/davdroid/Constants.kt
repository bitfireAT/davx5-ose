/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/
package at.bitfire.davdroid

object Constants {

    const val DAVDROID_GREEN_RGBA = 0xFF8bc34a.toInt()

    // gplay billing
    const val BILLINGCLIENT_CONNECTION_MAX_RETRIES = 4

    /**
     * Context label for [org.apache.commons.lang3.exception.ContextedException].
     * Context value is the [at.bitfire.davdroid.resource.LocalResource]
     * which is related to the exception cause.
     */
    const val EXCEPTION_CONTEXT_LOCAL_RESOURCE = "localResource"

    /**
     * Context label for [org.apache.commons.lang3.exception.ContextedException].
     * Context value is the [okhttp3.HttpUrl] of the remote resource
     * which is related to the exception cause.
     */
    const val EXCEPTION_CONTEXT_REMOTE_RESOURCE = "remoteResource"

}
