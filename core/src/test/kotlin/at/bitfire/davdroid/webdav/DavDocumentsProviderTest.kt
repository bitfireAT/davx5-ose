/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.webdav

import android.app.AuthenticationRequiredException
import android.content.Context
import android.os.OperationCanceledException
import at.bitfire.dav4jvm.ktor.exception.HttpException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.FileNotFoundException
import java.net.SocketTimeoutException
import java.util.concurrent.CancellationException

@RunWith(RobolectricTestRunner::class)
class DavDocumentsProviderTest {

    private val context: Context = RuntimeEnvironment.getApplication()

    @Test
    fun `toDocumentsProviderException() converts CancellationException to OperationCanceledException`() {
        val input = CancellationException("cancelled")

        val result = input.toDocumentsProviderException(context)

        assertTrue(result is OperationCanceledException)
    }

    @Test
    fun `toDocumentsProviderException() converts 401 to AuthenticationRequiredException`() = runTest {
        val input = httpException(HttpStatusCode.Unauthorized)

        val result = input.toDocumentsProviderException(context)

        assertTrue(result is AuthenticationRequiredException)
    }

    @Test
    @Config(sdk = [25])
    fun `toDocumentsProviderException() converts 401 to FileNotFoundException below API 26`() = runTest {
        val input = httpException(HttpStatusCode.Unauthorized)

        val result = input.toDocumentsProviderException(context)

        assertTrue(result is FileNotFoundException)
    }

    @Test
    fun `toDocumentsProviderException() keeps FileNotFoundException`() {
        val input = FileNotFoundException()

        val result = input.toDocumentsProviderException(context)

        assertSame(input, result)
    }

    @Test
    fun `toDocumentsProviderException() keeps IllegalArgumentException`() {
        val input = NumberFormatException()

        val result = input.toDocumentsProviderException(context)

        assertSame(input, result)
    }

    @Test
    fun `toDocumentsProviderException() wraps HTTP 502 into FileNotFoundException`() = runTest {
        val input = httpException(HttpStatusCode.BadGateway)

        val result = input.toDocumentsProviderException(context)

        assertTrue(result is FileNotFoundException)
        assertSame(input, result.cause)
        assertEquals(input.message, result.message)
    }

    @Test
    fun `toDocumentsProviderException() wraps exception without message`() {
        val input = SocketTimeoutException()

        val result = input.toDocumentsProviderException(context)

        assertEquals("SocketTimeoutException", result.message)
    }


    private suspend fun httpException(status: HttpStatusCode): HttpException =
        HttpClient(MockEngine {
            respond("", status)
        }).use { client ->
            HttpException.fromResponse(client.get("http://example.com"))
        }

}
