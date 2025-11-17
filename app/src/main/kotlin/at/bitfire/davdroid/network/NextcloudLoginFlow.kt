/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.network

import androidx.annotation.VisibleForTesting
import at.bitfire.davdroid.settings.Credentials
import at.bitfire.davdroid.ui.setup.LoginInfo
import at.bitfire.davdroid.util.SensitiveString.Companion.toSensitiveString
import at.bitfire.davdroid.util.withTrailingSlash
import at.bitfire.vcard4android.GroupMethod
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType
import io.ktor.http.path
import kotlinx.serialization.Serializable
import java.net.URI
import javax.inject.Inject

/**
 * Implements Nextcloud Login Flow v2.
 *
 * See https://docs.nextcloud.com/server/latest/developer_manual/client_apis/LoginFlow/index.html#login-flow-v2
 */
class NextcloudLoginFlow @Inject constructor(
    httpClientBuilder: HttpClientBuilder
) {

    private val httpClient = httpClientBuilder.buildKtor()

    // Login flow state
    var pollUrl: Url? = null
    var token: String? = null

    /**
     * Starts Nextcloud Login Flow v2.
     *
     * @param baseUrl   Nextcloud login flow or base URL
     *
     * @return URL that should be opened in the browser (login screen)
     */
    suspend fun start(baseUrl: Url): Url {
        // reset fields in case something goes wrong
        pollUrl = null
        token = null

        // POST to login flow URL in order to receive endpoint data
        val result = httpClient.post(loginFlowUrl(baseUrl))
        val endpointData: EndpointData = result.body()

        // save endpoint data for polling
        pollUrl = Url(endpointData.poll.endpoint)
        token = endpointData.poll.token

        return Url(endpointData.login)
    }

    @VisibleForTesting      // TODO test
    internal fun loginFlowUrl(baseUrl: Url): Url {
        return when {
            // already a Login Flow v2 URL
            baseUrl.encodedPath.endsWith(FLOW_V2_PATH) ->
                baseUrl

            // Login Flow v1 URL, rewrite to v2
            baseUrl.encodedPath.endsWith(FLOW_V1_PATH) -> {
                // drop "[index.php/login]/flow" from the end and append "/v2"
                val v2Segments = baseUrl.segments.dropLast(1) + "v2"
                val builder = URLBuilder(baseUrl)
                builder.path(*v2Segments.toTypedArray())
                builder.build()
            }

            // other URL, make it a Login Flow v2 URL
            else ->
                URLBuilder(baseUrl)
                    .appendPathSegments(FLOW_V2_PATH.split('/'))
                    .build()
        }
    }

    suspend fun fetchLoginInfo(): LoginInfo {
        val pollUrl = pollUrl ?: throw IllegalArgumentException("Missing pollUrl")
        val token = token ?: throw IllegalArgumentException("Missing token")

        // send HTTP request to request server, login name and app password
        val result = httpClient.post(pollUrl) {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("token=$token")
        }
        val loginData: LoginData = result.body()

        // make sure server URL ends with a slash so that DAV_PATH can be appended
        val serverUrl = loginData.server.withTrailingSlash()

        return LoginInfo(
            baseUri = URI(serverUrl).resolve(DAV_PATH),
            credentials = Credentials(
                username = loginData.loginName,
                password = loginData.appPassword.toSensitiveString()
            ),
            suggestedGroupMethod = GroupMethod.CATEGORIES
        )
    }


    /**
     * Represents the JSON response that is returned on the first call to `/login/v2`.
     */
    @Serializable
    private data class EndpointData(
        val poll: Poll,
        val login: String
    ) {
        @Serializable
        data class Poll(
            val token: String,
            val endpoint: String
        )
    }

    /**
     * Represents the JSON response that is returned by the polling endpoint.
     */
    @Serializable
    private data class LoginData(
        val server: String,
        val loginName: String,
        val appPassword: String
    )


    companion object {
        const val FLOW_V1_PATH = "index.php/login/flow"
        const val FLOW_V2_PATH = "index.php/login/v2"

        /** Path to DAV endpoint (e.g. `remote.php/dav`). Will be appended to the server URL returned by Login Flow. */
        const val DAV_PATH = "remote.php/dav"
    }

}