/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource.remote

import at.bitfire.dav4jvm.ktor.DavAddressBook

/**
 * Remote CardDAV collection, as used for address books.
 */
class CardDavCollection(override val davCollection: DavAddressBook) : BaseWebDavCollection(davCollection)
