/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.servicedetection

import at.bitfire.dav4jvm.Property
import at.bitfire.dav4jvm.Response
import at.bitfire.dav4jvm.equalsForWebDAV
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
import okhttp3.HttpUrl

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

    /**
     * Evaluates whether a response is personal or not.
     * It takes the [Owner] property from the response, and compares its value against [principal].
     *
     * If either one of those is not set (`null`), this function returns `null`.
     * @param principal The current principal url to compare the owner against.
     * @param davResponse The response to process.
     * @return
     * - `null` if either [principal] or [davResponse]'s [Owner] are null, or if the owner url cannot be resolved on [principal].
     * - `true` if the owner matches a principal.
     * - `false` if the owner doesn't match a principal or the owner and/or principal is not set / unknown.
     */
    fun isPersonal(principal: HttpUrl?, davResponse: Response): Boolean? {
        // Owner must be set in order to check if the home set is personal
        val ownerHref = davResponse[Owner::class.java]?.href ?: return null
        principal ?: return null

        // Try to resolve the owner href
        val ownerResolvedHref = principal.resolve(ownerHref)
        if (ownerResolvedHref == null) return null

        // If both fields are set, compare them
        return ownerResolvedHref.equalsForWebDAV(principal)
    }

}