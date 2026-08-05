/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource.remote

import at.bitfire.dav4jvm.ktor.DavCollection

/**
 * Provides remote collection access as required for [at.bitfire.davdroid.sync.SyncManager].
 *
 * Currently, call sites just call through [davCollection], but the goal is to provide all remote collection
 * operations that are required for synchronization here.
 */
interface WebDavCollection {

    val davCollection: DavCollection

}
