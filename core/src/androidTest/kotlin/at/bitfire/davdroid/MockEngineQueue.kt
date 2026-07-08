/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.Headers
import io.ktor.http.HttpStatusCode

/**
 * A helper for tests using Ktor's [MockEngine] that need a queue of canned HTTP responses,
 * similar to OkHttp's `MockWebServer.enqueue()` but at the Ktor engine level.
 *
 * Enqueue responses with [enqueue]; each response is consumed in order when the engine
 * receives a request. Access [engine] to pass to [io.ktor.client.HttpClient].
 * Inspect [engine]'s [MockEngine.requestHistory] to verify the requests that were received.
 */
class MockEngineQueue {

    data class Response(
        val status: HttpStatusCode,
        val body: String = "",
        val headers: Headers = Headers.Empty
    )

    private val queue = ArrayDeque<Response>()

    /** Schedules the next HTTP response to return. Responses are consumed in FIFO order. */
    fun enqueue(
        status: HttpStatusCode = HttpStatusCode.OK,
        body: String = "",
        headers: Headers = Headers.Empty
    ): MockEngineQueue {
        queue.addLast(Response(status, body, headers))
        return this
    }

    val engine: MockEngine = MockEngine { request ->
        val response = queue.removeFirstOrNull()
            ?: error("MockEngineQueue: unexpected request to ${request.url} - queue is empty")
        respond(response.body, response.status, response.headers)
    }
}
