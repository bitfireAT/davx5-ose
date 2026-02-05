/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.accounts.Account
import at.bitfire.davdroid.network.HttpClientBuilder
import at.bitfire.davdroid.util.DavUtils.toURIorNull
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import ezvcard.util.DataUri
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.isSuccess
import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Provider

/**
 * Downloads a separate resource that is referenced during synchronization, for instance in
 * a vCard with `PHOTO:<external URL>`.
 *
 * The [ResourceRetriever] only sends authentication for URLs on the same domain as the
 * original URL. For instance, if the vCard that references a photo is taken from
 * `example.com` ([originalHost]), then [retrieve] will send authentication
 * when downloading `https://example.com/photo.jpg`, but not for `https://external-hoster.com/photo.jpg`.
 *
 * @param account       account to build authentication from
 * @param originalHost  client only authenticates for the domain of this host
 */
class ResourceRetriever @AssistedInject constructor(
    @Assisted private val account: Account,
    @Assisted private val originalHost: String,
    private val httpClientBuilder: Provider<HttpClientBuilder>,
    private val logger: Logger
) {

    @AssistedFactory
    interface Factory {
        fun create(account: Account, originalHost: String): ResourceRetriever
    }

    /**
     * Retrieves the given resource and returns it as an in-memory blob.
     * Supports HTTP/HTTPS (→ will download) and data (→ will decode) URLs.
     *
     * Authentication is handled as described in [ResourceRetriever].
     *
     * @param url       URL of the resource to download (`http`, `https` or `data` scheme)
     *
     * @return blob of requested resource, or `null` on error or when the URL scheme is not supported
     */
    suspend fun retrieve(url: String): ByteArray? =
        try {
            when (url.toURIorNull()?.scheme?.lowercase()) {
                "data" ->
                    DataUri.parse(url).data     // may throw IllegalArgumentException

                "http", "https" ->
                    download(url)                   // may throw various exceptions

                else ->
                    null
            }
        } catch (e: Exception) {
            logger.log(Level.SEVERE, "Couldn't retrieve resource", e)
            null
        }

    /**
     * Downloads the resource from the given HTTP/HTTPS URL.
     *
     * Doesn't catch any exceptions!
     */
    private suspend fun download(url: String): ByteArray? =
        httpClientBuilder
            .get()
            .fromAccount(account, authDomain = originalHost)  // restricts authentication to original domain
            .followRedirects(true)      // allow redirects
            .buildKtor()
            .use { httpClient ->
                val response = httpClient.get(url)
                if (response.status.isSuccess())
                    return response.bodyAsBytes()
                else {
                    logger.warning("Couldn't download external resource (${response.status})")
                    null
                }
            }

}