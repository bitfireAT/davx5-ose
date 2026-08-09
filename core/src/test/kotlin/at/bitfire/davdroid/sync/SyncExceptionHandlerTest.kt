/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.accounts.Account
import android.os.DeadObjectException
import android.os.RemoteException
import at.bitfire.dav4jvm.ktor.exception.DavException
import at.bitfire.dav4jvm.ktor.exception.HttpException
import at.bitfire.davdroid.accounts.LegacyAccount
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.sync.account.InvalidAccountException
import at.bitfire.synctools.storage.LocalStorageException
import at.bitfire.synctools.test.assertThrows
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit4.MockKRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.ConscryptMode
import java.io.IOException
import java.security.cert.CertificateException
import java.util.concurrent.CancellationException
import java.util.logging.Logger
import javax.net.ssl.SSLHandshakeException

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class SyncExceptionHandlerTest {

    @get:Rule
    val mockKRule = MockKRule(this)

    @RelaxedMockK
    lateinit var syncNotificationManager: SyncNotificationManager

    @MockK
    lateinit var syncNotificationManagerFactory: SyncNotificationManager.Factory

    private val context = RuntimeEnvironment.getApplication()

    private val collection = Collection(
        id = 1,
        type = Collection.TYPE_CALENDAR,
        url = Url("https://example.com/"),
        displayName = "title"
    )

    @Before
    fun setUp() {
        every { syncNotificationManagerFactory.create(any()) } returns syncNotificationManager
    }


    // classifySyncException()

    @Test
    fun `classifySyncException() rethrows DeadObjectException wrapped in LocalStorageException`() {
        val exception = LocalStorageException("provider error", DeadObjectException())

        val action = handler().classifySyncException(exception)

        assertEquals(SyncExceptionHandler.SyncErrorAction.Rethrow, action)
    }

    @Test
    fun `classifySyncException() rethrows unwrapped DeadObjectException`() {
        val exception = DeadObjectException()

        val action = handler().classifySyncException(exception)

        assertEquals(SyncExceptionHandler.SyncErrorAction.Rethrow, action)
    }

    @Test
    fun `classifySyncException() rethrows CancellationException`() {
        val exception = CancellationException()

        val action = handler().classifySyncException(exception)

        assertEquals(SyncExceptionHandler.SyncErrorAction.Rethrow, action)
    }

    @Test
    fun `classifySyncException() rethrows InvalidAccountException`() {
        val exception = InvalidAccountException(Account("test@example.com", "test"))

        val action = handler().classifySyncException(exception)

        assertEquals(SyncExceptionHandler.SyncErrorAction.Rethrow, action)
    }

    @Test
    fun `classifySyncException() logs a warning for SSLHandshakeException caused by a rejected certificate`() {
        val exception = SSLHandshakeException("rejected").apply { initCause(CertificateException()) }

        val action = handler().classifySyncException(exception)

        assertTrue(action is SyncExceptionHandler.SyncErrorAction.LogWarning)
    }

    @Test
    fun `classifySyncException() treats SSLHandshakeException as soft error`() {
        val exception = SSLHandshakeException("handshake failed")

        val action = handler().classifySyncException(exception)

        assertTrue(action is SyncExceptionHandler.SyncErrorAction.SoftError)
        assertNotNull((action as SyncExceptionHandler.SyncErrorAction.SoftError).notifyMessage)
    }

    @Test
    fun `classifySyncException() treats IOException as soft error`() {
        val exception = IOException("network down")

        val action = handler().classifySyncException(exception)

        assertTrue(action is SyncExceptionHandler.SyncErrorAction.SoftError)
        assertNotNull((action as SyncExceptionHandler.SyncErrorAction.SoftError).notifyMessage)
    }

    @Test
    fun `classifySyncException() treats UnauthorizedException as hard error`() = runTest {
        val exception = httpException(HttpStatusCode.Unauthorized)

        val action = handler().classifySyncException(exception)

        assertTrue(action is SyncExceptionHandler.SyncErrorAction.HardError)
    }

    @Test
    fun `classifySyncException() treats ServiceUnavailableException as soft error with a delay`() = runTest {
        val exception = httpException(HttpStatusCode.ServiceUnavailable, HttpHeaders.RetryAfter to "60")

        val action = handler().classifySyncException(exception)

        assertTrue(action is SyncExceptionHandler.SyncErrorAction.SoftError)
        assertNotNull((action as SyncExceptionHandler.SyncErrorAction.SoftError).delayUntil)
    }

    @Test
    fun `classifySyncException() treats HttpException as hard error`() = runTest {
        val exception = httpException(HttpStatusCode.BadRequest)

        val action = handler().classifySyncException(exception)

        assertTrue(action is SyncExceptionHandler.SyncErrorAction.HardError)
    }

    @Test
    fun `classifySyncException() treats DavException as hard error`() {
        val exception = DavException("something went wrong")

        val action = handler().classifySyncException(exception)

        assertTrue(action is SyncExceptionHandler.SyncErrorAction.HardError)
    }

    @Test
    fun `classifySyncException() treats LocalStorageException as hard error`() {
        val exception = LocalStorageException("provider error")

        val action = handler().classifySyncException(exception)

        assertTrue(action is SyncExceptionHandler.SyncErrorAction.HardError)
    }

    @Test
    fun `classifySyncException() treats RemoteException as hard error`() {
        val exception = RemoteException("ipc failed")

        val action = handler().classifySyncException(exception)

        assertTrue(action is SyncExceptionHandler.SyncErrorAction.HardError)
    }

    @Test
    fun `classifySyncException() treats an unclassified exception as hard error`() {
        val exception = RuntimeException("boom")

        val action = handler().classifySyncException(exception)

        assertEquals(SyncExceptionHandler.SyncErrorAction.HardError("Unclassified sync error", "boom"), action)
    }


    // handleException()

    @Test
    fun `handleException() rethrows`() = runTest {
        val exception = CancellationException()

        assertThrows<CancellationException> {
            handler().handleException(exception, collection, null, null)
        }
        coVerify(exactly = 0) {
            syncNotificationManager.notifyException(
                syncDataType = any(),
                collectionId = any(),
                message = any(),
                title = any(),
                e = any(),
                local = any(),
                remote = any()
            )
        }
    }

    @Test
    fun `handleException() logs without notifying`() = runTest {
        val exception = SSLHandshakeException("rejected").apply { initCause(CertificateException()) }

        val result = handler().handleException(exception, collection, null, null)

        assertEquals(SyncExceptionHandler.SyncErrorResult.NoError, result)
        coVerify(exactly = 0) {
            syncNotificationManager.notifyException(
                syncDataType = any(),
                collectionId = any(),
                message = any(),
                title = any(),
                e = any(),
                local = any(),
                remote = any()
            )
        }
    }

    @Test
    fun `handleException() notifies and returns a soft error`() = runTest {
        val exception = IOException("network down")

        val result = handler().handleException(exception, collection, null, null)

        assertEquals(SyncExceptionHandler.SyncErrorResult.SoftError(delayUntil = null), result)
        coVerify(exactly = 1) {
            syncNotificationManager.notifyException(
                syncDataType = SyncDataType.EVENTS,
                collectionId = 1L,
                message = any(),
                title = "title",
                e = exception,
                local = null,
                remote = null
            )
        }
    }

    @Test
    fun `handleException() notifies and returns a hard error`() = runTest {
        val exception = RuntimeException("boom")

        val result = handler().handleException(exception, collection, null, null)

        assertEquals(SyncExceptionHandler.SyncErrorResult.HardError, result)
        coVerify(exactly = 1) {
            syncNotificationManager.notifyException(
                syncDataType = SyncDataType.EVENTS,
                collectionId = 1L,
                message = "boom",
                title = "title",
                e = exception,
                local = null,
                remote = null
            )
        }
    }


    private fun handler(dataType: SyncDataType = SyncDataType.EVENTS) = SyncExceptionHandler(
        accountId = LegacyAccount(Account("test@example.com", "test")),
        dataType = dataType,
        context = context,
        logger = Logger.getLogger(javaClass.name),
        syncNotificationManagerFactory = syncNotificationManagerFactory
    )

    private suspend fun httpException(status: HttpStatusCode, vararg headers: Pair<String, String>): HttpException {
        val client = HttpClient(MockEngine {
            respond(
                "",
                status,
                headersOf(*headers.map { it.first to listOf(it.second) }.toTypedArray())
            )
        })
        return HttpException.fromResponse(client.get("http://example.com"))
    }

}
