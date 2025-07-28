/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.servicedetection

import at.bitfire.dav4jvm.Property
import at.bitfire.dav4jvm.property.caldav.CalendarColor
import at.bitfire.dav4jvm.property.caldav.CalendarDescription
import at.bitfire.dav4jvm.property.caldav.CalendarTimezone
import at.bitfire.dav4jvm.property.caldav.CalendarTimezoneId
import at.bitfire.dav4jvm.property.caldav.Source
import at.bitfire.dav4jvm.property.caldav.SupportedCalendarComponentSet
import at.bitfire.dav4jvm.property.carddav.AddressbookDescription
import at.bitfire.dav4jvm.property.push.PushTransports
import at.bitfire.dav4jvm.property.push.Topic
import at.bitfire.dav4jvm.property.webdav.CurrentUserPrivilegeSet
import at.bitfire.dav4jvm.property.webdav.DisplayName
import at.bitfire.dav4jvm.property.webdav.Owner
import at.bitfire.dav4jvm.property.webdav.ResourceType
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.db.ServiceType

object ServiceDetectionUtils {

    /**
     * WebDAV properties to ask for in a PROPFIND request on a collection.
     */
    fun collectionQueryProperties(@ServiceType serviceType: String): Array<Property.Name> =
        arrayOf(                        // generic WebDAV properties
            CurrentUserPrivilegeSet.NAME,
            DisplayName.NAME,
            Owner.NAME,
            ResourceType.NAME,
            PushTransports.NAME,        // WebDAV-Push
            Topic.NAME
        ) + when (serviceType) {       // service-specific CalDAV/CardDAV properties
            Service.TYPE_CARDDAV -> arrayOf(
                AddressbookDescription.NAME
            )

            Service.TYPE_CALDAV -> arrayOf(
                CalendarColor.NAME,
                CalendarDescription.NAME,
                CalendarTimezone.NAME,
                CalendarTimezoneId.NAME,
                SupportedCalendarComponentSet.NAME,
                Source.NAME
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