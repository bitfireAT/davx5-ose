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
import at.bitfire.dav4jvm.property.caldav.CalendarTimezoneId
import at.bitfire.dav4jvm.property.caldav.Source
import at.bitfire.dav4jvm.property.caldav.SupportedCalendarComponentSet
import at.bitfire.dav4jvm.property.carddav.AddressbookDescription
import at.bitfire.dav4jvm.property.push.PushTransports
import at.bitfire.dav4jvm.property.push.Topic
import at.bitfire.dav4jvm.property.webdav.CurrentUserPrivilegeSet
import at.bitfire.dav4jvm.property.webdav.DisplayName
import at.bitfire.dav4jvm.property.webdav.ResourceType
import at.bitfire.davdroid.util.DavUtils.lastSegment
import at.bitfire.davdroid.util.trimToNull
import at.bitfire.ical4android.util.DateUtils
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@Entity(tableName = "collection",
    foreignKeys = [
        ForeignKey(entity = Service::class, parentColumns = arrayOf("id"), childColumns = arrayOf("serviceId"), onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = HomeSet::class, parentColumns = arrayOf("id"), childColumns = arrayOf("homeSetId"), onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = Principal::class, parentColumns = arrayOf("id"), childColumns = arrayOf("ownerId"), onDelete = ForeignKey.SET_NULL)
    ],
    indices = [
        Index("serviceId","type"),
        Index("homeSetId","type"),
        Index("ownerId","type"),
        Index("pushTopic","type"),
        Index("url")
    ]
)
data class Collection(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

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
    val type: String,

    /**
     * Address where this collection lives - with trailing slash
     */
    val url: HttpUrl,

    /**
     * Whether we have the permission to change contents of the collection on the server.
     * Even if this flag is set, there may still be other reasons why a collection is effectively read-only.
     */
    val privWriteContent: Boolean = true,
    /**
     * Whether we have the permission to delete the collection on the server
     */
    val privUnbind: Boolean = true,
    /**
     * Whether the user has manually set the "force read-only" flag.
     * Even if this flag is not set, there may still be other reasons why a collection is effectively read-only.
     */
    var forceReadOnly: Boolean = false,

    /**
     * Human-readable name of the collection
     */
    val displayName: String? = null,
    /**
     * Human-readable description of the collection
     */
    val description: String? = null,

    // CalDAV only
    var color: Int? = null,

    /** default timezone (only timezone ID, like `Europe/Vienna`) */
    val timezoneId: String? = null,

    /** whether the collection supports VEVENT; in case of calendars: null means true */
    val supportsVEVENT: Boolean? = null,

    /** whether the collection supports VTODO; in case of calendars: null means true */
    val supportsVTODO: Boolean? = null,

    /** whether the collection supports VJOURNAL; in case of calendars: null means true */
    val supportsVJOURNAL: Boolean? = null,

    /** Webcal subscription source URL */
    val source: HttpUrl? = null,

    /** whether this collection has been selected for synchronization */
    var sync: Boolean = false,

    /** WebDAV-Push topic */
    val pushTopic: String? = null,

    /** WebDAV-Push: whether this collection supports the Web Push Transport */
    @ColumnInfo(defaultValue = "0")
    val supportsWebPush: Boolean = false,

    /** WebDAV-Push subscription URL */
    val pushSubscription: String? = null,

    /** when the [pushSubscription] expires (timestamp, used to determine whether we need to re-subscribe) */
    val pushSubscriptionExpires: Long? = null,

    /** when the [pushSubscription] was created/updated (timestamp) */
    val pushSubscriptionCreated: Long? = null

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

            val displayName = dav[DisplayName::class.java]?.displayName.trimToNull()

            var description: String? = null
            var color: Int? = null
            var timezoneId: String? = null
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
                    dav[CalendarTimezoneId::class.java]?.let { timezoneId = it.identifier }
                    if (timezoneId == null)
                        dav[CalendarTimezone::class.java]?.vTimeZone?.let {
                            timezoneId = DateUtils.parseVTimeZone(it)?.timeZoneId?.value
                        }

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
                timezoneId = timezoneId,
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
    fun title() = displayName ?: url.lastSegment
    fun readOnly() = forceReadOnly || !privWriteContent

}