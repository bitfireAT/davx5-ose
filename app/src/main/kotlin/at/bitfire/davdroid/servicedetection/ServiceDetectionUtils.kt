/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.servicedetection

import at.bitfire.dav4jvm.Property
import at.bitfire.dav4jvm.property.caldav.CalDAV
import at.bitfire.dav4jvm.property.carddav.CardDAV
import at.bitfire.dav4jvm.property.push.WebDAVPush
import at.bitfire.dav4jvm.property.webdav.WebDAV
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.db.ServiceType

object ServiceDetectionUtils {

    /**
     * WebDAV properties to ask for in a PROPFIND request on a collection.
     */
    fun collectionQueryProperties(@ServiceType serviceType: String): Array<Property.Name> =
        arrayOf(                        // generic WebDAV properties
            WebDAV.CurrentUserPrivilegeSet,
            WebDAV.DisplayName,
            WebDAV.Owner,
            WebDAV.ResourceType,
            WebDAVPush.Transports,      // WebDAV-Push
            WebDAVPush.Topic
        ) + when (serviceType) {        // service-specific CalDAV/CardDAV properties
            Service.TYPE_CARDDAV -> arrayOf(
                CardDAV.AddressbookDescription
            )

            Service.TYPE_CALDAV -> arrayOf(
                CalDAV.CalendarColor,
                CalDAV.CalendarDescription,
                CalDAV.CalendarTimezone,
                CalDAV.CalendarTimezoneId,
                CalDAV.SupportedCalendarComponentSet,
                CalDAV.Source
            )

            else -> throw IllegalArgumentException()
        }

    /**
     * Finds out whether given collection is usable for synchronization, by checking that either
     *
     *  - CalDAV/CardDAV: service and collection type match, or
     *  - WebCal: subscription source URL is not empty.
     */
    fun isUsableCollection(service: Service, collection: Collection) =
        (service.type == Service.TYPE_CARDDAV && collection.type == Collection.TYPE_ADDRESSBOOK) ||
                (service.type == Service.TYPE_CALDAV && arrayOf(Collection.TYPE_CALENDAR, Collection.TYPE_WEBCAL).contains(collection.type)) ||
                (collection.type == Collection.TYPE_WEBCAL && collection.source != null)

}