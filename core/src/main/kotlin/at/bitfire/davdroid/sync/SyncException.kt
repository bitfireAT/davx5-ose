/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import at.bitfire.davdroid.resource.LocalResource
import io.ktor.http.Url

/**
 * Exception that wraps another notification together with potential information about
 * a local and/or remote resource that is related to the exception.
 */
class SyncException(cause: Throwable) : Exception(cause) {

    companion object {

        // provide lambda wrappers for setting the local/remote resource

        suspend fun <T> wrapWithLocalResource(localResource: LocalResource?, body: suspend () -> T): T {
            try {
                return body()
            } catch (e: SyncException) {
                if (localResource != null)
                    e.setLocalResourceIfNull(localResource)
                throw e
            } catch (e: Throwable) {
                throw if (localResource != null)
                    SyncException(e).setLocalResourceIfNull(localResource)
                else
                    e
            }
        }

        suspend fun <T> wrapWithRemoteResource(remoteResource: Url?, body: suspend () -> T): T {
            try {
                return body()
            } catch (e: SyncException) {
                if (remoteResource != null)
                    e.setRemoteResourceIfNull(remoteResource)
                throw e
            } catch (e: Throwable) {
                throw if (remoteResource != null)
                    SyncException(e).setRemoteResourceIfNull(remoteResource)
                else
                    e
            }
        }

        fun unwrap(e: Throwable, contextReceiver: (SyncException) -> Unit) =
            if (e is SyncException) {
                contextReceiver(e)
                e.cause!!
            } else
                e

    }


    var localResource: LocalResource? = null
        private set
    var remoteResource: Url? = null
        private set

    fun setLocalResourceIfNull(local: LocalResource): SyncException {
        if (localResource == null)
            localResource = local

        return this
    }

    fun setRemoteResourceIfNull(remote: Url): SyncException {
        if (remoteResource == null)
            remoteResource = remote

        return this
    }

    override fun toString(): String {
        return "SyncException(localResource=$localResource, remoteResource=$remoteResource, cause=$cause)"
    }

}