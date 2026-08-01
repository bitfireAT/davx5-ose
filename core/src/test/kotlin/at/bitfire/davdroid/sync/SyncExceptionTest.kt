/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import at.bitfire.davdroid.resource.LocalResource
import io.ktor.http.Url
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncExceptionTest {

    @Test
    fun testWrapWithLocalResource_LocalResource_Exception() = runTest {
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
    fun testWrapWithLocalResource_LocalResource_SyncException() = runTest {
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
    fun testWrapWithLocalResource_RemoteResource_SyncException() = runTest {
        val local = mockk<LocalResource>()
        val remote = mockk<Url>()
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
    fun testWrapWithRemoteResource_LocalResource_Exception() = runTest {
        val remote = mockk<Url>()
        val local = mockk<LocalResource>()
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
    fun testWrapWithRemoteResource_LocalResource_SyncException() = runTest {
        val remote = mockk<Url>()
        val local = mockk<LocalResource>()
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
    fun testWrapWithRemoteResource_RemoteResource_Exception() = runTest {
        val outer = mockk<Url>()
        val inner = mockk<Url>()
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
    fun testWrapWithRemoteResource_RemoteResource_SyncException() = runTest {
        val outer = mockk<Url>()
        val inner = mockk<Url>()
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

        val ctx = SyncException.unwrap(e)
        assertEquals(e, ctx.cause)
        assertEquals(null, ctx.localResource)
        assertEquals(null, ctx.remoteResource)
    }

    @Test
    fun testUnwrap_SyncException() {
        val e = Exception()
        val local = mockk<LocalResource>()
        val remote = Url("https://example.com")
        val wrapped = SyncException(e).setLocalResourceIfNull(local).setRemoteResourceIfNull(remote)

        val ctx = SyncException.unwrap(wrapped)
        assertEquals(e, ctx.cause)
        assertEquals(local, ctx.localResource)
        assertEquals(remote, ctx.remoteResource)
    }


    // helpers

    suspend fun assertSyncException(block: suspend () -> Unit): SyncException {
        try {
            block()
        } catch(ex: Throwable) {
            if (ex is SyncException)
                return ex
        }
        throw AssertionError("Expected SyncException")
    }

}
