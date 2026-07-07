/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid

import at.bitfire.davdroid.MockEngineUtils.basic
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.Headers
import io.ktor.http.HttpStatusCode

object MockEngineUtils {
    /**
     * Provides a [MockEngine] that always responds an empty body with code 200. See [basic] with default arguments.
     */
    val MockEngine.Companion.Default get() = basic()

    /**
     * Creates a new [MockEngine] that always responds with the specified content and status code.
     */
    fun MockEngine.Companion.basic(
        content: String = "",
        statusCode: HttpStatusCode = HttpStatusCode.OK,
        headers: Headers = Headers.Empty
    ) = MockEngine {
        respond(content, statusCode, headers)
    }
}
