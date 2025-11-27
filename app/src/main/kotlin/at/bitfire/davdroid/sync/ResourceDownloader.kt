/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.accounts.Account
import at.bitfire.davdroid.network.HttpClientBuilder
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.Url
import io.ktor.http.isSuccess
import java.io.IOException
import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Provider

/**
 * Downloads a separate resource that is referenced during synchronization, for instance in
 * a vCard with `PHOTO:<external URL>`.
 *
 * The [ResourceDownloader] only sends authentication for URLs on the same domain as the
 * original URL. For instance, if the vCard that references a photo is taken from
 * `example.com ([originalHost]), then [download] will send authentication
 * when downloading `https://example.com/photo.jpg`, but not for `https://external-hoster.com/photo.jpg`.
 *
 * @param account       account to build authentication from
 * @param originalHost  client only authenticates for the domain of this host
 */
class ResourceDownloader @AssistedInject constructor(
    @Assisted private val account: Account,
    @Assisted private val originalHost: String,
    private val httpClientBuilder: Provider<HttpClientBuilder>,
    private val logger: Logger
) {

    @AssistedFactory
    interface Factory {
        fun create(account: Account, originalHost: String): ResourceDownloader
    }

    suspend fun download(url: Url): ByteArray? {
        httpClientBuilder
            .get()
            .fromAccount(account, onlyHost = originalHost)
            .followRedirects(true)      // allow redirects
            .buildKtor()
            .use { httpClient ->
                try {
                    val response = httpClient.get(url)
                    if (response.status.isSuccess())
                        return response.bodyAsBytes()
                    else
                        logger.warning("Couldn't download external resource (${response.status})")
                } catch(e: IOException) {
                    logger.log(Level.SEVERE, "Couldn't download external resource", e)
                }
            }
        return null
    }

}