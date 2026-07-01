/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import at.bitfire.davdroid.resource.LocalResource
import io.ktor.http.Url
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncExceptionTest {

    @Test
    fun testWrapWithLocalResource_LocalResource_Exception() {
        val outer = mockk<LocalResource>()
        val inner = mockk<LocalResource>()
        val e = Exception()

        val result = assertSyncException {
            SyncException.wrapWithLocalResource(outer) {
                SyncException.wrapWithLocalResource(inner) {
                    throw e
                }
            }
        }

        assertEquals(inner, result.localResource)
        assertEquals(e, result.cause)
    }

    @Test
    fun testWrapWithLocalResource_LocalResource_SyncException() {
        val outer = mockk<LocalResource>()
        val inner = mockk<LocalResource>()
        val e = SyncException(Exception())

        val result = assertSyncException {
            SyncException.wrapWithLocalResource(outer) {
                SyncException.wrapWithLocalResource(inner) {
                    throw e
                }
            }
        }

        assertEquals(inner, result.localResource)
        assertEquals(e, result)
    }

    @Test
    fun testWrapWithLocalResource_RemoteResource_Exception() = runTest {
        val local = mockk<LocalResource>()
        val remote = mockk<Url>()
        val e = Exception()

        val result = assertSyncExceptionSuspending {
            SyncException.wrapWithLocalResourceSuspending(local) {
                SyncException.wrapWithRemoteResourceSuspending(remote) {
                    throw e
                }
            }
        }

        assertEquals(local, result.localResource)
        assertEquals(remote, result.remoteResource)
        assertEquals(e, result.cause)
    }

    @Test
    fun testWrapWithLocalResource_RemoteResource_SyncException() = runTest {
        val local = mockk<LocalResource>()
        val remote = mockk<Url>()
        val e = SyncException(Exception())

        val result = assertSyncExceptionSuspending {
            SyncException.wrapWithLocalResourceSuspending(local) {
                SyncException.wrapWithRemoteResourceSuspending(remote) {
                    throw e
                }
            }
        }

        assertEquals(local, result.localResource)
        assertEquals(remote, result.remoteResource)
        assertEquals(e, result)
    }


    @Test
    fun testWrapWithRemoteResource_LocalResource_Exception() = runTest {
        val remote = mockk<Url>()
        val local = mockk<LocalResource>()
        val e = Exception()

        val result = assertSyncExceptionSuspending {
            SyncException.wrapWithRemoteResourceSuspending(remote) {
                SyncException.wrapWithLocalResourceSuspending(local) {
                    throw e
                }
            }
        }

        assertEquals(local, result.localResource)
        assertEquals(remote, result.remoteResource)
        assertEquals(e, result.cause)
    }

    @Test
    fun testWrapWithRemoteResource_LocalResource_SyncException() = runTest {
        val remote = mockk<Url>()
        val local = mockk<LocalResource>()
        val e = SyncException(Exception())

        val result = assertSyncExceptionSuspending {
            SyncException.wrapWithRemoteResourceSuspending(remote) {
                SyncException.wrapWithLocalResourceSuspending(local) {
                    throw e
                }
            }
        }

        assertEquals(local, result.localResource)
        assertEquals(remote, result.remoteResource)
        assertEquals(e, result)
    }

    @Test
    fun testWrapWithRemoteResource_RemoteResource_Exception() = runTest {
        val outer = mockk<Url>()
        val inner = mockk<Url>()
        val e = Exception()

        val result = assertSyncExceptionSuspending {
            SyncException.wrapWithRemoteResourceSuspending(outer) {
                SyncException.wrapWithRemoteResourceSuspending(inner) {
                    throw e
                }
            }
        }

        assertEquals(inner, result.remoteResource)
        assertEquals(e, result.cause)
    }

    @Test
    fun testWrapWithRemoteResource_RemoteResource_SyncException() = runTest {
        val outer = mockk<Url>()
        val inner = mockk<Url>()
        val e = SyncException(Exception())

        val result = assertSyncExceptionSuspending {
            SyncException.wrapWithRemoteResourceSuspending(outer) {
                SyncException.wrapWithRemoteResourceSuspending(inner) {
                    throw e
                }
            }
        }

        assertEquals(inner, result.remoteResource)
        assertEquals(e, result)
    }


    @Test
    fun testUnwrap_Exception() {
        val e = Exception()

        var contextProvided = false
        val unwrapped = SyncException.unwrap(e) {
            contextProvided = true
        }
        assertEquals(e, unwrapped)
        assertFalse(contextProvided)
    }

    @Test
    fun testUnwrap_SyncException() {
        val e = Exception()
        val wrapped = SyncException(e)

        var contextProvided = false
        val unwrapped = SyncException.unwrap(wrapped) {
            assertEquals(wrapped, it)
            contextProvided = true
        }
        assertEquals(e, unwrapped)
        assertTrue(contextProvided)
    }


    // helpers

    fun assertSyncException(block: () -> Unit): SyncException {
        try {
            block()
        } catch(ex: Throwable) {
            if (ex is SyncException)
                return ex
        }
        throw AssertionError("Expected SyncException")
    }

    suspend fun assertSyncExceptionSuspending(block: suspend () -> Unit): SyncException {
        try {
            block()
        } catch (ex: Throwable) {
            if (ex is SyncException)
                return ex
        }
        throw AssertionError("Expected SyncException")
    }

}