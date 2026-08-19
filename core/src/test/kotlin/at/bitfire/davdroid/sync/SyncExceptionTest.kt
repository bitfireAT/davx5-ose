/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import at.bitfire.davdroid.resource.local.LocalResource
import io.ktor.http.Url
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class SyncExceptionTest {

    @Test
    fun testWithExceptionContext_LocalResource_LocalResource() = runTest {
        val outer = mockk<LocalResource>()
        val inner = mockk<LocalResource>()
        val e = Exception()

        val result = assertWrapped {
            outer.withExceptionContext {
                inner.withExceptionContext {
                    throw e
                }
            }
        }

        assertEquals(inner, result.localResource)
        assertEquals(e, result.cause)
    }

    @Test
    fun testWithExceptionContext_LocalResource_RemoteResource() = runTest {
        val local = mockk<LocalResource>()
        val remote = Url("https://example.com")
        val e = Exception()

        val result = assertWrapped {
            local.withExceptionContext {
                remote.withExceptionContext {
                    throw e
                }
            }
        }

        assertEquals(local, result.localResource)
        assertEquals(remote, result.remoteResource)
        assertEquals(e, result.cause)
    }

    @Test
    fun testWithExceptionContext_RemoteResource_LocalResource() = runTest {
        val remote = Url("https://example.com")
        val local = mockk<LocalResource>()
        val e = Exception()

        val result = assertWrapped {
            remote.withExceptionContext {
                local.withExceptionContext {
                    throw e
                }
            }
        }

        assertEquals(local, result.localResource)
        assertEquals(remote, result.remoteResource)
        assertEquals(e, result.cause)
    }

    @Test
    fun testWithExceptionContext_RemoteResource_RemoteResource() = runTest {
        val outer = Url("https://example.com/outer")
        val inner = Url("https://example.com/inner")
        val e = Exception()

        val result = assertWrapped {
            outer.withExceptionContext {
                inner.withExceptionContext {
                    throw e
                }
            }
        }

        assertEquals(inner, result.remoteResource)
        assertEquals(e, result.cause)
    }

    @Test
    fun testWithExceptionContext_CancellationException() = runTest {
        val local = mockk<LocalResource>()
        val e = CancellationException()

        val result = assertThrown {
            local.withExceptionContext {
                throw e
            }
        }

        assertSame(e, result)
    }

    @Test
    fun testWithExceptionContext_CancellationException_Nested() = runTest {
        val local = mockk<LocalResource>()
        val remote = Url("https://example.com")
        val e = CancellationException()

        val result = assertThrown {
            local.withExceptionContext {
                remote.withExceptionContext {
                    throw e
                }
            }
        }

        assertSame(e, result)
    }

    @Test
    fun testWithExceptionContext_InterruptedException() = runTest {
        val remote = Url("https://example.com")
        val e = InterruptedException()

        val result = assertThrown {
            remote.withExceptionContext {
                throw e
            }
        }

        assertSame(e, result)
    }


    @Test
    fun testUnwrapContext_PlainException() {
        val e = Exception()

        val ctx = e.unwrapContext()
        assertEquals(e, ctx.cause)
        assertEquals(null, ctx.localResource)
        assertEquals(null, ctx.remoteResource)
    }

    @Test
    fun testUnwrapContext_WrappedException() = runTest {
        val e = Exception()
        val local = mockk<LocalResource>()
        val remote = Url("https://example.com")

        val result = assertWrapped {
            local.withExceptionContext {
                remote.withExceptionContext {
                    throw e
                }
            }
        }

        assertEquals(e, result.cause)
        assertEquals(local, result.localResource)
        assertEquals(remote, result.remoteResource)
    }


    // helpers

    /**
     * Runs [block], expecting it to throw, and returns the thrown [Throwable] as-is
     * (without unwrapping it), to verify that it wasn't wrapped into a [SyncException].
     */
    suspend fun assertThrown(block: suspend () -> Unit): Throwable {
        try {
            block()
        } catch (ex: Throwable) {
            return ex
        }
        throw AssertionError("Expected an exception to be thrown")
    }

    /**
     * Runs [block], expecting it to throw, and returns the [SyncExceptionContext]
     * recovered from the thrown exception via [unwrapContext].
     */
    suspend fun assertWrapped(block: suspend () -> Unit): SyncExceptionContext {
        try {
            block()
        } catch (ex: Throwable) {
            return ex.unwrapContext()
        }
        throw AssertionError("Expected an exception to be thrown")
    }

}
