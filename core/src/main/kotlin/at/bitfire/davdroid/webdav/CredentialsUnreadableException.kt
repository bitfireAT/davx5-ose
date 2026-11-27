/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.webdav

/**
 * Thrown by [CredentialsStore.getCredentials] when credentials are stored for a mount, but they can't be read
 * (for instance because the encryption key is lost or the stored data is corrupt).
 *
 * This is different from "no credentials stored" (which is `null`): requests must not silently continue without
 * authentication, the user has to enter the credentials again.
 */
class CredentialsUnreadableException(mountId: Long, cause: Throwable) :
    Exception("Stored credentials of WebDAV mount $mountId can't be read", cause)
