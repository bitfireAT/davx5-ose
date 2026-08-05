/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import at.bitfire.dav4jvm.ktor.DavCollection
import at.bitfire.davdroid.resource.remote.WebDavCollection

class TestWebDavCollection(override val davCollection: DavCollection) : WebDavCollection
