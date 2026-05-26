/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

/*
 * Copyright (c) Techbee e.U.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.techbee.jtx

import android.accounts.Account
import android.net.Uri
import android.provider.BaseColumns
import androidx.core.net.toUri
import at.techbee.jtx.JtxContract.JtxAlarm.ACTION
import at.techbee.jtx.JtxContract.JtxAlarm.TRIGGER_RELATIVE_TO
import at.techbee.jtx.JtxContract.JtxAlarm.TRIGGER_TIME
import at.techbee.jtx.JtxContract.JtxICalObject.COMPLETED
import at.techbee.jtx.JtxContract.JtxICalObject.COMPLETED_TIMEZONE
import at.techbee.jtx.JtxContract.JtxICalObject.DTEND
import at.techbee.jtx.JtxContract.JtxICalObject.DTEND_TIMEZONE
import at.techbee.jtx.JtxContract.JtxICalObject.DTSTART
import at.techbee.jtx.JtxContract.JtxICalObject.DTSTART_TIMEZONE
import at.techbee.jtx.JtxContract.JtxICalObject.DUE
import at.techbee.jtx.JtxContract.JtxICalObject.DUE_TIMEZONE
import at.techbee.jtx.JtxContract.JtxICalObject.GEO_LAT
import at.techbee.jtx.JtxContract.JtxICalObject.GEO_LONG
import at.techbee.jtx.JtxContract.JtxICalObject.TZ_ALLDAY
import net.fortuna.ical4j.model.ParameterList
import net.fortuna.ical4j.model.PropertyList
import net.fortuna.ical4j.model.parameter.XParameter
import net.fortuna.ical4j.model.property.XProperty
import org.json.JSONObject
import java.util.logging.Level
import java.util.logging.Logger


object JtxContract {

    private val logger
        get() = Logger.getLogger(javaClass.name)

    /**
     * URI parameter to signal that the caller is a sync adapter.
     */
    const val CALLER_IS_SYNCADAPTER = "caller_is_syncadapter"

    /**
     * URI parameter to submit the account name of the account we operate on.
     */
    const val ACCOUNT_NAME = "account_name"

    /**
     * URI parameter to submit the account type of the account we operate on.
     */
    const val ACCOUNT_TYPE = "account_type"

    /** The authority under which the content provider can be accessed */
    const val AUTHORITY = "at.techbee.jtx.provider"

    /** The version of this SyncContentProviderContract */
    const val VERSION = 9

    /** Constructs an Uri for the Jtx Sync Adapter with the given Account
     * @param [account] The account that should be appended to the Base Uri
     * @return [Uri] with the appended Account
     */
    fun Uri.asSyncAdapter(account: Account): Uri =
        buildUpon()
            .appendQueryParameter(CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(ACCOUNT_NAME, account.name)
            .appendQueryParameter(ACCOUNT_TYPE, account.type)
            .build()

    /**
     * This function takes a string and tries to parse it to a list of XParameters.
     * This is the counterpart of getJsonStringFromXParameters(...)
     * @param [string] that should be parsed
     * @return The list of XParameter parsed from the string
     */
    fun getXParametersFromJson(string: String): List<XParameter> {
        val jsonObject = JSONObject(string)
        val xparamList = mutableListOf<XParameter>()
        for (i in 0 until jsonObject.length()) {
            val names = jsonObject.names() ?: break
            val xparamName = names[i]?.toString() ?: break
            val xparamValue = jsonObject.getString(xparamName).toString()
            if (xparamName.isNotBlank() && xparamValue.isNotBlank()) {
                val xparam = XParameter(xparamName, xparamValue)
                xparamList.add(xparam)
            }
        }
        return xparamList
    }

    /**
     * This function takes a string and tries to parse it to a list of XProperty.
     * This is the counterpart of getJsonStringFromXProperties(...)
     * @param [string] that should be parsed
     * @return The list of XProperty parsed from the string
     */
    fun getXPropertyListFromJson(string: String): PropertyList {
        val propertyList = PropertyList()

        if (string.isBlank())
            return propertyList

        try {
            val jsonObject = JSONObject(string)
            for (i in 0 until jsonObject.length()) {
                val names = jsonObject.names() ?: break
                val propertyName = names[i]?.toString() ?: break
                val propertyValue = jsonObject.getString(propertyName).toString()
                if (propertyName.isNotBlank() && propertyValue.isNotBlank()) {
                    val prop = XProperty(propertyName, propertyValue)
                    propertyList.add(prop)
                }
            }
        } catch (e: NullPointerException) {
            logger.log(Level.WARNING, "Error parsing x-property-list $string", e)
        }
        return propertyList
    }


    /**
     * Takes a Parameter List and returns a Json String to be saved in a DB field.
     * This is the counterpart to getXParameterFromJson(...)
     * @param [parameters] The ParameterList that should be transformed into a Json String
     * @return The generated Json object as a [String]
     */
    fun getJsonStringFromXParameters(parameters: ParameterList?): String? {
        if (parameters == null)
            return null

        val jsonObject = JSONObject()

        // Note: probably the contract should be separated from methods that do things, especially if they depend on ical4j

        parameters.all.forEach { parameter ->
            jsonObject.put(parameter.name, parameter.value)
        }
        return if (jsonObject.length() == 0)
            null
        else
            jsonObject.toString()
    }

    /**
     * Takes a Property List and returns a Json String to be saved in a DB field.
     * This is the counterpart to getXPropertyListFromJson(...)
     * @param [propertyList] The PropertyList that should be transformed into a Json String
     * @return The generated Json object as a [String]
     */
    fun getJsonStringFromXProperties(propertyList: PropertyList?): String? {
        if (propertyList == null)
            return null

        // Note: probably the contract should be separated from methods that do things, especially if they depend on ical4j

        val jsonObject = JSONObject()
        propertyList.all.forEach { property ->
            jsonObject.put(property.name, property.value)
        }
        return if (jsonObject.length() == 0)
            null
        else
            jsonObject.toString()
    }


    /**
     * Some date fields in jtx Board are saved as a list of Long values separated by commas.
     * This applies for example to the exdate for recurring events.
     * This function takes a string and tries to parse it to a list of long values (timestamps)
     * @param [string] that should be parsed
     * @return a [MutableList<Long>] with the timestamps parsed from the string
     *
     */
    fun getLongListFromString(string: String): MutableList<Long> {
        val stringList = string.split(",")
        val longList = mutableListOf<Long>()

        stringList.forEach {
            try {
                longList.add(it.toLong())
            } catch (_: NumberFormatException) {
                logger.log(Level.WARNING, "String could not be cast to Long ($it)")
                return@forEach
            }
        }
        return longList
    }


    object JtxICalObject {

        /** The name of the the content URI for IcalObjects.
         * This is a general purpose table containing general columns
         * for Journals, Notes and Todos */
        const val CONTENT_URI_PATH = "icalobject"

        /** The content uri of the ICalObject table */
        val CONTENT_URI: Uri by lazy { "content://$AUTHORITY/$CONTENT_URI_PATH".toUri() }

        /** The host under which an Intent can be called to open an ICalObject */
        const val VIEW_INTENT_HOST = "at.techbee.jtx"

        /* The Intent Uri to open an ICalObject, append the ICalObjectId as lastPathSegment to open a specific entry */
        val VIEW_INTENT_URI: Uri by lazy { "content://$VIEW_INTENT_HOST/$CONTENT_URI_PATH".toUri() }

        /* Convenience function to directly build the content URI to view a specific ICalObject in jtx Board by its ID */
        fun getViewIntentUriFor(iCalObjectId: Long): Uri = Uri.withAppendedPath(VIEW_INTENT_URI, iCalObjectId.toString())



        /** Constant to define all day values (for dtstart, due, completed timezone fields */
        const val TZ_ALLDAY = "ALLDAY"

        /** The name of the ID column.
         * This is the unique identifier of an ICalObject.
         * Type: [Long]*/
        const val ID = BaseColumns._ID

        /** The column for the module.
         * This is an internal differentiation for JOURNAL, NOTE and T0DO as provided in the enum [Module].
         * The Module does not need to be set. On import it will be derived from the component from the [Component] (T0do or Journal/Note) and if a
         * dtstart is present or not (Journal or Note). If the module was set, it might be overwritten on import. In this sense also
         * unknown values are overwritten.
         * Use e.g. Module.JOURNAL.name for a correct String value in this field.
         * Type: [String]
         */
        const val MODULE = "module"

        /***** The names of all the other columns  *****/
        /** The column for the component based on the values provided in the enum [Component].
         * Use e.g. Component.VTODO.name for a correct String value in this field.
         * If no Component is provided on Insert, an IllegalArgumentException is thrown
         * Type: [String]
         */
        const val COMPONENT = "component"

        /**
         * Purpose:  This column/property defines a short summary or subject for the calendar component.
         * See [https://tools.ietf.org/html/rfc5545#section-3.8.1.12]
         * Type: [String]
         */
        const val SUMMARY = "summary"

        /**
         * Purpose:  This column/property provides a more complete description of the calendar component than that provided by the "SUMMARY" property.
         * See [https://tools.ietf.org/html/rfc5545#section-3.8.1.5]
         * Type: [String]
         */
        const val DESCRIPTION = "description"

        /**
         * Purpose:  This column/property specifies when the calendar component begins.
         * This value is stored as UNIX timestamp (milliseconds).
         * The corresponding timezone is stored in [DTSTART_TIMEZONE].
         * See [https://tools.ietf.org/html/rfc5545#section-3.8.2.4]
         * Type: [Long]
         */
        const val DTSTART = "dtstart"

        /**
         * Purpose:  This column/property specifies the timezone of when the calendar component begins.
         * The corresponding datetime is stored in [DTSTART].
         * The value of a timezone can be:
         * 1. the id of a Java timezone to represent the given timezone.
         * 2. null to represent floating time.
         * 3. the value of [TZ_ALLDAY] to represent an all-day entry.
         * If an invalid value is passed, the Timezone is ignored and interpreted as UTC.
         * See [https://tools.ietf.org/html/rfc5545#section-3.8.2.4]
         * Type: [String]
         */
        const val DTSTART_TIMEZONE = "dtstarttimezone"

        /**
         * Purpose:  This column/property specifies when the calendar component ends.
         * This value is stored as UNIX timestamp (milliseconds).
         * The corresponding timezone is stored in [DTEND_TIMEZONE].
         * See [https://tools.ietf.org/html/rfc5545#section-3.8.2.4]
         * Type: [Long]
         */
        const val DTEND = "dtend"

        /**
         * Purpose:  This column/property specifies the timezone of when the calendar component ends.
         * The corresponding datetime is stored in [DTEND].
         * See [DTSTART_TIMEZONE] for information about the timezone handling
         * See [https://tools.ietf.org/html/rfc5545#section-3.8.2.2]
         * Type: [String]
         */
        const val DTEND_TIMEZONE = "dtendtimezone"

        /**
         * Purpose:  This property defines the overall status or confirmation for the calendar component.
         * The possible values of a status are defined in [StatusTodo] for To-Dos and in [StatusJournal] for Notes and Journals
         * If no valid Status is used, the value is taken and will be shown as-is.
         * Also null-value is allowed.
         * See [https://tools.ietf.org/html/rfc5545#section-3.8.1.11]
         * Type: [String]
         */
        const val STATUS = "status"

        /**
         * Purpose:  To specify the filename of the attachment.
         * This is an X-PROPERTY that should be addressed as "X-LABEL"
         * Type: [String]
         */
        const val EXTENDED_STATUS = "xstatus"

        /**
         * Purpose:  Defines the radius for a geofence in meters
         * This is put into an extended property in the iCalendar-file
         * Type: [String]
         */
        const val GEOFENCE_RADIUS = "geofenceRadius"

        /**
         * Purpose:  This property defines the access classification for a calendar component.
         * The possible values of a status are defined in the enum [Classification].
         * Use e.g. Classification.PUBLIC.name to put a correct String value in this field.
         * If no valid Classification is used, the value is taken and will be shown as-is.
         * Also null-value is allowed.
         * See [https://tools.ietf.org/html/rfc5545#section-3.8.1.11]
         * Type: [String]
         */
        const val CLASSIFICATION = "classification"

        /**
         * Purpose:  This property defines a Uniform Resource Locator (URL) associated with the iCalendar object.
         * See [https://tools.ietf.org/html/rfc5545#section-3.8.4.6]
         * Type: [String]
         */
        const val URL = "url"

        /**
         * Purpose:  This property is used to represent contact information or alternately a reference
         * to contact information associated with the calendar component.
         * See [https://tools.ietf.org/html/rfc5545#section-3.8.4.2]
         * Type: [String]
         */
        const val CONTACT = "contact"

        /**
         * Purpose:  This property specifies information related to the global position for the activity specified by a calendar component.
         * This property is split in the fields [GEO_LAT] for the latitude
         * and [GEO_LONG] for the longitude coordinates using the WGS84 ellipsoid.
         * See [https://tools.ietf.org/html/rfc5545#section-3.8.1.6]
         * Type: [Float]
         */
        const val GEO_LAT = "geolat"

        /**
         * Purpose:  This property specifies information related to the global position for the activity specified by a calendar component.
         * This property is split in the fields [GEO_LAT] for the latitude
         * and [GEO_LONG] for the longitude coordinates using the WGS84 ellipsoid.
         * See [https://tools.ietf.org/html/rfc5545#section-3.8.1.6]
         * Type: [Double]
         */
        const val GEO_LONG = "geolong"

        /**
         * Purpose:  This property defines the intended venue for the activity defined by a calendar component.
         * See [https://tools.ietf.org/html/rfc5545#section-3.8.1.7]
         * Type: [Double]
         */
        const val LOCATION = "location"

        /**
         * Purpose:  This property defines the alternative representation of the intended venue for the activity defined by a calendar component.
         * See [https://tools.ietf.org/html/rfc5545#section-3.8.1.7]
         * Type: [String]
         */
        const val LOCATION_ALTREP = "locationaltrep"

        /**
         * Purpose:  This property is used by an assignee or delegatee of a to-do to convey the percent completion of a to-do to the "Organizer".
         * The value must either be null or between 0 and 100. The value 0 is interpreted as null.
         * If a value outside of the boundaries (0-100) is passed, the value is reset to null.
         * See [https://tools.ietf.org/html/rfc5545#section-3.8.1.8]
         * Type: [Int]
         */
        const val PERCENT = "percent"

        /**
         * Purpose:  This property defines the relative priority for a calendar component.
         * See [https://tools.ietf.org/html/rfc5545#section-3.8.1.9]. The priority can be null or between 0 and 9.
         * Values outside of these boundaries are accepted but internally not mapped to a string resource.
         * Type: [Int]
         */
        const val PRIORITY = "priority"

        /**
         * Purpose:  This property defines the date and time that a to-do is expected to be completed.
         * This value is stored as UNIX timestamp (milliseconds).
         * The corresponding timezone is stored in [DUE_TIMEZONE].
         * See [https://tools.ietf.org/html/rfc5545#section-3.8.2.3]
         * Type: [Long]
         */
        const val DUE = "due"

        /**
         * Purpose:  This column/property specifies the timezone of when a to-do is expected to be completed.
         * The corresponding datetime is stored in [DUE].
         * See [DTSTART_TIMEZONE] for information about the timezone handling
         * See [https://tools.ietf.org/html/rfc5545#section-3.8.2.2]
         * Type: [String]
         */
        const val DUE_TIMEZONE = "duetimezone"

        /**
         * Purpose:  This property defines the date and time that a to-do was actually completed.
         * This value is stored as UNIX timestamp (milliseconds).
         * The corresponding timezone is stored in [COMPLETED_TIMEZONE].
         * See [https://tools.ietf.org/html/rfc5545#section-3.8.2.1]
         * Type: [Long]
         */
        const val COMPLETED = "completed"

        /**
         * Purpose:  This column/property specifies the timezone of when a to-do was actually completed.
         * The corresponding datetime is stored in [COMPLETED].
         * See [DTSTART_TIMEZONE] for information about the timezone handling
         * See [https://tools.ietf.org/html/rfc5545#section-3.8.2.1]
         * Type: [String]
         */
        const val COMPLETED_TIMEZONE = "completedtimezone"

        /**
         * Purpose:  This property specifies a positive duration of time.
         * See [DTSTART_TIMEZONE] for information about the timezone handling
         * The string representation follows the notation as given in RFC-5545
         * for the value type duration: [https://datatracker.ietf.org/doc/html/rfc5545#section-3.3.6]
         * Exampe: "P15DT5H0M20S". This field is currently not in use. If present, the user would
         * see a notification that a duration cannot be processed and will be overwritten if the entry is changed.
         * See [https://tools.ietf.org/html/rfc5545#section-3.8.2.5]
         * Type: [String]
         */
        const val DURATION = "duration"


        /**
         * Purpose:  This property defines a rule or repeating pattern for recurring events,
         * to-dos, journal entries, or time zone definitions.
         * The representation of the RRULE follows the value type RECUR of RFC-5545 as given in
         * [https://datatracker.ietf.org/doc/html/rfc5545#section-3.3.10]
         * For example: "FREQ=DAILY;COUNT=10"
         * See also [https://datatracker.ietf.org/doc/html/rfc5545#section-3.8.5.3].
         * Type: [String]
         */
        const val RRULE = "rrule"

        /**
         * Purpose:  This property defines the list of DATE-TIME values for
         * recurring events, to-dos, journal entries, or time zone definitions.
         * Type: [String], contains a list of comma-separated date values as
         * UNIX timestamps (milliseconds) e.g. "1636751475000,1636837875000".
         * Invalid values that cannot be transformed to [Long] are ignored.
         */
        const val RDATE = "rdate"

        /**
         * Purpose:  This property defines the list of DATE-TIME exceptions for
         * recurring events, to-dos, journal entries, or time zone definitions.
         * Type: [String], contains a list of comma-separated date values as
         * UNIX timestamps (milliseconds) e.g. "1636751475000,1636837875000".
         * Invalid values that cannot be transformed to [Long] are ignored.
         */
        const val EXDATE = "exdate"

        /**
         * Purpose:  This property is used in conjunction with the "UID" and
         * "SEQUENCE" properties to identify a specific instance of a
         * recurring "VEVENT", "VTODO", or "VJOURNAL" calendar component.
         * The property value is the original value of the "DTSTART" property
         * of the recurrence instance, ie. a DATE or DATETIME value e.g. "20211101T160000".
         * Must be null for non-recurring and original events from which recurring events are derived.
         * Type: [String]
         */
        const val RECURID = "recurid"

        /**
         * Purpose:  This property is used in conjunction with the "UID" and
         * "SEQUENCE" properties to identify a specific instance of a
         * recurring "VEVENT", "VTODO", or "VJOURNAL" calendar component.
         * The property value is the original value of the "DTSTART" property
         * of the recurrence instance, ie. a DATE or DATETIME value e.g. "20211101T160000".
         * Must be null for non-recurring and original events from which recurring events are derived.
         * Type: [String?]
         */
        const val RECURID_TIMEZONE = "recuridtimezone"

        /**
         * Stores the reference to the original event from which the recurring event was derived.
         * This value is NULL for the orignal event or if the event is not recurring
         * Type: [Long], References [JtxICalObject.ID]
         */
        const val RECUR_ORIGINALICALOBJECTID = "original_id"

        /**
         * Purpose:  This property defines the persistent, globally unique identifier for the calendar component.
         * See [https://tools.ietf.org/html/rfc5545#section-3.8.4.7]
         * Type: [String]
         */
        const val UID = "uid"

        /**
         * Purpose:  This property specifies the date and time that the calendar information
         * was created by the calendar user agent in the calendar store and is stored as UNIX timestamp (milliseconds).
         * See [https://tools.ietf.org/html/rfc5545#section-3.8.7.1]
         * Type: [Long]
         */
        const val CREATED = "created"

        /**
         * Purpose:  In the case of an iCalendar object that specifies a
         * "METHOD" property, this property specifies the date and time that
         * the instance of the iCalendar object was created.  In the case of
         * an iCalendar object that doesn't specify a "METHOD" property, this
         * property specifies the date and time that the information
         * associated with the calendar component was last revised in the
         * calendar store. It is saved as UNIX timestamp (milliseconds).
         * See [https://tools.ietf.org/html/rfc5545#section-3.8.7.2]
         * Type: [Long]
         */
        const val DTSTAMP = "dtstamp"

        /**
         * Purpose:  This property specifies the date and time that the information associated
         * with the calendar component was last revised in the calendar store.
         * It is saved as UNIX timestamp (milliseconds).
         * See [https://tools.ietf.org/html/rfc5545#section-3.8.7.3]
         * Type: [Long]
         */
        const val LAST_MODIFIED = "lastmodified"

        /**
         * Purpose:  This property defines the revision sequence number of the calendar component within a sequence of revisions.
         * If no sequence is present, it is automatically initialized with 0
         * See [https://tools.ietf.org/html/rfc5545#section-3.8.7.4]
         * Type: [Int]
         */
        const val SEQUENCE = "sequence"

        /**
         * Purpose:  This property specifies a color used for displaying the calendar, event, t0do, or journal data.
         * See [https://tools.ietf.org/html/rfc7986#section-5.9].
         * The color is represented as Int-value as described in [https://developer.android.com/reference/android/graphics/Color#color-ints]
         * Type: [Int]
         */
        const val COLOR = "color"

        /**
         * Purpose:  This column is the foreign key to the [JtxCollection].
         * Type: [Long], references [JtxCollection.ID]
         */
        const val ICALOBJECT_COLLECTIONID = "collectionId"

        /**
         * Purpose:  This column defines if the local ICalObject was changed and that it is supposed to be synchronised.
         * Type: [Boolean]
         */
        const val DIRTY = "dirty"

        /**
         * Purpose:  This column defines if a collection that is supposed to be synchonized was locally marked as deleted.
         * Type: [Boolean]
         */
        const val DELETED = "deleted"

        /**
         * Purpose:  The remote file name of the synchronized entry (*.ics), only relevant for synchronized
         * entries through the sync-adapter
         * Type: [String]
         */
        const val FILENAME = "filename"

        /**
         * Purpose:  eTag for SyncAdapter, only relevant for synched entries through sync-adapter
         * Type: [String]
         */
        const val ETAG = "etag"

        /**
         * Purpose:  scheduleTag for SyncAdapter, only relevant for synched entries through sync-adapter
         * Type: [String]
         */
        const val SCHEDULETAG = "scheduletag"

        /**
         * Purpose:  flags for SyncAdapter, only relevant for synched entries through sync-adapter
         * Type: [Int]
         */
        const val FLAGS = "flags"

        /**
         * Purpose:  To specify other properties for the ICalObject.
         * This is especially used for additional properties that cannot be put into another field, especially relevant for the synchronization
         * The Properties are stored as JSON. There are two helper functions provided:
         * getJsonStringFromXProperties(PropertyList<*> that returns a Json String from the property list
         * to be stored in this other field. The counterpart to this function is
         * getXPropertyListFromJson(String) that returns a PropertyList from a Json that was created with getJsonStringFromXProperties()
         * Type: [String]
         */
        const val OTHER = "other"


        /**
         * This enum class defines the possible values for the attribute status of an [JtxICalObject] for Journals/Notes
         *  Use its name when the string representation is required, e.g. StatusJournal.DRAFT.name.
         */
        enum class StatusJournal {
            DRAFT, FINAL, CANCELLED
        }

        /**
         * This enum class defines the possible values for the attribute status of an [JtxICalObject] for Todos
         * Use its name when the string representation is required, e.g. StatusTodo.`NEEDS-ACTION`.name.
         */
        enum class StatusTodo {
            `NEEDS-ACTION`, COMPLETED, `IN-PROCESS`, CANCELLED
        }

        /**
         * This enum class defines the possible values for the attribute classification of an [JtxICalObject]
         * Use its name when the string representation is required, e.g. Classification.PUBLIC.name.
         */
        enum class Classification {
            PUBLIC, PRIVATE, CONFIDENTIAL
        }

        /**
         * This enum class defines the possible values for the attribute component of an [JtxICalObject]
         * Use its name when the string representation is required, e.g. Component.VJOURNAL.name.
         */
        enum class Component {
            VJOURNAL, VTODO
        }

        /**
         * This enum class defines the possible values for the attribute module of an [JtxICalObject]
         * Use its name when the string representation is required, e.g. Module.JOURNAL.name.
         */
        enum class Module {
            JOURNAL, NOTE, TODO
        }
    }


    object JtxAttendee {

        /** The name of the the table for Attendees that are linked to an ICalObject.
         *  [https://tools.ietf.org/html/rfc5545#section-3.8.4.1] */
        private const val CONTENT_URI_PATH = "attendee"

        /** The content uri of the Attendee table */
        val CONTENT_URI: Uri by lazy { "content://$AUTHORITY/$CONTENT_URI_PATH".toUri() }


        /** The name of the ID column.
         * This is the unique identifier of an Attendee
         * Type: [Long] */
        const val ID = BaseColumns._ID

        /** The name of the Foreign Key Column for IcalObjects.
         * Type: [Long], references [JtxICalObject.ID]
         */
        const val ICALOBJECT_ID = "icalObjectId"


        /***** The names of all the other columns  *****/
        /**
         * Purpose:  This value type is used to identify properties that contain a calendar user address (in this case of the attendee).
         * This is usually an e-mail address as defined in [https://datatracker.ietf.org/doc/html/rfc5545#section-3.3.3].
         * The value should be passed as String containing the URI with "mailto:", for example: "mailto:jane_doe@example.com"
         * See also [https://tools.ietf.org/html/rfc5545#section-3.8.4.1].
         * Type: [String]
         */
        const val CALADDRESS = "caladdress"

        /**
         * Purpose:  To identify the type of calendar user specified by the property in this case for the attendee.
         * The possible values are defined in the enum [Cutype].
         * Use e.g. Cutype.INDIVIDUAL.name to put a correct String value in this field.
         * If no value is passed for the Cutype, the Cutype will be interpreted as INDIVIDUAL as according to the RFC.
         * Other values are accepted and treated as UNKNOWN.
         * see [https://tools.ietf.org/html/rfc5545#section-3.8.4.1] and [https://tools.ietf.org/html/rfc5545#section-3.2.3]
         * Type: [String]
         */
        const val CUTYPE = "cutype"

        /**
         * Purpose:  To specify the group or list membership of the calendar user specified by the property in this case for the attendee.
         * see [https://tools.ietf.org/html/rfc5545#section-3.8.4.1] and [https://tools.ietf.org/html/rfc5545#section-3.2.11]
         * Type: [String]
         */
        const val MEMBER = "member"

        /**
         * Purpose:  To specify the participation role for the calendar user specified by the property in this case for the attendee.
         * The possible values are defined in the enum [Role]
         * Use e.g. Role.CHAIR.name to put a correct String value in this field.
         * If no value (null) is passed for the Role, it will be interpreted as REQ-PARTICIPANT as according to the RFC.
         * Other values are accepted and treated as REQ-PARTICIPANTs.
         * see [https://tools.ietf.org/html/rfc5545#section-3.8.4.1] and [https://tools.ietf.org/html/rfc5545#section-3.2.16]
         * Type: [String]
         */
        const val ROLE = "role"

        /**
         * Purpose:  To specify the participation status for the calendar user specified by the property in this case for the attendee.
         * The possible values are defined in the enum [PartstatJournal] and [PartstatTodo]
         * Use e.g. PartstatJournal.ACCEPTED.name to put a correct String value in this field.
         * If no value (null) is passed for the Partstat, it will be interpreted as NEEDS-ACTION as according to the RFC.
         * Other values are accepted and treated as NEEDS-ACTION.
         * see [https://tools.ietf.org/html/rfc5545#section-3.8.4.1] and [https://tools.ietf.org/html/rfc5545#section-3.2.12]
         * Type: [String]
         */
        const val PARTSTAT = "partstat"

        /**
         * Purpose:  To specify whether there is an expectation of a favor of a reply from the calendar user specified by the property value
         * in this case for the attendee.
         * see [https://tools.ietf.org/html/rfc5545#section-3.8.4.1] and [https://tools.ietf.org/html/rfc5545#section-3.2.17]
         * Type: [Boolean]
         */
        const val RSVP = "rsvp"

        /**
         * Purpose:  To specify the calendar users to whom the calendar user specified by the property
         * has delegated participation in this case for the attendee.
         * see [https://tools.ietf.org/html/rfc5545#section-3.8.4.1] and [https://tools.ietf.org/html/rfc5545#section-3.2.5]
         * Type: [String]
         */
        const val DELEGATEDTO = "delegatedto"

        /**
         * Purpose:  To specify the calendar users that have delegated their participation to the calendar user specified by the property
         * in this case for the attendee.
         * This is usually an e-mail address as defined in [https://datatracker.ietf.org/doc/html/rfc5545#section-3.3.3].
         * The value should be passed as String containing the URI with "mailto:", for example: "mailto:jane_doe@example.com"
         * See also [https://tools.ietf.org/html/rfc5545#section-3.8.4.1] and [https://tools.ietf.org/html/rfc5545#section-3.2.4]
         * Type: [String]
         */
        const val DELEGATEDFROM = "delegatedfrom"

        /**
         * Purpose:  To specify the calendar user that is acting on behalf of the calendar user specified by the property in this case for the attendee.
         * This is usually an e-mail address as defined in [https://datatracker.ietf.org/doc/html/rfc5545#section-3.3.3].
         * The value should be passed as String containing the URI with "mailto:", for example: "mailto:jane_doe@example.com"
         * See also [https://tools.ietf.org/html/rfc5545#section-3.8.4.1] and [https://datatracker.ietf.org/doc/html/rfc5545#section-3.2.18]
         * Type: [String]
         */
        const val SENTBY = "sentby"

        /**
         * Purpose:  To specify the common name to be associated with the calendar user specified by the property in this case for the attendee.
         * see [https://tools.ietf.org/html/rfc5545#section-3.8.4.1] and [https://datatracker.ietf.org/doc/html/rfc5545#section-3.2.2]
         * Type: [String]
         */
        const val CN = "cn"

        /**
         * Purpose:  To specify reference to a directory entry associated with the calendar user specified by the property in this case for the attendee.
         * Expected is an URI as defined in [https://datatracker.ietf.org/doc/html/rfc5545#section-3.2.6].
         * The value should be passed as String, e.g. "ldap://example.com:6666/o=ABC%20Industries,c=US???(cn=Jim%20Dolittle)"
         * Type: [String]
         */
        const val DIR = "dir"

        /**
         * Purpose:  To specify the language for text values in a property or property parameter, in this case of the attendee.
         * see [https://tools.ietf.org/html/rfc5545#section-3.8.4.1] and [https://tools.ietf.org/html/rfc5545#section-3.2.10]
         * Language-Tag as defined in RFC5646, e.g. "en:Germany"
         * Type: [String]
         */
        const val LANGUAGE = "language"

        /**
         * Purpose:  To specify other parameters for the attendee.
         * see [https://tools.ietf.org/html/rfc5545#section-3.8.4.1]
         * The Parameters are stored as JSON. There are two helper functions provided:
         * getJsonStringFromXParameters(ParameterList?) that returns a Json String from the parameter list
         * to be stored in this other field. The counterpart to this function is
         * getXParameterFromJson(String) that returns a list of XParameters from a Json that was created with getJsonStringFromXParameters(...)
         * Type: [String]
         */
        const val OTHER = "other"


        /**
         * This enum class defines the possible values for the attribute Cutype of an [JtxAttendee]
         * Use its name when the string representation is required, e.g. Cutype.INDIVIDUAL.name.
         */
        enum class Cutype {
            INDIVIDUAL, GROUP, RESOURCE, ROOM, UNKNOWN
        }

        /**
         * This enum class defines the possible values for the attribute Role of an [JtxAttendee]
         * Use its name when the string representation is required, e.g. Role.`REQ-PARTICIPANT`.name.
         */
        enum class Role {
            CHAIR, `REQ-PARTICIPANT`, `OPT-PARTICIPANT`, `NON-PARTICIPANT`
        }

        /** This enum class defines the possible values for the attribute [JtxAttendee] for the Component VJOURNAL  */
        enum class PartstatJournal {
            `NEEDS-ACTION`, ACCEPTED, DECLINED
        }

        /** This enum class defines the possible values for the attribute [JtxAttendee] for the Component VTODO  */
        enum class PartstatTodo {
            `NEEDS-ACTION`, ACCEPTED, DECLINED, TENTATIVE, DELEGATED, COMPLETED, `IN-PROCESS`
        }
    }

    object JtxCategory {

        /** The name of the the table for Categories that are linked to an ICalObject.
         * [https://tools.ietf.org/html/rfc5545#section-3.8.1.2]*/
        private const val CONTENT_URI_PATH = "category"

        /** The content uri of the Category table */
        val CONTENT_URI: Uri by lazy { "content://$AUTHORITY/$CONTENT_URI_PATH".toUri() }


        /** The name of the ID column for categories.
         * This is the unique identifier of a Category
         * Type: [Long]*/
        const val ID = BaseColumns._ID

        /** The name of the Foreign Key Column for IcalObjects.
         * Type: [Long], references [JtxICalObject.ID]
         */
        const val ICALOBJECT_ID = "icalObjectId"


        /***** The names of all the other columns  *****/
        /**
         * Purpose:  This property defines the name of the category for a calendar component.
         * see [https://tools.ietf.org/html/rfc5545#section-3.8.1.2]
         * Type: [String]
         */
        const val TEXT = "text"

        /**
         * Purpose:  To specify the language for text values in a property or property parameter, in this case of the category.
         * see [https://tools.ietf.org/html/rfc5545#section-3.8.1.2] and [https://tools.ietf.org/html/rfc5545#section-3.2.10]
         * Language-Tag as defined in RFC5646, e.g. "en:Germany"
         * Type: [String]
         */
        const val LANGUAGE = "language"

        /**
         * Purpose:  To specify other properties for the category.
         * see [https://tools.ietf.org/html/rfc5545#section-3.8.1.2]
         * The Parameters are stored as JSON. There are two helper functions provided:
         * getJsonStringFromXParameters(ParameterList?) that returns a Json String from the parameter list
         * to be stored in this other field. The counterpart to this function is
         * getXParameterFromJson(String) that returns a list of XParameters from a Json that was created with getJsonStringFromXParameters(...)
         * Type: [String]
         */
        const val OTHER = "other"
    }


    object JtxComment {

        /** The name of the the table for Comments that are linked to an ICalObject.
         * [https://tools.ietf.org/html/rfc5545#section-3.8.1.4]*/
        private const val CONTENT_URI_PATH = "comment"

        /** The content uri of the Comment table */
        val CONTENT_URI: Uri by lazy { "content://$AUTHORITY/$CONTENT_URI_PATH".toUri() }


        /** The name of the ID column for comments.
         * This is the unique identifier of a Comment
         * Type: [Long]*/
        const val ID = BaseColumns._ID

        /** The name of the Foreign Key Column for IcalObjects.
         * Type: [Long], references [JtxICalObject.ID]
         */
        const val ICALOBJECT_ID = "icalObjectId"


        /***** The names of all the other columns  *****/
        /**
         * Purpose:  This property specifies non-processing information intended to provide a comment to the calendar user.
         * see [https://tools.ietf.org/html/rfc5545#section-3.8.1.4]
         * Type: [String]
         */
        const val TEXT = "text"

        /**
         * Purpose:  To specify the language for text values in a property or property parameter, in this case of the comment.
         * see [https://tools.ietf.org/html/rfc5545#section-3.8.1.4]
         * Language-Tag as defined in RFC5646, e.g. "en:Germany"
         * Type: [String]
         */
        const val ALTREP = "altrep"

        /**
         * Purpose:  To specify an alternate text representation for the property value, in this case of the comment.
         * see [https://tools.ietf.org/html/rfc5545#section-3.8.1.4]
         * Type: [String]
         */
        const val LANGUAGE = "language"

        /**
         * Purpose:  To specify other properties for the comment.
         * see [https://tools.ietf.org/html/rfc5545#section-3.8.1.4]
         * The Parameters are stored as JSON. There are two helper functions provided:
         * getJsonStringFromXParameters(ParameterList?) that returns a Json String from the parameter list
         * to be stored in this other field. The counterpart to this function is
         * getXParameterFromJson(String) that returns a list of XParameters from a Json that was created with getJsonStringFromXParameters(...)
         * Type: [String]
         */
        const val OTHER = "other"
    }



    object JtxOrganizer {
        /** The name of the the table for Organizer that are linked to an ICalObject.
         * [https://tools.ietf.org/html/rfc5545#section-3.8.4.3]
         */
        private const val CONTENT_URI_PATH = "organizer"

        /** The content uri of the Organizer table */
        val CONTENT_URI: Uri by lazy { "content://$AUTHORITY/$CONTENT_URI_PATH".toUri() }


        /** The name of the ID column for the organizer.
         * This is the unique identifier of a Organizer
         * Type: [Long]*/
        const val ID = BaseColumns._ID

        /** The name of the Foreign Key Column for IcalObjects.
         * Type: [Long], references [JtxICalObject.ID]
         */
        const val ICALOBJECT_ID = "icalObjectId"


        /***** The names of all the other columns  *****/
        /**
         * Purpose:  This value type is used to identify properties that contain a calendar user address (in this case of the organizer).
         * This is usually an e-mail address as defined in [https://datatracker.ietf.org/doc/html/rfc5545#section-3.3.3].
         * The value should be passed as String containing the URI with "mailto:", for example: "mailto:jane_doe@example.com"
         * see [https://tools.ietf.org/html/rfc5545#section-3.8.4.3].
         * Type: [String]
         */
        const val CALADDRESS = "caladdress"

        /**
         * Purpose:  To specify the common name to be associated with the calendar user specified by the property in this case for the organizer.
         * see [https://tools.ietf.org/html/rfc5545#section-3.8.4.3] and [https://tools.ietf.org/html/rfc5545#section-3.2.18]
         * Type: [String]
         */
        const val CN = "cnparam"

        /**
         * Purpose:  To specify reference to a directory entry associated with the calendar user specified by the property in this case for the organizer.
         * Expected is an URI as defined in [https://datatracker.ietf.org/doc/html/rfc5545#section-3.2.6].
         * The value should be passed as String, e.g. "ldap://example.com:6666/o=ABC%20Industries,c=US???(cn=Jim%20Dolittle)"
         * see [https://tools.ietf.org/html/rfc5545#section-3.8.4.3] and [https://tools.ietf.org/html/rfc5545#section-3.2.2]
         * Type: [String]
         */
        const val DIR = "dirparam"

        /**
         * Purpose:  To specify the calendar user that is acting on behalf of the calendar user specified by the property in this case for the organizer.
         * This is usually an e-mail address as defined in [https://datatracker.ietf.org/doc/html/rfc5545#section-3.3.3].
         * The value should be passed as String containing the URI with "mailto:", for example: "mailto:jane_doe@example.com"
         * See also [https://tools.ietf.org/html/rfc5545#section-3.8.4.3] and [https://datatracker.ietf.org/doc/html/rfc5545#section-3.2.18]
         * Type: [String]
         */
        const val SENTBY = "sentbyparam"

        /**
         * Purpose:  To specify the language for text values in a property or property parameter, in this case of the organizer.
         * see [https://tools.ietf.org/html/rfc5545#section-3.8.4.3] and [https://tools.ietf.org/html/rfc5545#section-3.2.10]
         * Language-Tag as defined in RFC5646, e.g. "en:Germany"
         * Type: [String]
         */
        const val LANGUAGE = "language"

        /**
         * Purpose:  To specify other properties for the organizer.
         * see [https://tools.ietf.org/html/rfc5545#section-3.8.4.3]
         * The Parameters are stored as JSON. There are two helper functions provided:
         * getJsonStringFromXParameters(ParameterList?) that returns a Json String from the parameter list
         * to be stored in this other field. The counterpart to this function is
         * getXParameterFromJson(String) that returns a list of XParameters from a Json that was created with getJsonStringFromXParameters(...)
         * Type: [String]
         */
        const val OTHER = "other"


    }


    object JtxRelatedto {

        /** The name of the the table for Relationships (related-to) that are linked to an ICalObject.
         * [https://tools.ietf.org/html/rfc5545#section-3.8.4.5]
         */
        private const val CONTENT_URI_PATH = "relatedto"

        /** The content uri of the relatedto table */
        val CONTENT_URI: Uri by lazy { "content://$AUTHORITY/$CONTENT_URI_PATH".toUri() }


        /** The name of the ID column for the related-to.
         * This is the unique identifier of a Related-to
         * Type: [Long]*/
        const val ID = BaseColumns._ID

        /** The name of the Foreign Key Column for IcalObjects.
         * Type: [Long], references [JtxICalObject.ID]
         */
        const val ICALOBJECT_ID = "icalObjectId"

        /** The name of the second Foreign Key Column of the related IcalObject
         * Type: [Long]
         */
        const val LINKEDICALOBJECT_ID = "linkedICalObjectId"


        /***** The names of all the other columns  *****/
        /**
         * Purpose:  This property is used to represent a relationship or reference between one calendar component and another.
         * The text gives the UID of the related calendar entry.
         * see [https://tools.ietf.org/html/rfc5545#section-3.8.4.5]
         * Type: [String]
         */
        const val TEXT = "text"

        /**
         * Purpose:  To specify the type of hierarchical relationship associated
         * with the calendar component specified by the property.
         * The possible relationship types are defined in the enum [Reltype].
         * Use e.g. Reltype.PARENT.name to put a correct String value in this field.
         * Other values and null-values are allowed, but will not be processed by the app.
         * see [https://tools.ietf.org/html/rfc5545#section-3.8.4.5] and [https://tools.ietf.org/html/rfc5545#section-3.2.15]
         * Type: [String]
         */
        const val RELTYPE = "reltype"

        /**
         * Purpose:  To specify other properties for the related-to.
         * see [https://tools.ietf.org/html/rfc5545#section-3.8.4.5]
         * The Parameters are stored as JSON. There are two helper functions provided:
         * getJsonStringFromXParameters(ParameterList?) that returns a Json String from the parameter list
         * to be stored in this other field. The counterpart to this function is
         * getXParameterFromJson(String) that returns a list of XParameters from a Json that was created with getJsonStringFromXParameters(...)
         * Type: [String]
         */
        const val OTHER = "other"


        /**
         * This enum class defines the possible values for the attribute Reltype of an [JtxRelatedto].
         * Use its name when the string representation is required, e.g. Reltype.PARENT.name.
         */
        enum class Reltype {
            PARENT, CHILD, SIBLING
        }

    }


    object JtxResource {
        /** The name of the the table for Resources that are linked to an ICalObject.
         * [https://tools.ietf.org/html/rfc5545#section-3.8.1.10]*/
        private const val CONTENT_URI_PATH = "resource"

        /** The content uri of the resources table */
        val CONTENT_URI: Uri by lazy { "content://$AUTHORITY/$CONTENT_URI_PATH".toUri() }


        /** The name of the ID column for resources.
         * This is the unique identifier of a Resource
         * Type: [Long]*/
        const val ID = BaseColumns._ID

        /** The name of the Foreign Key Column for IcalObjects.
         * Type: [Long], references [JtxICalObject.ID]
         */
        const val ICALOBJECT_ID = "icalObjectId"


        /***** The names of all the other columns  *****/
        /**
         * Purpose:  This property defines the name of the resource for a calendar component.
         * see [https://tools.ietf.org/html/rfc5545#section-3.8.1.10]
         * Type: [String]
         */
        const val TEXT = "text"

        /**
         * Purpose:  To specify an alternate text representation for the property value, in this case of the resource.
         * see [https://tools.ietf.org/html/rfc5545#section-3.8.1.4]
         * Language-Tag as defined in RFC5646, e.g. "en:Germany"
         * Type: [String]
         */
        const val LANGUAGE = "language"

        /**
         * Purpose:  To specify other properties for the resource.
         * see [https://tools.ietf.org/html/rfc5545#section-3.8.1.10]
         * The Parameters are stored as JSON. There are two helper functions provided:
         * getJsonStringFromXParameters(ParameterList?) that returns a Json String from the parameter list
         * to be stored in this other field. The counterpart to this function is
         * getXParameterFromJson(String) that returns a list of XParameters from a Json that was created with getJsonStringFromXParameters(...)
         * Type: [String]
         */
        const val OTHER = "other"

    }


    object JtxCollection {

        /** The name of the the table for Collections
         * ICalObjects MUST be linked to a collection! */
        private const val CONTENT_URI_PATH = "collection"

        /** The content uri of the collections table */
        val CONTENT_URI: Uri by lazy { "content://$AUTHORITY/$CONTENT_URI_PATH".toUri() }

        /**
         * Account type used for testing. This account type must be used for integrated testing.
         * Otherwise the application would check if an account exists in the Android accounts
         * and delete the test account/collections immediately. Using this test account prevents
         * this behaviour for Debug builds.
         */
        const val TEST_ACCOUNT_TYPE = "TEST"


        /** The name of the the table for Collections.
         * ICalObjects MUST be linked to a collection! */
        const val TABLE_NAME_COLLECTION = "collection"

        /** The name of the ID column for collections.
         * This is the unique identifier of a Collection
         * Type: [Long]*/
        const val ID = BaseColumns._ID

        /***** The names of all the other columns  *****/
        /**
         * Purpose:  This column/property defines the url of the collection.
         * Type: [String]
         */
        const val URL = "url"

        /**
         * Purpose:  This column/property defines the display name of the collection.
         * Type: [String]
         */
        const val DISPLAYNAME = "displayname"

        /**
         * Purpose:  This column/property defines a description of the collection.
         * Type: [String]
         */
        const val DESCRIPTION = "description"

        /**
         * Purpose:  This column/property defines the URL of the owner of the collection.
         * Type: [String]
         */
        const val OWNER = "owner"

        /**
         * Purpose:  This column/property defines the display name of the owner of the collection.
         * Type: [String]
         */
        const val OWNER_DISPLAYNAME = "ownerdisplayname"

        /**
         * Purpose:  This column/property defines the color of the collection items.
         * This color can also be overwritten by the color in an ICalObject.
         * The color is represented as Int-value as described in [https://developer.android.com/reference/android/graphics/Color#color-ints]
         * Type: [Int]
         */
        const val COLOR = "color"

        /**
         * Purpose:  This column/property defines the if the collection supports VEVENTs.
         * Type: [Boolean]
         */
        const val SUPPORTSVEVENT = "supportsVEVENT"

        /**
         * Purpose:  This column/property defines the if the collection supports VTODOs.
         * Type: [Boolean]
         */
        const val SUPPORTSVTODO = "supportsVTODO"

        /**
         * Purpose:  This column/property defines the if the collection supports VJOURNALs.
         * Type: [Boolean]
         */
        const val SUPPORTSVJOURNAL = "supportsVJOURNAL"

        /**
         * Purpose:  This column/property defines the if the account name under which the collection resides.
         * Type: [String]
         */
        const val ACCOUNT_NAME = "accountname"

        /**
         * Purpose:  This column/property defines the if the account type under which the collection resides.
         * Type: [String]
         */
        const val ACCOUNT_TYPE = "accounttype"

        /**
         * Purpose:  This column/property defines a field for the Sync Version for the Sync Adapter
         * Type: [String]
         */
        const val SYNC_VERSION = "syncversion"

        /**
         * Purpose:  This column/property defines if a collection is marked as read-only by the Sync Adapter
         * Type: [Boolean]
         */
        const val READONLY = "readonly"

        /**
         * Purpose:  This column/property defines the date/time of the last sync
         * Type: [Long]
         */
        const val LAST_SYNC = "lastsync"

        /**
         * Purpose:  This column/property stores a sync_id for the given collection
         * See https://github.com/TechbeeAT/jtxBoard/issues/1635
         * Type: [Long]
         */
        const val SYNC_ID = "sync_id"
    }



    object JtxAttachment {

        /** The name of the the table for Attachments that are linked to an ICalObject.*/
        private const val CONTENT_URI_PATH = "attachment"

        /** The content uri of the resources table */
        val CONTENT_URI: Uri by lazy { "content://$AUTHORITY/$CONTENT_URI_PATH".toUri() }


        /** The name of the ID column for attachments.
         * This is the unique identifier of an Attachment
         * Type: [Long]*/
        const val ID = BaseColumns._ID

        /** The name of the Foreign Key Column for IcalObjects.
         * Type: [Long], references [JtxICalObject.ID]
         */
        const val ICALOBJECT_ID = "icalObjectId"


        /***** The names of all the other columns  *****/
        /**
         * Purpose:  This property specifies the uri of an attachment.
         * see [https://tools.ietf.org/html/rfc5545#section-3.8.1.1]
         * Type: [String]
         */
        const val URI = "uri"

        /**
         * Purpose:  To specify the value of the attachment (binary).
         * see [https://tools.ietf.org/html/rfc5545#section-3.8.1.1]
         * Type: [String]
         */
        const val BINARY = "binary"

        /**
         * Purpose:  To specify the fmttype of the attachment.
         * see [https://tools.ietf.org/html/rfc5545#section-3.8.1.1]
         * Type: [String]
         */
        const val FMTTYPE = "fmttype"

        /**
         * Purpose:  To specify the filename of the attachment.
         * This is an X-PROPERTY that should be addressed as "X-LABEL"
         * Type: [String]
         */
        const val FILENAME = "filename"

        /**
         * Purpose:  To specify other properties for the attachment.
         * see [https://tools.ietf.org/html/rfc5545#section-3.8.1.1]
         * The Parameters are stored as JSON. There are two helper functions provided:
         * getJsonStringFromXParameters(ParameterList?) that returns a Json String from the parameter list
         * to be stored in this other field. The counterpart to this function is
         * getXParameterFromJson(String) that returns a list of XParameters from a Json that was created with getJsonStringFromXParameters(...)
         * Type: [String]
         */
        const val OTHER = "other"

    }



    object JtxAlarm {

        /** The name of the the table for Alarms that are linked to an ICalObject.*/
        private const val CONTENT_URI_PATH = "alarm"

        /** The content uri of the resources table */
        val CONTENT_URI: Uri by lazy { "content://$AUTHORITY/$CONTENT_URI_PATH".toUri() }


        /** The name of the ID column for attachments.
         * This is the unique identifier of an Attachment
         * Type: [Long], references [JtxICalObject.ID]
         */
        const val ID = BaseColumns._ID

        /** The name of the Foreign Key Column for IcalObjects.
         * Type: [Long], references [JtxICalObject.ID]
         */
        const val ICALOBJECT_ID = "icalObjectId"

        /**
         * Each "VALARM" calendar component has a particular type
         * of action with which it is associated.  This property specifies
         * the type of action.  Applications MUST ignore alarms with x-name
         * and iana-token values they don't recognize.
         * Currently only "DISPLAY" is supported, all other values can be stored but are ignored
         * Type: [String]
         */
        const val ACTION = "action"

        /**
         * This property provides a more complete description of the
         * calendar component than that provided by the "SUMMARY" property.
         * Type: [String]
         */
        const val DESCRIPTION = "description"

        /**
         * This property defines a short summary or subject for the
         * calendar component.
         * Type: [String]
         */
        const val SUMMARY = "summary"

        /**
         * This property contains a CSV-list of attendees as Uris
         * e.g. "mailto:contact@techbee.at,mailto:jtx@techbee.at"
         */
        const val ATTENDEE = "attendee"

        /**
         * The alarm can be defined such that it triggers repeatedly.  A
         * definition of an alarm with a repeating trigger MUST include both
         * the "DURATION" and "REPEAT" properties.  The "DURATION" property
         * specifies the delay period, after which the alarm will repeat.
         * Type: [String]
         */
        const val DURATION = "duration"

        /**
         * The "REPEAT" property specifies the number of additional
         * repetitions that the alarm will be triggered.  This repetition
         * count is in addition to the initial triggering of the alarm.  Both
         * of these properties MUST be present in order to specify a
         * repeating alarm.  If one of these two properties is absent, then
         * the alarm will not repeat beyond the initial trigger.
         * Type: [String]
         */
        const val REPEAT = "repeat"

        /**
         * Contains the uri of an attachment
         * Type: [String]
         */
        const val ATTACH = "attach"

        /**
         * Purpose:  To specify other properties for the alarm.
         * see [https://tools.ietf.org/html/rfc5545#section-3.8.4.3]
         * Type: [String]
         */
        const val OTHER = "other"

        /**
         * Stores a timestamp with the absolute time when the alarm should be triggered.
         * This value is stored as UNIX timestamp (milliseconds).
         * Either a Alarm Trigger Time OR a Alarm Relative Duration must be provided, but not both!
         * Type: [Long]
         */
        const val TRIGGER_TIME = "triggerTime"

        /**
         * Purpose:  This column/property specifies the timezone of the absolute trigger time.
         * The corresponding datetime is stored in [TRIGGER_TIME].
         * The value of a timezone can be:
         * 1. the id of a Java timezone to represent the given timezone.
         * 2. null to represent floating time.
         * If an invalid value is passed, the Timezone is ignored and interpreted as UTC.
         * See [https://tools.ietf.org/html/rfc5545#section-3.8.2.4]
         * Type: [String]
         */
        const val TRIGGER_TIMEZONE = "triggerTimezone"

        /**
         * Purpose:  This property defines the field to which the duration is relatiive to.
         * The possible values of a status are defined in the enum [AlarmRelativeTo].
         * Use e.g. AlarmRelativeTo.START.name to put a correct String value in this field.
         * AlarmRelativeTo.START would make the duration relative to DTSTART.
         * AlarmRelativeTo.END would make the duration relative to DUE (only VTODO is supported!).
         * If no valid AlarmRelativeTo is provided, the default value is AlarmRelativeTo.START.
         * Type: [String]
         */
        const val TRIGGER_RELATIVE_TO = "triggerRelativeTo"

        /**
         * Purpose: Specifying a relative time for the
         * trigger of the alarm.  The default duration is relative to the
         * start of an event or to-do with which the alarm is associated.
         * The duration can be explicitly set to trigger from either the end
         * or the start of the associated event or to-do with the [TRIGGER_RELATIVE_TO]
         * parameter.  A value of START will set the alarm to trigger off the
         * start of the associated event or to-do.  A value of END will set
         * the alarm to trigger off the end of the associated event or to-do.
         * Either a positive or negative duration may be specified for the
         * "TRIGGER" property.  An alarm with a positive duration is
         * triggered after the associated start or end of the event or to-do.
         * An alarm with a negative duration is triggered before the
         * associated start or end of the event or to-do.
         * Type: [String]
         */
        const val TRIGGER_RELATIVE_DURATION = "triggerRelativeDuration"

        /** This enum class defines the possible values for the attribute [TRIGGER_RELATIVE_TO] for the Component VALARM  */
        enum class AlarmRelativeTo {
            START, END
        }

        /** This enum class defines the possible values for the attribute [ACTION] for the Component VALARM  */
        enum class AlarmAction {
            AUDIO, DISPLAY, EMAIL
        }
    }


    object JtxUnknown {

        /** The name of the the table for Unknown properties that are linked to an ICalObject.*/
        private const val CONTENT_URI_PATH = "unknown"

        /** The content uri of the resources table */
        val CONTENT_URI: Uri by lazy { "content://$AUTHORITY/$CONTENT_URI_PATH".toUri() }


        /** The name of the ID column for attachments.
         * This is the unique identifier of an Attachment
         * Type: [Long], references [JtxICalObject.ID]
         */
        const val ID = BaseColumns._ID

        /** The name of the Foreign Key Column for IcalObjects.
         * Type: [Long] */
        const val ICALOBJECT_ID = "icalObjectId"


        /***** The names of all the other columns  *****/
        /**
         * Purpose:  This property stores the unknown value as json
         * Type: [String]
         */
        const val UNKNOWN_VALUE = "value"
    }
}