/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.davx5.feature.preconfiguredsettings.shared

import at.bitfire.davdroid.network.HttpClientBuilder
import at.bitfire.davdroid.network.lenientJson
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.SerializationException
import javax.inject.Inject

class PreconfigurationRepository @Inject constructor(
    private val httpClientBuilder: HttpClientBuilder
) {

    /**
     * Loads [PreconfiguredSettings] from [url].
     * @return An instance of [PreconfiguredSettings] parsed from the response of [url].
     * @throws InvalidPreconfiguredSettingsException If the URL responded with an error, or the response could not be parsed into [PreconfiguredSettings].
     */
    suspend fun fetchPreconfiguredSettings(url: String): PreconfiguredSettings {
        httpClientBuilder.build().use { client ->
            val response = client.get(url)
            val body = response.bodyAsText()

            val preconfiguredSettings: PreconfiguredSettings = try {
                if (response.status.isSuccess()) {
                    lenientJson.decodeFromString(body)
                } else {
                    throw InvalidPreconfiguredSettingsException(response.status, body, null)
                }
            } catch (e: SerializationException) {
                throw InvalidPreconfiguredSettingsException(response.status, body, e)
            } catch (e: IllegalArgumentException) {
                throw InvalidPreconfiguredSettingsException(response.status, body, e)
            }
            return preconfiguredSettings
        }
    }

}
