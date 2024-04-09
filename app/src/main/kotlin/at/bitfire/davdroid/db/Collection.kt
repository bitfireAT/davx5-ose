/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import at.bitfire.dav4jvm.Response
import at.bitfire.dav4jvm.UrlUtils
import at.bitfire.dav4jvm.property.caldav.CalendarColor
import at.bitfire.dav4jvm.property.caldav.CalendarDescription
import at.bitfire.dav4jvm.property.caldav.CalendarTimezone
import at.bitfire.dav4jvm.property.caldav.Source
import at.bitfire.dav4jvm.property.caldav.SupportedCalendarComponentSet
import at.bitfire.dav4jvm.property.carddav.AddressbookDescription
import at.bitfire.dav4jvm.property.push.PushTransports
import at.bitfire.dav4jvm.property.push.Topic
import at.bitfire.dav4jvm.property.webdav.CurrentUserPrivilegeSet
import at.bitfire.dav4jvm.property.webdav.DisplayName
import at.bitfire.dav4jvm.property.webdav.ResourceType
import at.bitfire.davdroid.util.DavUtils
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.apache.commons.lang3.StringUtils

@Entity(tableName = "collection",
    foreignKeys = [
        ForeignKey(entity = Service::class, parentColumns = arrayOf("id"), childColumns = arrayOf("serviceId"), onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = HomeSet::class, parentColumns = arrayOf("id"), childColumns = arrayOf("homeSetId"), onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = Principal::class, parentColumns = arrayOf("id"), childColumns = arrayOf("ownerId"), onDelete = ForeignKey.SET_NULL)
    ],
    indices = [
        Index("serviceId","type"),
        Index("homeSetId","type"),
        Index("url")
    ]
)
data class Collection(
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0,

    /**
     * Service, which this collection belongs to. Services are unique, so a [Collection] is uniquely
     * identifiable via its [serviceId] and [url].
     */
    var serviceId: Long = 0,

    /**
     * A home set this collection belongs to. Multiple homesets are not supported.
     * If *null* the collection is considered homeless.
     */
    var homeSetId: Long? = null,

    /**
     * Principal who is owner of this collection.
     */
    var ownerId: Long? = null,

    /**
     * Type of service. CalDAV or CardDAV
     */
    var type: String,

    /**
     * Address where this collection lives - with trailing slash
     */
    var url: HttpUrl,

    var privWriteContent: Boolean = true,
    var privUnbind: Boolean = true,
    var forceReadOnly: Boolean = false,

    var displayName: String? = null,
    var description: String? = null,

    // CalDAV only
    var color: Int? = null,

    /** timezone definition (full VTIMEZONE) - not a TZID! **/
    var timezone: String? = null,

    /** whether the collection supports VEVENT; in case of calendars: null means true */
    var supportsVEVENT: Boolean? = null,

    /** whether the collection supports VTODO; in case of calendars: null means true */
    var supportsVTODO: Boolean? = null,

    /** whether the collection supports VJOURNAL; in case of calendars: null means true */
    var supportsVJOURNAL: Boolean? = null,

    /** Webcal subscription source URL */
    var source: HttpUrl? = null,

    /** whether this collection has been selected for synchronization */
    var sync: Boolean = false,

    /** WebDAV-Push topic */
    var pushTopic: String? = null,

    /** WebDAV-Push: whether this collection supports the Web Push Transport */
    @ColumnInfo(defaultValue = "0")
    var supportsWebPush: Boolean = false,

    /** WebDAV-Push subscription URL */
    var pushSubscription: String? = null,

    /** when the [pushSubscription] was created/updated (used to determine whether we need to re-subscribe) */
    var pushSubscriptionCreated: Long? = null

) {

    companion object {

        const val TYPE_ADDRESSBOOK = "ADDRESS_BOOK"
        const val TYPE_CALENDAR = "CALENDAR"
        const val TYPE_WEBCAL = "WEBCAL"

        /**
         * Generates a collection entity from a WebDAV response.
         * @param dav WebDAV response
         * @return null if the response doesn't represent a collection
         */
        fun fromDavResponse(dav: Response): Collection? {
            val url = UrlUtils.withTrailingSlash(dav.href)
            val type: String = dav[ResourceType::class.java]?.let { resourceType ->
                when {
                    resourceType.types.contains(ResourceType.ADDRESSBOOK) -> TYPE_ADDRESSBOOK
                    resourceType.types.contains(ResourceType.CALENDAR)    -> TYPE_CALENDAR
                    resourceType.types.contains(ResourceType.SUBSCRIBED)  -> TYPE_WEBCAL
                    else -> null
                }
            } ?: return null

            var privWriteContent = true
            var privUnbind = true
            dav[CurrentUserPrivilegeSet::class.java]?.let { privilegeSet ->
                privWriteContent = privilegeSet.mayWriteContent
                privUnbind = privilegeSet.mayUnbind
            }

            val displayName = StringUtils.trimToNull(dav[DisplayName::class.java]?.displayName)

            var description: String? = null
            var color: Int? = null
            var timezone: String? = null
            var supportsVEVENT: Boolean? = null
            var supportsVTODO: Boolean? = null
            var supportsVJOURNAL: Boolean? = null
            var source: HttpUrl? = null
            when (type) {
                TYPE_ADDRESSBOOK -> {
                    dav[AddressbookDescription::class.java]?.let { description = it.description }
                }
                TYPE_CALENDAR, TYPE_WEBCAL -> {
                    dav[CalendarDescription::class.java]?.let { description = it.description }
                    dav[CalendarColor::class.java]?.let { color = it.color }
                    dav[CalendarTimezone::class.java]?.let { timezone = it.vTimeZone }

                    if (type == TYPE_CALENDAR) {
                        supportsVEVENT = true
                        supportsVTODO = true
                        supportsVJOURNAL = true
                        dav[SupportedCalendarComponentSet::class.java]?.let {
                            supportsVEVENT = it.supportsEvents
                            supportsVTODO = it.supportsTasks
                            supportsVJOURNAL = it.supportsJournal
                        }
                    } else { // Type.WEBCAL
                        dav[Source::class.java]?.let {
                            source = it.hrefs.firstOrNull()?.let { rawHref ->
                                val href = rawHref
                                        .replace("^webcal://".toRegex(), "http://")
                                        .replace("^webcals://".toRegex(), "https://")
                                href.toHttpUrlOrNull()
                            }
                        }
                        supportsVEVENT = true
                    }
                }
            }

            // WebDAV-Push
            var supportsWebPush = false
            dav[PushTransports::class.java]?.let { pushTransports ->
                supportsWebPush = pushTransports.hasWebPush()
            }
            val pushTopic = dav[Topic::class.java]?.topic

            return Collection(
                type = type,
                url = url,
                privWriteContent = privWriteContent,
                privUnbind = privUnbind,
                displayName = displayName,
                description = description,
                color = color,
                timezone = timezone,
                supportsVEVENT = supportsVEVENT,
                supportsVTODO = supportsVTODO,
                supportsVJOURNAL = supportsVJOURNAL,
                source = source,
                supportsWebPush = supportsWebPush,
                pushTopic = pushTopic
            )
        }

    }

    // calculated properties
    fun title() = displayName ?: DavUtils.lastSegmentOfUrl(url)
    fun readOnly() = forceReadOnly || !privWriteContent

}