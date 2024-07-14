/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import at.bitfire.davdroid.resource.LocalResource
import io.mockk.mockk
import okhttp3.HttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncExceptionTest {

    @Test
    fun testWrapWithLocalResource_LocalResource_Exception() {
        val outer = mockk<LocalResource<*>>()
        val inner = mockk<LocalResource<*>>()
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
        val outer = mockk<LocalResource<*>>()
        val inner = mockk<LocalResource<*>>()
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
    fun testWrapWithLocalResource_RemoteResource_Exception() {
        val local = mockk<LocalResource<*>>()
        val remote = mockk<HttpUrl>()
        val e = Exception()

        val result = assertSyncException {
            SyncException.wrapWithLocalResource(local) {
                SyncException.wrapWithRemoteResource(remote) {
                    throw e
                }
            }
        }

        assertEquals(local, result.localResource)
        assertEquals(remote, result.remoteResource)
        assertEquals(e, result.cause)
    }

    @Test
    fun testWrapWithLocalResource_RemoteResource_SyncException() {
        val local = mockk<LocalResource<*>>()
        val remote = mockk<HttpUrl>()
        val e = SyncException(Exception())

        val result = assertSyncException {
            SyncException.wrapWithLocalResource(local) {
                SyncException.wrapWithRemoteResource(remote) {
                    throw e
                }
            }
        }

        assertEquals(local, result.localResource)
        assertEquals(remote, result.remoteResource)
        assertEquals(e, result)
    }
    

    @Test
    fun testWrapWithRemoteResource_LocalResource_Exception() {
        val remote = mockk<HttpUrl>()
        val local = mockk<LocalResource<*>>()
        val e = Exception()

        val result = assertSyncException {
            SyncException.wrapWithRemoteResource(remote) {
                SyncException.wrapWithLocalResource(local) {
                    throw e
                }
            }
        }

        assertEquals(local, result.localResource)
        assertEquals(remote, result.remoteResource)
        assertEquals(e, result.cause)
    }

    @Test
    fun testWrapWithRemoteResource_LocalResource_SyncException() {
        val remote = mockk<HttpUrl>()
        val local = mockk<LocalResource<*>>()
        val e = SyncException(Exception())

        val result = assertSyncException {
            SyncException.wrapWithRemoteResource(remote) {
                SyncException.wrapWithLocalResource(local) {
                    throw e
                }
            }
        }

        assertEquals(local, result.localResource)
        assertEquals(remote, result.remoteResource)
        assertEquals(e, result)
    }

    @Test
    fun testWrapWithRemoteResource_RemoteResource_Exception() {
        val outer = mockk<HttpUrl>()
        val inner = mockk<HttpUrl>()
        val e = Exception()

        val result = assertSyncException {
            SyncException.wrapWithRemoteResource(outer) {
                SyncException.wrapWithRemoteResource(inner) {
                    throw e
                }
            }
        }

        assertEquals(inner, result.remoteResource)
        assertEquals(e, result.cause)
    }

    @Test
    fun testWrapWithRemoteResource_RemoteResource_SyncException() {
        val outer = mockk<HttpUrl>()
        val inner = mockk<HttpUrl>()
        val e = SyncException(Exception())

        val result = assertSyncException {
            SyncException.wrapWithRemoteResource(outer) {
                SyncException.wrapWithRemoteResource(inner) {
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

}