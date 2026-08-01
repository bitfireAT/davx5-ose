/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import at.bitfire.davdroid.resource.LocalResource
import at.bitfire.davdroid.sync.SyncException.Companion.unwrap
import at.bitfire.davdroid.sync.SyncException.Companion.wrapWithLocalResource
import at.bitfire.davdroid.sync.SyncException.Companion.wrapWithRemoteResource
import io.ktor.http.Url

/**
 * Exception that wraps another notification together with potential information about
 * a local and/or remote resource that is related to the exception.
 */
class SyncException(cause: Throwable) : Exception(cause) {

    /**
     * Context information extracted from a [SyncException] with [unwrap].
     */
    data class Unwrapped(
        val cause: Throwable,
        val localResource: LocalResource? = null,
        val remoteResource: Url? = null
    )

    companion object {

        private suspend fun <T> wrapContext(with: (SyncException) -> Unit, body: suspend () -> T): T =
            try {
                body()
            } catch (e: SyncException) {
                with(e)
                throw e
            } catch (e: Throwable) {
                throw SyncException(e).also(with)
            }

        suspend fun <T> wrapWithLocalResource(localResource: LocalResource?, body: suspend () -> T): T =
            if (localResource == null)
                body()
            else
                wrapContext({ it.setLocalResourceIfNull(localResource) }, body)

        suspend fun <T> wrapWithRemoteResource(remoteResource: Url?, body: suspend () -> T): T =
            if (remoteResource == null)
                body()
            else
                wrapContext({ it.setRemoteResourceIfNull(remoteResource) }, body)

        fun unwrap(e: Throwable): Unwrapped =
            if (e is SyncException)
                Unwrapped(e.cause ?: e, e.localResource, e.remoteResource)
            else
                Unwrapped(e)

    }


    var localResource: LocalResource? = null
        private set
    var remoteResource: Url? = null
        private set

    /** Sets [localResource] unless already set, so the innermost (closest to the actual
     *  failure) [wrapWithLocalResource] call wins when wraps are nested. */
    fun setLocalResourceIfNull(local: LocalResource): SyncException {
        if (localResource == null)
            localResource = local

        return this
    }

    /** Sets [remoteResource] unless already set, so the innermost (closest to the actual
     *  failure) [wrapWithRemoteResource] call wins when wraps are nested. */
    fun setRemoteResourceIfNull(remote: Url): SyncException {
        if (remoteResource == null)
            remoteResource = remote

        return this
    }

    override fun toString(): String {
        return "SyncException(localResource=$localResource, remoteResource=$remoteResource, cause=$cause)"
    }

}