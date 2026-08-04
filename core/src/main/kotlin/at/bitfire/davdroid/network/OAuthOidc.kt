/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.network

import at.bitfire.dav4jvm.ktor.exception.HttpException
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.appendPathSegments
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.http.auth.parseAuthorizationHeader
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.AuthorizationServiceDiscovery
import net.openid.appauth.ResponseTypeValues
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Provider


class OAuthOidc @Inject constructor(
    private val httpClientBuilder: Provider<HttpClientBuilder>,
    private val oAuthIntegration: OAuthIntegration
) {
    private var serviceConfig: AuthorizationServiceConfiguration? = null

    suspend fun fetchServiceConfig(baseUrl: Url) {
        serviceConfig = null

        createClient().use { client ->
            var discoveryBaseUrl = baseUrl

            val baseResponse = client.get(baseUrl)
            val resourceMetadata = baseResponse.headers
                .getAll(HttpHeaders.WWWAuthenticate)
                // TODO: this is not fully spec compliant because it doesn't parse multiple challenges in one header
                // the `parseAuthorizationHeaders` function would do that, but it's marked as InternalApi
                ?.mapNotNull { parseAuthorizationHeader(it) }
                ?.firstNotNullOfOrNull { header ->
                    if (header is HttpAuthHeader.Parameterized &&
                        header.authScheme.equals("Bearer", ignoreCase = true)
                    ) {
                        header.parameter("resource_metadata")
                    } else null
                }

            if (resourceMetadata != null) {
                val resourceResponse = client.get(resourceMetadata)
                if (!resourceResponse.status.isSuccess())
                    throw HttpException.fromResponse(resourceResponse)

                val resourceObject: ResourceMetadata = resourceResponse.body()

                discoveryBaseUrl = resourceObject.authorizationServers.first()
            }

            val discoveryUrl = URLBuilder(discoveryBaseUrl)
                .appendPathSegments(
                    AuthorizationServiceConfiguration.WELL_KNOWN_PATH,
                    AuthorizationServiceConfiguration.OPENID_CONFIGURATION_RESOURCE
                )
                .build()
            val discoveryResponse = client.get(discoveryUrl)
            if (!discoveryResponse.status.isSuccess())
                throw HttpException.fromResponse(discoveryResponse)

            val discoveryJson = JSONObject(discoveryResponse.bodyAsText())
            val discovery = AuthorizationServiceDiscovery(discoveryJson)
            serviceConfig = AuthorizationServiceConfiguration(discovery)
        }
    }

    fun signIn(clientId: String, scope: String, locale: String?): AuthorizationRequest {
        val serviceConfig = serviceConfig ?: throw IllegalArgumentException("Missing serviceConfig")

        val builder = AuthorizationRequest.Builder(
            serviceConfig,
            clientId,
            ResponseTypeValues.CODE,
            oAuthIntegration.redirectUri
        )
        return builder
            .setScope(scope)
            .setUiLocales(locale)
            .build()
    }

    private fun createClient() = httpClientBuilder.get().buildKtor()

    @Serializable
    private data class ResourceMetadata(
        @SerialName("authorization_servers") val authorizationServers: List<Url>
    )

}