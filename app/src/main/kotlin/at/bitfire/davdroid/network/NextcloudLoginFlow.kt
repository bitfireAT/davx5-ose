/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.network

import androidx.annotation.VisibleForTesting
import at.bitfire.dav4jvm.exception.DavException
import at.bitfire.dav4jvm.exception.HttpException
import at.bitfire.davdroid.settings.Credentials
import at.bitfire.davdroid.ui.setup.LoginInfo
import at.bitfire.davdroid.util.SensitiveString.Companion.toSensitiveString
import at.bitfire.davdroid.util.withTrailingSlash
import at.bitfire.vcard4android.GroupMethod
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType
import io.ktor.http.path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
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
    var loginUrl: Url? = null
    var pollUrl: Url? = null
    var token: String? = null

    suspend fun initiate(baseUrl: Url): Url? {
        loginUrl = null
        pollUrl = null
        token = null

        val json = postForJson(initiateUrl(baseUrl), "")

        loginUrl = Url(json.getString("login"))
        json.getJSONObject("poll").let { poll ->
            pollUrl = Url(poll.getString("endpoint"))
            token = poll.getString("token")
        }

        return loginUrl
    }

    @VisibleForTesting      // TODO test
    internal fun initiateUrl(baseUrl: Url): Url {
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
        val json = postForJson(pollUrl, "token=$token", ContentType.Application.FormUrlEncoded)

        // make sure server URL ends with a slash so that DAV_PATH can be appended
        val serverUrl = json.getString("server").withTrailingSlash()

        return LoginInfo(
            baseUri = URI(serverUrl).resolve(DAV_PATH),
            credentials = Credentials(
                username = json.getString("loginName"),
                password = json.getString("appPassword").toSensitiveString()
            ),
            suggestedGroupMethod = GroupMethod.CATEGORIES
        )
    }

    private suspend fun postForJson(url: Url, body: String, contentType: ContentType? = null): JSONObject = withContext(Dispatchers.IO) {
        val response = httpClient.post(url) {
            if (contentType != null)
                contentType(contentType)
            setBody(body)
        }

        if (response.status != HttpStatusCode.OK)
            throw HttpException(/* TODO replace by response */
                response.status.description,
                statusCode = response.status.value,
                requestExcerpt = null,
                responseExcerpt = null
            )

        val body = response.bodyAsText()
        val mimeType = response.contentType() ?: throw DavException("Login Flow response without MIME type")
        if (!mimeType.match(ContentType.Application.Json))
            throw DavException("Invalid Login Flow response (not JSON)")

        // decode JSON
        return@withContext JSONObject(body)
    }


    companion object {
        const val FLOW_V1_PATH = "index.php/login/flow"
        const val FLOW_V2_PATH = "index.php/login/v2"

        /** Path to DAV endpoint (e.g. `remote.php/dav`). Will be appended to the server URL returned by Login Flow. */
        const val DAV_PATH = "remote.php/dav"
    }

}