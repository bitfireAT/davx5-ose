/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.network

import android.content.Context
import at.bitfire.dav4jvm.exception.DavException
import at.bitfire.dav4jvm.exception.HttpException
import at.bitfire.davdroid.db.Credentials
import at.bitfire.davdroid.ui.setup.LoginInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.apache.commons.lang3.StringUtils
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI

/**
 * Implements Nextcloud Login Flow v2.
 *
 * See https://docs.nextcloud.com/server/latest/developer_manual/client_apis/LoginFlow/index.html#login-flow-v2
 */
class NextcloudLoginFlow(
    context: Context
): AutoCloseable {

    companion object {
        const val FLOW_V1_PATH = "index.php/login/flow"
        const val FLOW_V2_PATH = "index.php/login/v2"

        /** Path to DAV endpoint (e.g. `remote.php/dav`). Will be appended to the server URL returned by Login Flow. */
        const val DAV_PATH = "remote.php/dav"
    }

    val httpClient = HttpClient.Builder(context)
        .setForeground(true)
        .build()

    override fun close() {
        httpClient.close()
    }


    // Login flow state
    var loginUrl: HttpUrl? = null
    var pollUrl: HttpUrl? = null
    var token: String? = null


    suspend fun initiate(baseUrl: HttpUrl): HttpUrl? {
        loginUrl = null
        pollUrl = null
        token = null

        val json = postForJson(initiateUrl(baseUrl), "".toRequestBody())

        loginUrl = json.getString("login").toHttpUrlOrNull()
        json.getJSONObject("poll").let { poll ->
            pollUrl = poll.getString("endpoint").toHttpUrl()
            token = poll.getString("token")
        }

        return loginUrl
    }

    fun initiateUrl(baseUrl: HttpUrl): HttpUrl {
        val path = baseUrl.encodedPath

        if (path.endsWith(FLOW_V2_PATH))
            // already a Login Flow v2 URL
            return baseUrl

        if (path.endsWith(FLOW_V1_PATH))
            // Login Flow v1 URL, rewrite to v2
            return baseUrl.newBuilder()
                .encodedPath(path.replace(FLOW_V1_PATH, FLOW_V2_PATH))
                .build()

        // other URL, make it a Login Flow v2 URL
        return baseUrl.newBuilder()
            .addPathSegments(FLOW_V2_PATH)
            .build()
    }


    suspend fun fetchLoginInfo(): LoginInfo {
        val pollUrl = pollUrl ?: throw IllegalArgumentException("Missing pollUrl")
        val token = token ?: throw IllegalArgumentException("Missing token")

        // send HTTP request to request server, login name and app password
        val json = postForJson(pollUrl, "token=$token".toRequestBody("application/x-www-form-urlencoded".toMediaType()))

        // make sure server URL ends with a slash so that DAV_PATH can be appended
        val serverUrl = StringUtils.appendIfMissing(json.getString("server"), "/")

        return LoginInfo(
            baseUri = URI(serverUrl).resolve(DAV_PATH),
            credentials = Credentials(
                username = json.getString("loginName"),
                password = json.getString("appPassword")
            )
        )
    }


    private suspend fun postForJson(url: HttpUrl, requestBody: RequestBody): JSONObject = withContext(Dispatchers.IO) {
        val postRq = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()
        val response = runInterruptible {
            httpClient.okHttpClient.newCall(postRq).execute()
        }

        if (response.code != HttpURLConnection.HTTP_OK)
            throw HttpException(response)

        response.body?.use { body ->
            val mimeType = body.contentType() ?: throw DavException("Login Flow response without MIME type")
            if (mimeType.type != "application" || mimeType.subtype != "json")
                throw DavException("Invalid Login Flow response (not JSON)")

            // decode JSON
            return@withContext JSONObject(body.string())
        }

        throw DavException("Invalid Login Flow response (no body)")
    }

}