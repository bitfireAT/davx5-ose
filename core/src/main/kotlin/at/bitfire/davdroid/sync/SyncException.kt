/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import at.bitfire.davdroid.resource.LocalResource
import io.ktor.http.Url

/**
 * A throwable together with the local/remote resource that was being processed
 * when it occurred, if any. Use
 *
 * - `withExceptionContext` to attach context when a resource is being processed, and
 * - [Throwable.unwrapContext] to retrieve it after catching an exception.
 *
 * If multiple `withExceptionContext` calls are nested, the innermost ones are unwrapped.
 */
data class SyncExceptionContext(
    val cause: Throwable,
    val localResource: LocalResource? = null,
    val remoteResource: Url? = null
)

/**
 * The exception that actually carries the context. Only used internally.
 */
private class SyncException(val context: SyncExceptionContext) : Exception(context.cause)

private suspend fun <T> wrapContext(
    buildContext: (SyncExceptionContext) -> SyncExceptionContext,
    body: suspend () -> T
): T =
    try {
        body()
    } catch (e: SyncException) {
        throw SyncException(buildContext(e.context))
    } catch (e: Throwable) {
        throw SyncException(buildContext(SyncExceptionContext(e)))
    }

/**
 * Runs [body], tagging any exception it throws with this local resource as context
 * (unless a more deeply nested [withExceptionContext] already claimed it), recoverable later via
 * [Throwable.unwrapContext].
 */
suspend fun <T> LocalResource?.withExceptionContext(body: suspend () -> T): T {
    val local = this
    return if (local == null)
        body()
    else
        wrapContext(
            buildContext = { oldContext ->
                // don't overwrite innermost local resource, if already set
                if (oldContext.localResource == null)
                    oldContext.copy(localResource = local)
                else
                    oldContext
            },
            body = body
        )
}

/**
 * Runs [body], tagging any exception it throws with this remote resource URL as context
 * (unless a more deeply nested [withExceptionContext] already claimed it), recoverable later via
 * [Throwable.unwrapContext].
 */
suspend fun <T> Url?.withExceptionContext(body: suspend () -> T): T {
    val remote = this
    return if (remote == null)
        body()
    else
        wrapContext(
            buildContext = { oldContext ->
                // don't overwrite innermost remote resource, if already set
                if (oldContext.remoteResource == null)
                    oldContext.copy(remoteResource = remote)
                else
                    oldContext
            },
            body = body
        )
}

/**
 * Extracts the original cause and any local/remote resource context recorded via
 * `withExceptionContext`, if available.
 */
fun Throwable.unwrapContext(): SyncExceptionContext =
    if (this is SyncException)
        context
    else
        SyncExceptionContext(this)
