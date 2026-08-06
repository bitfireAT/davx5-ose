/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource.remote

import at.bitfire.dav4jvm.ktor.DavCalendar

/**
 * Remote CalDAV collection, as used for calendars, jtx board collections and task lists.
 */
class CalDavCollection(override val davCollection: DavCalendar) : BaseWebDavCollection(davCollection)
