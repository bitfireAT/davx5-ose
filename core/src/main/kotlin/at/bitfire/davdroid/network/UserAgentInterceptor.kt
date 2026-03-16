/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.network

import at.bitfire.davdroid.ProductIds
import dagger.Reusable
import okhttp3.Interceptor
import okhttp3.Response
import java.util.Locale
import java.util.logging.Logger
import javax.inject.Inject

@Reusable
class UserAgentInterceptor @Inject constructor(
    logger: Logger,
    productIds: ProductIds
): Interceptor {

    private val userAgent = productIds.httpUserAgent

    init {
        logger.info("Will set User-Agent: $userAgent")
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val locale = Locale.getDefault()
        val request = chain.request().newBuilder()
            .header("User-Agent", userAgent)
            .header("Accept-Language", "${locale.language}-${locale.country}, ${locale.language};q=0.7, *;q=0.5")
            .build()
        return chain.proceed(request)
    }

}