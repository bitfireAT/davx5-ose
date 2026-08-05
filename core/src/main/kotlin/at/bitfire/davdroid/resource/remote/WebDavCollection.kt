/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource.remote

import at.bitfire.dav4jvm.ktor.DavCollection

/**
 * Provides remote collection access as required for [at.bitfire.davdroid.sync.SyncManager].
 */
interface WebDavCollection {

    val davCollection: DavCollection

}
