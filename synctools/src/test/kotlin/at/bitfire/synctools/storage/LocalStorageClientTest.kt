/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.storage

import android.content.ContentProviderClient
import android.content.ContentValues
import android.net.Uri
import android.os.DeadObjectException
import android.os.RemoteException
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

private val URI = Uri.parse("dummy:")
private val CONTENT_VALUES = ContentValues()

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)      // required because main project uses Conscrypt, but unit tests do not
class LocalStorageClientTest {

    private val provider: ContentProviderClient = mockk()
    private val client = LocalStorageClient(provider)

    @Test
    fun `RemoteException should be wrapped`() {
        val remoteException = RemoteException()
        every { provider.insert(any(), any()) } throws remoteException

        val exception = assertFailsWith<LocalStorageException> {
            client.insert(URI, CONTENT_VALUES)
        }

        assertSame(remoteException, exception.cause)
        assertFalse(exception.softError)
    }

    @Test
    fun `DeadObjectException should be wrapped and marked as soft error`() {
        val deadObjectException = DeadObjectException()
        every { provider.insert(any(), any()) } throws deadObjectException

        val exception = assertFailsWith<LocalStorageException> {
            client.insert(URI, CONTENT_VALUES)
        }

        assertSame(deadObjectException, exception.cause)
        assertTrue(exception.softError)
    }

    @Test
    fun `RemoteException wrapped in RuntimeException should be wrapped`() {
        val runtimeException = RuntimeException(RemoteException())
        every { provider.insert(any(), any()) } throws runtimeException

        val exception = assertFailsWith<LocalStorageException> {
            client.insert(URI, CONTENT_VALUES)
        }

        assertSame(runtimeException, exception.cause)
        assertFalse(exception.softError)
    }

    @Test
    fun `DeadObjectException wrapped in RuntimeException should be wrapped and marked as soft error`() {
        val runtimeException = RuntimeException(DeadObjectException())
        every { provider.insert(any(), any()) } throws runtimeException

        val exception = assertFailsWith<LocalStorageException> {
            client.insert(URI, CONTENT_VALUES)
        }

        assertSame(runtimeException, exception.cause)
        assertTrue(exception.softError)
    }

    @Test
    fun `CancellationException should not be wrapped`() {
        val cancellationException = CancellationException()
        every { provider.insert(any(), any()) } throws cancellationException

        val exception = assertFails {
            client.insert(URI, CONTENT_VALUES)
        }

        assertSame(cancellationException, exception)
    }

    @Test
    fun `Random RuntimeException should not be wrapped`() {
        val runtimeException = RuntimeException()
        every { provider.insert(any(), any()) } throws runtimeException

        val exception = assertFails {
            client.insert(URI, CONTENT_VALUES)
        }

        assertSame(runtimeException, exception)
    }
}
