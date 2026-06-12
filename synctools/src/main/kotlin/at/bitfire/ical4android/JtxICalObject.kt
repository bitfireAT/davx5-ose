/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.ical4android

import android.content.ContentUris
import android.content.ContentValues
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Base64
import androidx.annotation.VisibleForTesting
import androidx.core.content.contentValuesOf
import at.bitfire.synctools.exception.InvalidICalendarException
import at.bitfire.synctools.icalendar.Css3Color
import at.bitfire.synctools.icalendar.DatePropertyTzMapper.normalizedDate
import at.bitfire.synctools.icalendar.DatePropertyTzMapper.normalizedDates
import at.bitfire.synctools.icalendar.ICalendarParser
import at.bitfire.synctools.icalendar.plusAssign
import at.bitfire.synctools.icalendar.withUserAgents
import at.bitfire.synctools.storage.BatchOperation
import at.bitfire.synctools.storage.TaskProvider
import at.bitfire.synctools.storage.UnknownProperty
import at.bitfire.synctools.storage.jtx.JtxBatchOperation
import at.bitfire.synctools.storage.jtx.JtxCollection
import at.bitfire.synctools.storage.toContentValues
import at.bitfire.synctools.util.AndroidTimeUtils.toTimestamp
import at.bitfire.synctools.util.TimeApiExtensions.toLocalDate
import at.techbee.jtx.JtxContract
import at.techbee.jtx.JtxContract.JtxICalObject.TZ_ALLDAY
import at.techbee.jtx.JtxContract.asSyncAdapter
import net.fortuna.ical4j.data.CalendarOutputter
import net.fortuna.ical4j.model.Calendar
import net.fortuna.ical4j.model.ComponentList
import net.fortuna.ical4j.model.DateList
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.ParameterList
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.PropertyList
import net.fortuna.ical4j.model.TextList
import net.fortuna.ical4j.model.component.VAlarm
import net.fortuna.ical4j.model.component.VJournal
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.parameter.AltRep
import net.fortuna.ical4j.model.parameter.Cn
import net.fortuna.ical4j.model.parameter.CuType
import net.fortuna.ical4j.model.parameter.DelegatedFrom
import net.fortuna.ical4j.model.parameter.DelegatedTo
import net.fortuna.ical4j.model.parameter.Dir
import net.fortuna.ical4j.model.parameter.FmtType
import net.fortuna.ical4j.model.parameter.Language
import net.fortuna.ical4j.model.parameter.Member
import net.fortuna.ical4j.model.parameter.PartStat
import net.fortuna.ical4j.model.parameter.RelType
import net.fortuna.ical4j.model.parameter.Related
import net.fortuna.ical4j.model.parameter.Role
import net.fortuna.ical4j.model.parameter.Rsvp
import net.fortuna.ical4j.model.parameter.SentBy
import net.fortuna.ical4j.model.parameter.TzId
import net.fortuna.ical4j.model.parameter.Value
import net.fortuna.ical4j.model.parameter.XParameter
import net.fortuna.ical4j.model.property.Action
import net.fortuna.ical4j.model.property.Attach
import net.fortuna.ical4j.model.property.Categories
import net.fortuna.ical4j.model.property.Clazz
import net.fortuna.ical4j.model.property.Color
import net.fortuna.ical4j.model.property.Completed
import net.fortuna.ical4j.model.property.Contact
import net.fortuna.ical4j.model.property.Created
import net.fortuna.ical4j.model.property.Description
import net.fortuna.ical4j.model.property.DtEnd
import net.fortuna.ical4j.model.property.DtStamp
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.Due
import net.fortuna.ical4j.model.property.Duration
import net.fortuna.ical4j.model.property.ExDate
import net.fortuna.ical4j.model.property.Geo
import net.fortuna.ical4j.model.property.LastModified
import net.fortuna.ical4j.model.property.Location
import net.fortuna.ical4j.model.property.PercentComplete
import net.fortuna.ical4j.model.property.Priority
import net.fortuna.ical4j.model.property.ProdId
import net.fortuna.ical4j.model.property.RDate
import net.fortuna.ical4j.model.property.RRule
import net.fortuna.ical4j.model.property.RecurrenceId
import net.fortuna.ical4j.model.property.Repeat
import net.fortuna.ical4j.model.property.Resources
import net.fortuna.ical4j.model.property.Sequence
import net.fortuna.ical4j.model.property.Status
import net.fortuna.ical4j.model.property.Summary
import net.fortuna.ical4j.model.property.Trigger
import net.fortuna.ical4j.model.property.Uid
import net.fortuna.ical4j.model.property.Url
import net.fortuna.ical4j.model.property.XProperty
import net.fortuna.ical4j.model.property.immutable.ImmutableAction
import net.fortuna.ical4j.model.property.immutable.ImmutablePriority
import net.fortuna.ical4j.model.property.immutable.ImmutableVersion
import java.io.FileNotFoundException
import java.io.IOException
import java.io.OutputStream
import java.io.Reader
import java.net.URI
import java.net.URISyntaxException
import java.nio.ByteBuffer
import java.text.ParseException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeParseException
import java.time.temporal.Temporal
import java.util.UUID
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.jvm.optionals.getOrNull

@Deprecated("Use at.bitfire.synctools.storage.jtx + at.bitfire.synctools.mapping.jtx API instead")
class JtxICalObject(
    val collection: JtxCollection
) {

    var id: Long = 0L
    lateinit var component: String
    var summary: String? = null
    var description: String? = null
    var dtstart: Long? = null
    var dtstartTimezone: String? = null
    var dtend: Long? = null
    var dtendTimezone: String? = null

    var classification: String? = null
    var status: String? = null
    var xstatus: String? = null

    var priority: Int? = null

    var due: Long? = null      // VTODO only!
    var dueTimezone: String? = null //VTODO only!
    var completed: Long? = null // VTODO only!
    var completedTimezone: String? = null //VTODO only!
    var duration: String? = null //VTODO only!

    var percent: Int? = null
    var url: String? = null
    var contact: String? = null
    var geoLat: Double? = null
    var geoLong: Double? = null
    var location: String? = null
    var locationAltrep: String? = null
    var geofenceRadius: Int? = null

    var uid: String = UUID.randomUUID().toString()

    var created: Long = System.currentTimeMillis()
    var dtstamp: Long = System.currentTimeMillis()
    var lastModified: Long = System.currentTimeMillis()
    var sequence: Long = 0

    var color: Int? = null

    var rrule: String? = null    //only for recurring events, see https://tools.ietf.org/html/rfc5545#section-3.8.5.3
    var exdate: String? = null   //only for recurring events, see https://tools.ietf.org/html/rfc5545#section-3.8.5.1
    var rdate: String? = null    //only for recurring events, see https://tools.ietf.org/html/rfc5545#section-3.8.5.2
    var recurid: String? = null  //only for recurring events, see https://tools.ietf.org/html/rfc5545#section-3.8.5
    var recuridTimezone: String? = null
    //var rstatus: String? = null

    var collectionId: Long = collection.id

    var dirty: Boolean = false     // default false to avoid instant sync after insert, can be overwritten
    var deleted: Boolean = false

    var fileName: String? = null
    var eTag: String? = null
    var scheduleTag: String? = null
    var flags: Int = 0

    var categories: MutableList<Category> = mutableListOf()
    var attachments: MutableList<Attachment> = mutableListOf()
    var attendees: MutableList<Attendee> = mutableListOf()
    var comments: MutableList<Comment> = mutableListOf()
    var organizer: Organizer? = null
    var resources: MutableList<Resource> = mutableListOf()
    var relatedTo: MutableList<RelatedTo> = mutableListOf()
    var alarms: MutableList<Alarm> = mutableListOf()
    var unknown: MutableList<Unknown> = mutableListOf()

    @VisibleForTesting
    internal var recurInstances: MutableList<JtxICalObject> = mutableListOf()




    data class Category(
        var categoryId: Long = 0L,
        var text: String? = null,
        var language: String? = null,
        var other: String? = null
    )

    data class Attachment(
        var attachmentId: Long = 0L,
        var uri: String? = null,
        var binary: String? = null,
        var fmttype: String? = null,
        var other: String? = null,
        var filename: String? = null,
        var extension: String? = null,
        var filesize: Long? = null
    )

    data class Comment(
        var commentId: Long = 0L,
        var text: String? = null,
        var altrep: String? = null,
        var language: String? = null,
        var other: String? = null
    )

    data class RelatedTo(
        var relatedtoId: Long = 0L,
        var text: String? = null,
        var reltype: String? = null,
        var other: String? = null
    )

    data class Attendee(
        var attendeeId: Long = 0L,
        var caladdress: String? = null,
        var cutype: String? = JtxContract.JtxAttendee.Cutype.INDIVIDUAL.name,
        var member: String? = null,
        var role: String? = JtxContract.JtxAttendee.Role.`REQ-PARTICIPANT`.name,
        var partstat: String? = null,
        var rsvp: Boolean? = null,
        var delegatedto: String? = null,
        var delegatedfrom: String? = null,
        var sentby: String? = null,
        var cn: String? = null,
        var dir: String? = null,
        var language: String? = null,
        var other: String? = null
    )

    data class Resource(
        var resourceId: Long = 0L,
        var text: String? = null,
        var altrep: String? = null,
        var language: String? = null,
        var other: String? = null
    )

    data class Organizer(
        var organizerId: Long = 0L,
        var caladdress: String? = null,
        var cn: String? = null,
        var dir: String? = null,
        var sentby: String? = null,
        var language: String? = null,
        var other: String? = null
    )

    data class Alarm(
        var alarmId: Long = 0L,
        var action: String? = null,
        var description: String? = null,
        var summary: String? = null,
        var attendee: String? = null,
        var duration: String? = null,
        var repeat: String? = null,
        var attach: String? = null,
        var other: String? = null,
        var triggerTime: Long? = null,
        var triggerTimezone: String? = null,
        var triggerRelativeTo: String? = null,
        var triggerRelativeDuration: String? = null
    )

    data class Unknown(
        var unknownId: Long = 0L,
        var value: String? = null
    )


    companion object {

        private val logger
            get() = Logger.getLogger(JtxICalObject::class.java.name)

        const val X_PROP_COMPLETEDTIMEZONE = "X-COMPLETEDTIMEZONE"
        const val X_PARAM_ATTACH_LABEL = "X-LABEL"     // used for filename in KOrganizer
        const val X_PARAM_FILENAME = "FILENAME"     // used for filename in GNOME Evolution
        const val X_PROP_XSTATUS = "X-STATUS"   // used to define an extended status (additionally to standard status)
        const val X_PROP_GEOFENCE_RADIUS = "X-GEOFENCE-RADIUS"   // used to define a Geofence-Radius to notifiy the user when close

        /**
         * Parses an iCalendar resource and extracts the VTODOs and/or VJOURNALS.
         *
         * @param reader where the iCalendar is taken from
         *
         * @return array of filled [JtxICalObject] data objects (may have size 0)
         *
         * @throws InvalidICalendarException when the iCalendar can't be parsed
         * @throws IOException on I/O errors
         */
        fun fromReader(
            reader: Reader,
            collection: JtxCollection
        ): List<JtxICalObject> {
            val ical = ICalendarParser().parse(reader)

            val iCalObjectList = mutableListOf<JtxICalObject>()

            ical.componentList.all.forEach { component ->

                val iCalObject = JtxICalObject(collection)
                when(component) {
                    is VToDo -> {
                        iCalObject.component = JtxContract.JtxICalObject.Component.VTODO.name
                        component.uid.getOrNull()?.let { uid ->
                            iCalObject.uid = uid.value // generated UID is overwritten here (if present)
                        }
                        extractProperties(iCalObject, component.propertyList)
                        extractVAlarms(iCalObject, component.componentList) // accessing the components needs an explicit type
                        iCalObjectList.add(iCalObject)
                    }
                    is VJournal -> {
                        iCalObject.component = JtxContract.JtxICalObject.Component.VJOURNAL.name
                        component.uid.getOrNull()?.let { uid ->
                            iCalObject.uid = uid.value
                        }
                        extractProperties(iCalObject, component.propertyList)
                        extractVAlarms(iCalObject, component.componentList) // accessing the components needs an explicit type
                        iCalObjectList.add(iCalObject)
                    }
                }
            }
            return iCalObjectList
        }

        /**
         * Extracts VAlarms from the given Component (VJOURNAL or VTODO). The VAlarm is supposed to be a component within the VJOURNAL or VTODO component.
         * Other components than VAlarms should not occur.
         * @param [iCalObject] where the VAlarms should be inserted
         * @param [calComponents] from which the VAlarms should be extracted
         */
        private fun extractVAlarms(iCalObject: JtxICalObject, calComponents: ComponentList<*>) {
            calComponents.all.forEach { component ->
                if(component is VAlarm) {
                    val jtxAlarm = Alarm().apply {
                        component.getProperty<Action>(Property.ACTION).getOrNull()?.let { vAlarmAction -> this.action = vAlarmAction.value }
                        component.summary?.let { vAlarmSummary -> this.summary = vAlarmSummary.value }
                        component.description?.let { vAlarmDesc -> this.description = vAlarmDesc.value }
                        component.getProperty<Duration>(Property.DURATION).getOrNull()?.let { vAlarmDur -> this.duration = vAlarmDur.value }
                        component.getProperty<Attach>(Property.ATTACH).getOrNull()?.uri?.let { uri -> this.attach = uri.toString() }
                        component.getProperty<Repeat>(Property.REPEAT)?.getOrNull()?.let { vAlarmRep -> this.repeat = vAlarmRep.value }

                        // alarms can have a duration or an absolute dateTime, but not both!
                        component.getProperty<Trigger>(Property.TRIGGER).getOrNull()?.let { trigger ->
                            if(trigger.duration != null) {
                                trigger.duration?.let { duration -> this.triggerRelativeDuration = duration.toString() }
                                trigger.getParameter<Related>(Parameter.RELATED)?.getOrNull()?.let { related -> this.triggerRelativeTo = related.value }
                            } else if(trigger.isAbsolute) { // self-contained (not relative to dtstart/dtend)
                                val normalizedTrigger = trigger.normalizedDate() // Ensure timezone exists in system
                                this.triggerTime = normalizedTrigger.toTimestamp()
                                this.triggerTimezone =  normalizedTrigger.getTimeZoneId()
                            }
                        }

                        // remove properties to add the rest to other
                        component.propertyList.removeAll(
                            Property.ACTION,
                            Property.SUMMARY,
                            Property.DESCRIPTION,
                            Property.DURATION,
                            Property.ATTACH,
                            Property.REPEAT,
                            Property.TRIGGER
                        )
                        component.propertyList?.let { vAlarmProps -> this.other = JtxContract.getJsonStringFromXProperties(vAlarmProps) }
                    }
                    iCalObject.alarms.add(jtxAlarm)
                }
            }
        }

        /**
         * Extracts properties from a given Property list and maps it to a JtxICalObject
         * @param [iCalObject] where the properties should be mapped to
         * @param [properties] from which the properties can be extracted
         */
        private fun extractProperties(iCalObject: JtxICalObject, properties: PropertyList) {
            // sequence must only be null for locally created, not-yet-synchronized events
            iCalObject.sequence = 0

            for (prop in properties.all) {
                when (prop) {
                    is Sequence -> iCalObject.sequence = prop.sequenceNo.toLong()
                    is Created -> iCalObject.created = prop.normalizedDate().toTimestamp()
                    is LastModified -> iCalObject.lastModified = prop.normalizedDate().toTimestamp()
                    is Summary -> iCalObject.summary = prop.value
                    is Location -> {
                        iCalObject.location = prop.value
                        if(!prop.parameterList.all.isEmpty() && prop.parameterList.getFirst<AltRep>(Parameter.ALTREP) != null)
                            iCalObject.locationAltrep = prop.parameterList.getFirst<AltRep>(Parameter.ALTREP).getOrNull()?.value
                    }
                    is Geo -> {
                        iCalObject.geoLat = prop.latitude.toDouble()
                        iCalObject.geoLong = prop.longitude.toDouble()
                    }
                    is Description -> iCalObject.description = prop.value
                    is Color -> iCalObject.color = Css3Color.fromString(prop.value)?.argb
                    is Url -> iCalObject.url = prop.value
                    is Contact -> iCalObject.contact = prop.value
                    is Priority -> iCalObject.priority = prop.level
                    is Clazz -> iCalObject.classification = prop.value
                    is Status -> iCalObject.status = prop.value
                    is DtEnd<*> -> logger.warning("The property DtEnd must not be used for VTODO and VJOURNAL, this value is rejected.")
                    is Completed -> {
                        if (iCalObject.component != JtxContract.JtxICalObject.Component.VTODO.name) {
                            logger.warning("The property Completed is only supported for VTODO, this value is rejected.")
                            continue
                        }
                        iCalObject.completed = prop.normalizedDate().toTimestamp()
                    }

                    is Due<*> -> {
                        if (iCalObject.component != JtxContract.JtxICalObject.Component.VTODO.name) {
                            logger.warning("The property Due is only supported for VTODO, this value is rejected.")
                            continue
                        }
                        iCalObject.due = prop.normalizedDate().toTimestamp()
                        iCalObject.dueTimezone = prop.normalizedDate().getTimeZoneId()
                    }

                    is Duration -> iCalObject.duration = prop.value

                    is DtStart<*> -> {
                        val temporal = prop.normalizedDate()
                        iCalObject.dtstart = temporal.toTimestamp()
                        iCalObject.dtstartTimezone = temporal.getTimeZoneId()
                    }

                    is PercentComplete -> {
                        if (iCalObject.component == JtxContract.JtxICalObject.Component.VTODO.name)
                            iCalObject.percent = prop.percentage
                        else
                            logger.warning("The property PercentComplete is only supported for VTODO, this value is rejected.")
                    }

                    is RRule<*> -> iCalObject.rrule = prop.value
                    is RDate<*> -> {
                        iCalObject.rdate = buildList {
                            if(!iCalObject.rdate.isNullOrEmpty())
                                addAll(JtxContract.getLongListFromString(iCalObject.rdate!!))
                            prop.normalizedDates().forEach { date ->
                                add(date.toTimestamp())
                            }
                        }.toTypedArray().joinToString(separator = ",")
                    }
                    is ExDate<*> -> {
                        iCalObject.exdate = buildList {
                            if(!iCalObject.exdate.isNullOrEmpty())
                                addAll(JtxContract.getLongListFromString(iCalObject.exdate!!))
                            prop.normalizedDates().forEach { date ->
                                add(date.toTimestamp())
                            }
                        }.toTypedArray().joinToString(separator = ",")
                    }

                    is RecurrenceId<*> -> {
                        iCalObject.recurid = prop.value
                        iCalObject.recuridTimezone = prop.normalizedDate().getTimeZoneId()
                    }

                    //is RequestStatus -> iCalObject.rstatus = prop.value

                    is Categories ->
                        for (category in prop.categories.texts)
                            iCalObject.categories.add(Category(text = category))

                    is net.fortuna.ical4j.model.property.Comment -> {
                        iCalObject.comments.add(
                            Comment().apply {
                                this.text = prop.value
                                this.language = prop.getParameter<Language>(Parameter.LANGUAGE)?.getOrNull()?.value
                                this.altrep = prop.getParameter<AltRep>(Parameter.ALTREP)?.getOrNull()?.value

                                // remove the known parameter
                                prop.removeAll<Property>(
                                    Parameter.LANGUAGE,
                                    Parameter.ALTREP
                                )

                                // save unknown parameters in the other field
                                this.other = JtxContract.getJsonStringFromXParameters(prop.parameterList)
                            })

                    }

                    is Resources ->
                        for (resource in prop.resources.texts)
                            iCalObject.resources.add(Resource(text = resource))

                    is Attach -> {
                        val attachment = Attachment()
                        prop.uri?.let { attachment.uri = it.toString() }
                        prop.getBinaryData()?.let {
                            attachment.binary = Base64.encodeToString(it, Base64.DEFAULT)
                        }
                        prop.getParameter<FmtType>(Parameter.FMTTYPE)?.getOrNull()?.let {
                            attachment.fmttype = it.value
                            prop.parameterList?.remove(it)
                        }
                        prop.getParameter<XParameter>(X_PARAM_ATTACH_LABEL)?.getOrNull()?.let {
                            attachment.filename = it.value
                            prop.parameterList.remove(it)
                        }
                        prop.getParameter<XParameter>(X_PARAM_FILENAME)?.getOrNull()?.let {
                            attachment.filename = it.value
                            prop.parameterList.remove(it)
                        }

                        attachment.other = JtxContract.getJsonStringFromXParameters(prop.parameterList)

                        if (attachment.uri?.isNotEmpty() == true || attachment.binary?.isNotEmpty() == true)   // either uri or value must be present!
                            iCalObject.attachments.add(attachment)
                    }

                    is net.fortuna.ical4j.model.property.RelatedTo -> {

                        iCalObject.relatedTo.add(
                            RelatedTo().apply {
                                this.text = prop.value
                                this.reltype = prop.getParameter<RelType>(RelType.RELTYPE)?.getOrNull()?.value ?: JtxContract.JtxRelatedto.Reltype.PARENT.name

                                // remove the known parameter
                                prop.removeAll<Property>(RelType.RELTYPE)

                                // save unknown parameters in the other field
                                this.other = JtxContract.getJsonStringFromXParameters(prop.parameterList)
                            })
                    }

                    is net.fortuna.ical4j.model.property.Attendee -> {
                        iCalObject.attendees.add(
                            Attendee().apply {
                                this.caladdress = prop.calAddress.toString()
                                this.cn = prop.getParameter<Cn>(Parameter.CN)?.getOrNull()?.value
                                this.delegatedto = prop.getParameter<DelegatedTo>(Parameter.DELEGATED_TO)?.getOrNull()?.value
                                this.delegatedfrom = prop.getParameter<DelegatedFrom>(Parameter.DELEGATED_FROM)?.getOrNull()?.value
                                this.cutype = prop.getParameter<CuType>(Parameter.CUTYPE)?.getOrNull()?.value
                                this.dir = prop.getParameter<Dir>(Parameter.DIR)?.getOrNull()?.value
                                this.language = prop.getParameter<Language>(Parameter.LANGUAGE)?.getOrNull()?.value
                                this.member = prop.getParameter<Member>(Parameter.MEMBER)?.getOrNull()?.value
                                this.partstat = prop.getParameter<PartStat>(Parameter.PARTSTAT)?.getOrNull()?.value
                                this.role = prop.getParameter<Role>(Parameter.ROLE)?.getOrNull()?.value
                                this.rsvp = prop.getParameter<Rsvp>(Parameter.RSVP)?.getOrNull()?.value?.toBoolean()
                                this.sentby = prop.getParameter<SentBy>(Parameter.SENT_BY)?.getOrNull()?.value

                                // remove all known parameters so that only unknown parameters remain
                                prop.removeAll<Property>(
                                    Parameter.CN,
                                    Parameter.DELEGATED_TO,
                                    Parameter.DELEGATED_FROM,
                                    Parameter.CUTYPE,
                                    Parameter.DIR,
                                    Parameter.LANGUAGE,
                                    Parameter.MEMBER,
                                    Parameter.PARTSTAT,
                                    Parameter.ROLE,
                                    Parameter.RSVP,
                                    Parameter.SENT_BY
                                )

                                // save unknown parameters in the other field
                                this.other = JtxContract.getJsonStringFromXParameters(prop.parameterList)
                            }
                        )
                    }
                    is net.fortuna.ical4j.model.property.Organizer -> {
                        iCalObject.organizer = Organizer().apply {
                            this.caladdress = prop.calAddress.toString()
                            this.cn = prop.getParameter<Cn>(Parameter.CN)?.getOrNull()?.value
                            this.dir = prop.getParameter<Dir>(Parameter.DIR)?.getOrNull()?.value
                            this.language = prop.getParameter<Language>(Parameter.LANGUAGE)?.getOrNull()?.value
                            this.sentby = prop.getParameter<SentBy>(Parameter.SENT_BY)?.getOrNull()?.value

                            // remove all known parameters so that only unknown parameters remain
                            prop.removeAll<Property>(
                                Parameter.CN,
                                Parameter.DIR,
                                Parameter.LANGUAGE,
                                Parameter.SENT_BY
                            )

                            // save unknown parameters in the other field
                            this.other = JtxContract.getJsonStringFromXParameters(prop.parameterList)
                        }
                    }

                    is Uid -> iCalObject.uid = prop.value
                    //is Uid,
                    is ProdId, is DtStamp -> {}    /* don't save these as unknown properties */
                    else -> when(prop.name) {
                        X_PROP_COMPLETEDTIMEZONE -> iCalObject.completedTimezone = prop.value
                        X_PROP_XSTATUS -> iCalObject.xstatus = prop.value
                        X_PROP_GEOFENCE_RADIUS -> iCalObject.geofenceRadius = try { prop.value.toInt() } catch (e: NumberFormatException) { logger.warning("Wrong format for geofenceRadius: ${prop.value}"); null }
                        else -> iCalObject.unknown.add(Unknown(value = UnknownProperty.toJsonString(prop)))               // save the whole property for unknown properties
                    }
                }
            }


            // There seem to be many invalid tasks out there because of some defect clients, do some validation.
            val dtStartTZ = iCalObject.dtstartTimezone
            val dueTZ = iCalObject.dueTimezone

            if (dtStartTZ != null && dueTZ != null) {
                if (dtStartTZ == TZ_ALLDAY && dueTZ != TZ_ALLDAY) {
                    logger.warning("DTSTART is DATE but DUE is DATE-TIME, rewriting DTSTART to DATE-TIME")
                    iCalObject.dtstartTimezone = dueTZ
                } else if (dtStartTZ != TZ_ALLDAY && dueTZ == TZ_ALLDAY) {
                    logger.warning("DTSTART is DATE-TIME but DUE is DATE, rewriting DUE to DATE-TIME")
                    iCalObject.dueTimezone = dtStartTZ
                }

                //previously due was dropped, now reduced to a warning, see also https://github.com/bitfireAT/ical4android/issues/70
                if (iCalObject.dtstart != null && iCalObject.due != null && iCalObject.due!! < iCalObject.dtstart!!)
                    logger.warning("Found invalid DUE < DTSTART")
            }

            if (iCalObject.duration != null && iCalObject.dtstart == null) {
                logger.warning("Found DURATION without DTSTART; ignoring")
                iCalObject.duration = null
            }
        }

        private fun Temporal.getTimeZoneId(): String? = when (this) {
            is ZonedDateTime ->
                this.zone.id // We got a timezone
            is Instant ->
                ZoneOffset.UTC.id // Instant is a point on the UTC timeline
            is LocalDateTime ->
                null // Timezone unknown => floating time
            is LocalDate ->
                TZ_ALLDAY // Without time, it is considered all-day
            else -> {
                logger.warning("Ignoring unsupported temporal type: ${this::class}")
                null
            }
        }
    }

    /**
     * Takes the current JtxICalObject and transforms it to a Calendar (ical4j)
     *
     * @param prodId    `PRODID` that identifies the app
     *
     * @return The current JtxICalObject transformed into a ical4j Calendar
     */
    fun getICalendarFormat(prodId: ProdId): Calendar? {
        val ical = Calendar()
        ical += ImmutableVersion.VERSION_2_0
        ical += prodId.withUserAgents(listOf(TaskProvider.ProviderName.JtxBoard.packageName))

        val calComponent = when (component) {
            JtxContract.JtxICalObject.Component.VTODO.name -> VToDo(true /* generates DTSTAMP */)
            JtxContract.JtxICalObject.Component.VJOURNAL.name -> VJournal(true /* generates DTSTAMP */)
            else -> return null
        }
        calComponent.propertyList = addProperties(calComponent.propertyList) // Need to re-set the immutable list
        ical += calComponent

        // add alarm components
        alarms.forEach { alarm ->
            val alarmProps = mutableListOf<Property>()
            alarm.action?.let {
                alarmProps += when (it) {
                    JtxContract.JtxAlarm.AlarmAction.DISPLAY.name -> ImmutableAction.DISPLAY
                    JtxContract.JtxAlarm.AlarmAction.AUDIO.name -> ImmutableAction.AUDIO
                    JtxContract.JtxAlarm.AlarmAction.EMAIL.name -> ImmutableAction.EMAIL
                    else -> return@let
                }
            }

            if (alarm.triggerRelativeDuration != null) {
                alarmProps += Trigger().apply {
                    try {
                        val dur = java.time.Duration.parse(alarm.triggerRelativeDuration)
                        this.duration = dur

                        // Add the RELATED parameter if present
                        alarm.triggerRelativeTo?.let {
                            if (it == JtxContract.JtxAlarm.AlarmRelativeTo.START.name)
                                this += Related.START
                            if (it == JtxContract.JtxAlarm.AlarmRelativeTo.END.name)
                                this += Related.END
                        }
                    } catch (e: DateTimeParseException) {
                        logger.log(Level.WARNING, "Could not parse Trigger duration as Duration.", e)
                    }
                }
            } else if (alarm.triggerTime != null) {
                alarmProps += Trigger().apply {
                    try {
                        when {
                            alarm.triggerTimezone == ZoneOffset.UTC.id ||
                                    alarm.triggerTimezone.isNullOrEmpty() ->
                                this.date = Instant.ofEpochMilli(alarm.triggerTime!!)

                            else -> {
                                this.date = ZonedDateTime.ofInstant(
                                    Instant.ofEpochMilli(alarm.triggerTime!!),
                                    ZoneId.of(alarm.triggerTimezone)
                                ).toInstant()
                            }
                        }
                    } catch (e: ParseException) {
                        logger.log(Level.WARNING, "TriggerTime could not be parsed.", e)
                    }
                }
            }

            alarm.summary?.let { alarmProps += Summary(it) }
            alarm.repeat?.let { alarmProps += Repeat().apply { value = it } }
            alarm.duration?.let {
                alarmProps += Duration().apply {
                    try {
                        val dur = java.time.Duration.parse(it)
                        this.duration = dur
                    } catch (e: DateTimeParseException) {
                        logger.log(Level.WARNING, "Could not parse duration as Duration.", e)
                    }
                }
            }
            alarm.description?.let { alarmProps += Description(it) }
            alarm.attach?.let { alarmProps += Attach().apply { value = it } }
            alarm.other?.let { alarmProps += JtxContract.getXPropertyListFromJson(it).all }

            // add VALARM to VTODO/VJOURNAL component
            val vAlarm = VAlarm(PropertyList(alarmProps))
            calComponent += vAlarm
        }

        // add a iCalendar component for each exception
        recurInstances.forEach { recurInstance ->
            // create a fresh component of the correct type and add to iCalendar
            val recurCalComponent = when (recurInstance.component) {
                JtxContract.JtxICalObject.Component.VTODO.name -> VToDo(true /* generates DTSTAMP */)
                JtxContract.JtxICalObject.Component.VJOURNAL.name -> VJournal(true /* generates DTSTAMP */)
                else -> return null
            }
            ical += recurCalComponent
            // assign properties (UID, RECURRENCE-ID, modified SUMMARY etc.) from the exception
            recurCalComponent.propertyList = recurInstance.addProperties(recurCalComponent.propertyList)
        }

        return ical
    }

    /**
     * Takes the current JtxICalObject, transforms it to an iCalendar and writes it in an OutputStream
     *
     * @param [os] OutputStream where iCalendar should be written to
     * @param prodId    `PRODID` that identifies the app
     */
    fun write(os: OutputStream, prodId: ProdId) {
        CalendarOutputter(false).output(this.getICalendarFormat(prodId), os)
    }

    /**
     * This function maps the current JtxICalObject to a iCalendar property list
     * @param [propertyList] The PropertyList where the properties should be added
     * @return A copy of given PropertyList with the added properties
     */
    private fun addProperties(propertyList: PropertyList): PropertyList? {
        val props = mutableListOf<Property>()
        uid.let { props += Uid(it) }
        sequence.let { props += Sequence(it.toInt()) }

        created.let { props += Created(Instant.ofEpochMilli(it)) }
        lastModified.let { props += LastModified(Instant.ofEpochMilli(it))}

        summary.let { props += Summary(it) }
        description?.let { props += Description(it) }

        location?.let { location ->
            val loc = Location(location)
            locationAltrep?.let { locationAltrep ->
                loc.add<Property>(AltRep(locationAltrep))
            }
            props += loc
        }
        if (geoLat != null && geoLong != null) {
            props += Geo(geoLat!!.toBigDecimal(), geoLong!!.toBigDecimal())
        }
        geofenceRadius?.let { geofenceRadius ->
            props += XProperty(X_PROP_GEOFENCE_RADIUS, geofenceRadius.toString())
        }
        color?.let { props += Color(null, Css3Color.nearestMatch(it).name) }
        url?.let {
            try {
                props += Url(URI(it))
            } catch (e: URISyntaxException) {
                logger.log(Level.WARNING, "Ignoring invalid task URL: $url", e)
            }
        }
        contact?.let {  props += Contact(it) }

        classification?.let { props += Clazz(it) }
        status?.let { props += Status(it) }
        xstatus?.let { xstatus ->
            props += XProperty(X_PROP_XSTATUS, xstatus)
        }

        categories.map { it.text }.let { categoryTextList ->
            if (categoryTextList.isNotEmpty())
                props += Categories(TextList(categoryTextList))
        }


        resources.map { it.text }.let { resourceTextList ->
            if (resourceTextList.isNotEmpty())
                props += Resources(resourceTextList)
        }


        comments.forEach { comment ->
            val c = net.fortuna.ical4j.model.property.Comment(comment.text).apply {
                comment.altrep?.let { this += AltRep(it) }
                comment.language?.let { this += Language(it) }
                comment.other?.let {
                    val xparams = JtxContract.getXParametersFromJson(it)
                    xparams.forEach { xparam ->
                        this += xparam
                    }
                }
            }
            props += c
        }


        attendees.forEach { attendee ->
            val attendeeProp = net.fortuna.ical4j.model.property.Attendee().apply {
                if(attendee.caladdress?.isNotEmpty() == true)
                    this.calAddress = try {
                        URI(attendee.caladdress)
                    } catch (_: Exception) {
                        logger.warning("Ignoring invalid attendee URI: ${attendee.caladdress}")
                        return@forEach
                    }

                attendee.cn?.let {
                    this += Cn(it)
                }
                attendee.cutype?.let {
                    this += when {
                        it.equals(CuType.INDIVIDUAL.value, ignoreCase = true) -> CuType.INDIVIDUAL
                        it.equals(CuType.GROUP.value, ignoreCase = true) -> CuType.GROUP
                        it.equals(CuType.ROOM.value, ignoreCase = true) -> CuType.ROOM
                        it.equals(CuType.RESOURCE.value, ignoreCase = true) -> CuType.RESOURCE
                        it.equals(CuType.UNKNOWN.value, ignoreCase = true) -> CuType.UNKNOWN
                        else -> CuType.UNKNOWN
                    }
                }
                attendee.delegatedfrom?.let {
                    this += DelegatedFrom(it)
                }
                attendee.delegatedto?.let {
                    this += DelegatedTo(it)
                }
                attendee.dir?.let { this += Dir(it) }
                attendee.language?.let {
                    this += Language(it)
                }
                attendee.member?.let {
                    this += Member(it)
                }
                attendee.partstat?.let {
                    this += PartStat(it)
                }
                attendee.role?.let {
                    this += Role(it)
                }
                attendee.rsvp?.let {
                    this += Rsvp(it)
                }
                attendee.sentby?.let {
                    this += SentBy(it)
                }
                attendee.other?.let {
                    val params = JtxContract.getXParametersFromJson(it)
                    params.forEach { xparam ->
                        this += xparam
                    }
                }
            }
            props += attendeeProp
        }

        organizer?.let { organizer ->
            val organizerProp = net.fortuna.ical4j.model.property.Organizer().apply {
                if(organizer.caladdress?.isNotEmpty() == true)
                    try {
                        this.calAddress = URI(organizer.caladdress)
                    } catch (_: Exception) {
                        logger.warning("Ignoring invalid organizer URI: ${organizer.caladdress}")
                    }

                organizer.cn?.let {
                    add<Property>(Cn(it))
                }
                organizer.dir?.let {
                    add<Property>(Dir(it))
                }
                organizer.language?.let {
                    add<Property>(Language(it))
                }
                organizer.sentby?.let {
                    add<Property>(SentBy(it))
                }
                organizer.other?.let {
                    val params = JtxContract.getXParametersFromJson(it)
                    params.forEach { xparam ->
                        add<Property>(xparam)
                    }
                }
            }
            props += organizerProp
        }

        attachments.forEach { attachment ->
            try {
                if (attachment.uri?.startsWith("content://") == true) {
                    val attachmentUri = ContentUris.withAppendedId(JtxContract.JtxAttachment.CONTENT_URI.asSyncAdapter(collection.account), attachment.attachmentId)
                    val attachmentFile = collection.client.openFile(attachmentUri, "r")
                    val attachmentBytes = ByteBuffer.wrap(ParcelFileDescriptor.AutoCloseInputStream(attachmentFile).readBytes())
                    val att = Attach(attachmentBytes).apply {
                        attachment.fmttype?.let { this += FmtType(it) }
                        attachment.filename?.let {
                            this += XParameter(X_PARAM_ATTACH_LABEL, it)
                            this += XParameter(X_PARAM_FILENAME, it)
                        }
                    }
                    props += att
                } else {
                    attachment.uri?.let { uri ->
                        val att = Attach(URI(uri)).apply {
                            attachment.fmttype?.let { this += FmtType(it) }
                            attachment.filename?.let {
                                this += XParameter(X_PARAM_ATTACH_LABEL, it)
                                this += XParameter(X_PARAM_FILENAME, it)
                            }
                        }
                        props += att
                    }
                }
            } catch (e: FileNotFoundException) {
                logger.log(Level.WARNING, "File not found at the given Uri: ${attachment.uri}", e)
            } catch (e: NullPointerException) {
                logger.log(Level.WARNING, "Provided Uri was empty: ${attachment.uri}", e)
            } catch (e: IllegalArgumentException) {
                logger.log(Level.WARNING, "Uri could not be parsed: ${attachment.uri}", e)
            }
        }

        unknown.forEach {
            it.value?.let {  jsonString ->
                props += UnknownProperty.fromJsonString(jsonString)
            }
        }

        relatedTo.forEach {
            val param: Parameter =
                when (it.reltype) {
                    RelType.CHILD.value -> RelType.CHILD
                    RelType.SIBLING.value -> RelType.SIBLING
                    RelType.PARENT.value -> RelType.PARENT
                    else -> return@forEach
                }
            val parameterList = ParameterList().add(param)
            props += net.fortuna.ical4j.model.property.RelatedTo(parameterList, it.text)
        }

        dtstart?.let {
            val instant = Instant.ofEpochMilli(it)
            props += DtStart(when {
                dtstartTimezone == TZ_ALLDAY ->
                    instant.toLocalDate()
                dtstartTimezone == ZoneOffset.UTC.id ->
                    instant.atZone(ZoneOffset.UTC)
                dtstartTimezone.isNullOrEmpty() ->
                    instant.atZone(ZoneId.systemDefault()).toLocalDateTime()
                else ->
                    instant.atZone(ZoneId.of(dtstartTimezone))
            })
        }

        rrule?.let { rrule ->
            props += RRule<Temporal>(rrule)
        }
        recurid?.let { recurid ->
            val parameterList = mutableListOf<Parameter>()
            if (recuridTimezone == TZ_ALLDAY)
                parameterList.add(Value.DATE)
            else if (!recuridTimezone.isNullOrEmpty())
                parameterList.add(TzId(recuridTimezone))
            props += RecurrenceId<Temporal>(ParameterList(parameterList), recurid)
        }

        rdate?.let { rdateString ->
            val rdates: MutableList<Long> = JtxContract.getLongListFromString(rdateString)
            props += RDate(when {
                dtstartTimezone == TZ_ALLDAY ->
                    DateList<LocalDate>(rdates.map {
                        LocalDate.ofInstant(Instant.ofEpochMilli(it), ZoneOffset.UTC)
                    })
                dtstartTimezone == ZoneOffset.UTC.id ->
                    DateList<ZonedDateTime>(rdates.map {
                        ZonedDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneOffset.UTC)
                    })
                dtstartTimezone.isNullOrEmpty() ->
                    DateList<LocalDateTime>(rdates.map {
                        LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault())
                    })
                else ->
                    DateList<ZonedDateTime>(rdates.map {
                        ZonedDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.of(dtstartTimezone))
                    })
            })
        }

        exdate?.let { exdateString ->
            val exdates: MutableList<Long> = JtxContract.getLongListFromString(exdateString)
            val dateList = when {
                dtstartTimezone == TZ_ALLDAY ->
                    DateList<LocalDate>(exdates.map {
                        LocalDate.ofInstant(Instant.ofEpochMilli(it), ZoneOffset.UTC)
                    })
                dtstartTimezone == ZoneOffset.UTC.id ->
                    DateList<ZonedDateTime>(exdates.map {
                        ZonedDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneOffset.UTC)
                    })
                dtstartTimezone.isNullOrEmpty() ->
                    DateList<LocalDateTime>(exdates.map {
                        LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault())
                    })
                else ->
                    DateList<ZonedDateTime>(exdates.map {
                        ZonedDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.of(dtstartTimezone))
                    })
            }

            props += if (dtstartTimezone == TZ_ALLDAY) {
                ExDate(ParameterList(listOf(Value.DATE)), dateList)
            } else {
                ExDate(dateList)
            }
        }

        duration?.let {
            val dur = Duration()
            dur.value = it
            props += dur
        }


        /*
// remember used time zones
val usedTimeZones = HashSet<TimeZone>()
duration?.let(props::add)
*/


        if(component == JtxContract.JtxICalObject.Component.VTODO.name) {
            completed?.let {
                // Completed is UNIX timestamp (milliseconds). But the X_PROP_COMPLETEDTIMEZONE can still define a timezone
                props += Completed(Instant.ofEpochMilli(it))

                // only take completedTimezone if completed time is set
                completedTimezone?.let { complTZ ->
                    props += XProperty(X_PROP_COMPLETEDTIMEZONE, complTZ)
                }
            }

            percent?.let {
                props += PercentComplete(it)
            }


            if (priority != null && priority != ImmutablePriority.UNDEFINED.level)
                priority?.let {
                    props += Priority(it)
                }

            due?.let {
                val instant = Instant.ofEpochMilli(it)
                props += Due(when {
                    dtstartTimezone == TZ_ALLDAY ->
                        instant.toLocalDate()
                    dtstartTimezone == ZoneOffset.UTC.id ->
                        instant.atZone(ZoneOffset.UTC)
                    dtstartTimezone.isNullOrEmpty() ->
                        instant.atZone(ZoneId.systemDefault()).toLocalDateTime()
                    else ->
                        instant.atZone(ZoneId.of(dtstartTimezone))
                })
            }
        }

        // Add properties to PropertyList
        return propertyList.addAll(props) // the list is immutable and "addAll" returns a copy which we need to return
    }

        /*
    // determine earliest referenced date
    val earliest = arrayOf(
        dtStart?.date,
        due?.date,
        completedAt?.date
    ).filterNotNull().min()
    // add VTIMEZONE components
    for (tz in usedTimeZones)
        ical.components += ICalendar.minifyVTimeZone(tz.vTimeZone, earliest)
    } */


    fun prepareForUpload(): String {
        return "${this.uid}.ics"
    }

    /**
     * Updates the fileName, eTag and scheduleTag of the current JtxICalObject
     */
    fun clearDirty(fileName: String?, eTag: String?, scheduleTag: String?) {

        var updateUri = JtxContract.JtxICalObject.CONTENT_URI.asSyncAdapter(collection.account)
        updateUri = Uri.withAppendedPath(updateUri, this.id.toString())

        val values = ContentValues()
        fileName?.let { values.put(JtxContract.JtxICalObject.FILENAME, fileName) }
        eTag?.let { values.put(JtxContract.JtxICalObject.ETAG, eTag) }
        scheduleTag?.let { values.put(JtxContract.JtxICalObject.SCHEDULETAG, scheduleTag) }
        values.put(JtxContract.JtxICalObject.DIRTY, false)

        collection.client.update(updateUri, values, null, null)
    }

    /**
     * Updates the flags of the current JtxICalObject
     * @param [flags] to be set as [Int]
     */
    fun updateFlags(flags: Int) {
        var updateUri = JtxContract.JtxICalObject.CONTENT_URI.asSyncAdapter(collection.account)
        updateUri = Uri.withAppendedPath(updateUri, this.id.toString())

        val values = ContentValues(1)
        values.put(JtxContract.JtxICalObject.FLAGS, flags)
        collection.client.update(updateUri, values, null, null)
        this.flags = flags
    }

    /**
     * adds the current JtxICalObject in the jtx DB through the provider
     * @return the Content [Uri] of the inserted object
     */
    fun add(): Uri {
        val values = this.toContentValues()

        val newUri = collection.client.insert(
            JtxContract.JtxICalObject.CONTENT_URI.asSyncAdapter(collection.account),
            values
        ) ?: return Uri.EMPTY
        this.id = newUri.lastPathSegment?.toLong() ?: return Uri.EMPTY

        insertOrUpdateListProperties(false)

        return newUri
    }

    /**
     * Updates the current JtxICalObject with the given data
     * @param [data] The JtxICalObject with the information that should be applied to this object and updated in the provider
     * @return [Uri] of the updated entry
     */
    fun update(data: JtxICalObject): Uri {
        this.applyNewData(data)
        val values = this.toContentValues()

        var updateUri = JtxContract.JtxICalObject.CONTENT_URI.asSyncAdapter(collection.account)
        updateUri = Uri.withAppendedPath(updateUri, this.id.toString())
        collection.client.update(
            updateUri,
            values,
            "${JtxContract.JtxICalObject.ID} = ?",
            arrayOf(this.id.toString())
        )

        insertOrUpdateListProperties(true)

        return updateUri
    }


    /**
     * This function takes care of all list properties and inserts them in the DB through the provider
     * @param isUpdate if true then the list properties are deleted through the provider before they are inserted
     */
    private fun insertOrUpdateListProperties(isUpdate: Boolean) {

        // delete the categories, attendees, ... and insert them again after. Only relevant for Update, for an insert there will be no entries
        if (isUpdate) {
            val deleteBatch = JtxBatchOperation(collection.client)

            deleteBatch += BatchOperation.CpoBuilder
                .newDelete(JtxContract.JtxCategory.CONTENT_URI.asSyncAdapter(collection.account))
                .withSelection("${JtxContract.JtxCategory.ICALOBJECT_ID} = ?", arrayOf(this.id.toString()))

            deleteBatch += BatchOperation.CpoBuilder
                .newDelete(JtxContract.JtxComment.CONTENT_URI.asSyncAdapter(collection.account))
                .withSelection("${JtxContract.JtxComment.ICALOBJECT_ID} = ?", arrayOf(this.id.toString()))

            deleteBatch += BatchOperation.CpoBuilder
                .newDelete(JtxContract.JtxResource.CONTENT_URI.asSyncAdapter(collection.account))
                .withSelection("${JtxContract.JtxResource.ICALOBJECT_ID} = ?", arrayOf(this.id.toString()))

            deleteBatch += BatchOperation.CpoBuilder
                .newDelete(JtxContract.JtxRelatedto.CONTENT_URI.asSyncAdapter(collection.account))
                .withSelection("${JtxContract.JtxRelatedto.ICALOBJECT_ID} = ?", arrayOf(this.id.toString()))

            deleteBatch += BatchOperation.CpoBuilder
                .newDelete(JtxContract.JtxAttendee.CONTENT_URI.asSyncAdapter(collection.account))
                .withSelection("${JtxContract.JtxAttendee.ICALOBJECT_ID} = ?", arrayOf(this.id.toString()))

            deleteBatch += BatchOperation.CpoBuilder
                .newDelete(JtxContract.JtxOrganizer.CONTENT_URI.asSyncAdapter(collection.account))
                .withSelection("${JtxContract.JtxOrganizer.ICALOBJECT_ID} = ?", arrayOf(this.id.toString()))

            deleteBatch += BatchOperation.CpoBuilder
                .newDelete(JtxContract.JtxAttachment.CONTENT_URI.asSyncAdapter(collection.account))
                .withSelection("${JtxContract.JtxAttachment.ICALOBJECT_ID} = ?", arrayOf(this.id.toString()))

            deleteBatch += BatchOperation.CpoBuilder
                .newDelete(JtxContract.JtxAlarm.CONTENT_URI.asSyncAdapter(collection.account))
                .withSelection("${JtxContract.JtxAlarm.ICALOBJECT_ID} = ?", arrayOf(this.id.toString()))

            deleteBatch += BatchOperation.CpoBuilder
                .newDelete(JtxContract.JtxUnknown.CONTENT_URI.asSyncAdapter(collection.account))
                .withSelection("${JtxContract.JtxUnknown.ICALOBJECT_ID} = ?", arrayOf(this.id.toString()))

            deleteBatch.commit()
        }

        val insertBatch = JtxBatchOperation(collection.client)

        this.categories.forEach { category ->
            insertBatch += BatchOperation.CpoBuilder
                .newInsert(JtxContract.JtxCategory.CONTENT_URI.asSyncAdapter(collection.account))
                .withValue(JtxContract.JtxCategory.ICALOBJECT_ID, id)
                .withValue(JtxContract.JtxCategory.TEXT, category.text)
                .withValue(JtxContract.JtxCategory.ID, category.categoryId)
                .withValue(JtxContract.JtxCategory.LANGUAGE, category.language)
                .withValue(JtxContract.JtxCategory.OTHER, category.other)
        }

        this.comments.forEach { comment ->
            insertBatch += BatchOperation.CpoBuilder
                .newInsert(JtxContract.JtxComment.CONTENT_URI.asSyncAdapter(collection.account))
                .withValue(JtxContract.JtxComment.ICALOBJECT_ID, id)
                .withValue(JtxContract.JtxComment.ID, comment.commentId)
                .withValue(JtxContract.JtxComment.TEXT, comment.text)
                .withValue(JtxContract.JtxComment.LANGUAGE, comment.language)
                .withValue(JtxContract.JtxComment.OTHER, comment.other)
        }


        this.resources.forEach { resource ->
            insertBatch += BatchOperation.CpoBuilder
                .newInsert(JtxContract.JtxResource.CONTENT_URI.asSyncAdapter(collection.account))
                .withValue(JtxContract.JtxResource.ICALOBJECT_ID, id)
                .withValue(JtxContract.JtxResource.ID, resource.resourceId)
                .withValue(JtxContract.JtxResource.TEXT, resource.text)
                .withValue(JtxContract.JtxResource.LANGUAGE, resource.language)
                .withValue(JtxContract.JtxResource.OTHER, resource.other)
        }

        this.relatedTo.forEach { related ->
            insertBatch += BatchOperation.CpoBuilder
                .newInsert(JtxContract.JtxRelatedto.CONTENT_URI.asSyncAdapter(collection.account))
                .withValue(JtxContract.JtxRelatedto.ICALOBJECT_ID, id)
                .withValue(JtxContract.JtxRelatedto.TEXT, related.text)
                .withValue(JtxContract.JtxRelatedto.RELTYPE, related.reltype)
                .withValue(JtxContract.JtxRelatedto.OTHER, related.other)
        }

        this.attendees.forEach { attendee ->
            insertBatch += BatchOperation.CpoBuilder
                .newInsert(JtxContract.JtxAttendee.CONTENT_URI.asSyncAdapter(collection.account))
                .withValue(JtxContract.JtxAttendee.ICALOBJECT_ID, id)
                .withValue(JtxContract.JtxAttendee.CALADDRESS, attendee.caladdress)
                .withValue(JtxContract.JtxAttendee.CN, attendee.cn)
                .withValue(JtxContract.JtxAttendee.CUTYPE, attendee.cutype)
                .withValue(JtxContract.JtxAttendee.DELEGATEDFROM, attendee.delegatedfrom)
                .withValue(JtxContract.JtxAttendee.DELEGATEDTO, attendee.delegatedto)
                .withValue(JtxContract.JtxAttendee.DIR, attendee.dir)
                .withValue(JtxContract.JtxAttendee.LANGUAGE, attendee.language)
                .withValue(JtxContract.JtxAttendee.MEMBER, attendee.member)
                .withValue(JtxContract.JtxAttendee.PARTSTAT, attendee.partstat)
                .withValue(JtxContract.JtxAttendee.ROLE, attendee.role)
                .withValue(JtxContract.JtxAttendee.RSVP, attendee.rsvp)
                .withValue(JtxContract.JtxAttendee.SENTBY, attendee.sentby)
                .withValue(JtxContract.JtxAttendee.OTHER, attendee.other)
        }

        this.organizer?.let { organizer ->
            insertBatch += BatchOperation.CpoBuilder
                .newInsert(JtxContract.JtxOrganizer.CONTENT_URI.asSyncAdapter(collection.account))
                .withValue(JtxContract.JtxOrganizer.ICALOBJECT_ID, id)
                .withValue(JtxContract.JtxOrganizer.CALADDRESS, organizer.caladdress)
                .withValue(JtxContract.JtxOrganizer.CN, organizer.cn)
                .withValue(JtxContract.JtxOrganizer.DIR, organizer.dir)
                .withValue(JtxContract.JtxOrganizer.LANGUAGE, organizer.language)
                .withValue(JtxContract.JtxOrganizer.SENTBY, organizer.sentby)
                .withValue(JtxContract.JtxOrganizer.OTHER, organizer.other)
        }

        this.attachments.forEach { attachment ->
            val attachmentContentValues = contentValuesOf(
                JtxContract.JtxAttachment.ICALOBJECT_ID to id,
                JtxContract.JtxAttachment.URI to attachment.uri,
                JtxContract.JtxAttachment.FMTTYPE to attachment.fmttype,
                JtxContract.JtxAttachment.OTHER to attachment.other,
                JtxContract.JtxAttachment.FILENAME to attachment.filename
            )
            val newAttachment = collection.client.insert(JtxContract.JtxAttachment.CONTENT_URI.asSyncAdapter(collection.account), attachmentContentValues)
            if(attachment.uri.isNullOrEmpty() && newAttachment != null) {
                val attachmentPFD = collection.client.openFile(newAttachment, "w")
                ParcelFileDescriptor.AutoCloseOutputStream(attachmentPFD).write(Base64.decode(attachment.binary, Base64.DEFAULT))
            }
        }

        this.alarms.forEach { alarm ->
            insertBatch += BatchOperation.CpoBuilder
                .newInsert(JtxContract.JtxAlarm.CONTENT_URI.asSyncAdapter(collection.account))
                .withValue(JtxContract.JtxAlarm.ICALOBJECT_ID, id)
                .withValue(JtxContract.JtxAlarm.ACTION, alarm.action)
                .withValue(JtxContract.JtxAlarm.ATTACH, alarm.attach)
                //.withValue(JtxContract.JtxAlarm.ATTENDEE, alarm.attendee)
                .withValue(JtxContract.JtxAlarm.DESCRIPTION, alarm.description)
                .withValue(JtxContract.JtxAlarm.DURATION, alarm.duration)
                .withValue(JtxContract.JtxAlarm.REPEAT, alarm.repeat)
                .withValue(JtxContract.JtxAlarm.SUMMARY, alarm.summary)
                .withValue(JtxContract.JtxAlarm.TRIGGER_RELATIVE_TO, alarm.triggerRelativeTo)
                .withValue(JtxContract.JtxAlarm.TRIGGER_RELATIVE_DURATION, alarm.triggerRelativeDuration)
                .withValue(JtxContract.JtxAlarm.TRIGGER_TIME, alarm.triggerTime)
                .withValue(JtxContract.JtxAlarm.TRIGGER_TIMEZONE, alarm.triggerTimezone)
                .withValue(JtxContract.JtxAlarm.OTHER, alarm.other)
        }

        this.unknown.forEach { unknown ->
            insertBatch += BatchOperation.CpoBuilder
                .newInsert(JtxContract.JtxUnknown.CONTENT_URI.asSyncAdapter(collection.account))
                .withValue(JtxContract.JtxUnknown.ICALOBJECT_ID, id)
                .withValue(JtxContract.JtxUnknown.UNKNOWN_VALUE, unknown.value)
        }

        insertBatch.commit()
    }

    /**
     * Deletes the current JtxICalObject
     * @return The number of deleted records (should always be 1)
     */
    fun delete(): Int {
        val uri = Uri.withAppendedPath(
            JtxContract.JtxICalObject.CONTENT_URI.asSyncAdapter(collection.account),
            id.toString()
        )
        return collection.client.delete(uri, null, null)
    }


    /**
     * This function is used for empty JtxICalObjects that need new data applied, usually a LocalJtxICalObject.
     * @param [newData], the (local) JtxICalObject that should be mapped onto the given JtxICalObject
     */
    fun applyNewData(newData: JtxICalObject) {

        this.component = newData.component
        this.sequence = newData.sequence
        this.created = newData.created
        this.lastModified = newData.lastModified
        this.summary = newData.summary
        this.description = newData.description
        this.uid = newData.uid

        this.location = newData.location
        this.locationAltrep = newData.locationAltrep
        this.geoLat = newData.geoLat
        this.geoLong = newData.geoLong
        this.geofenceRadius = newData.geofenceRadius
        this.percent = newData.percent
        this.classification = newData.classification
        this.status = newData.status
        this.xstatus = newData.xstatus
        this.priority = newData.priority
        this.color = newData.color
        this.url = newData.url
        this.contact = newData.contact

        this.dtstart = newData.dtstart
        this.dtstartTimezone = newData.dtstartTimezone
        this.dtend = newData.dtend
        this.dtendTimezone = newData.dtendTimezone
        this.completed = newData.completed
        this.completedTimezone = newData.completedTimezone
        this.due = newData.due
        this.dueTimezone = newData.dueTimezone
        this.duration = newData.duration

        this.rrule = newData.rrule
        this.rdate = newData.rdate
        this.exdate = newData.exdate
        this.recurid = newData.recurid
        this.recuridTimezone = newData.recuridTimezone


        this.categories = newData.categories
        this.comments = newData.comments
        this.resources = newData.resources
        this.relatedTo = newData.relatedTo
        this.attendees = newData.attendees
        this.organizer = newData.organizer
        this.attachments = newData.attachments
        this.alarms = newData.alarms
        this.unknown = newData.unknown
    }

    /**
     * Takes Content Values, applies them on the current JtxICalObject and retrieves all further list properties from the content provier and adds them.
     * @param [values] The Content Values with the information about the JtxICalObject
     */
    fun populateFromContentValues(values: ContentValues) {
        values.getAsLong(JtxContract.JtxICalObject.ID)?.let { id -> this.id = id }

        values.getAsString(JtxContract.JtxICalObject.COMPONENT)?.let { component -> this.component = component }
        values.getAsString(JtxContract.JtxICalObject.SUMMARY)?.let { summary -> this.summary = summary }
        values.getAsString(JtxContract.JtxICalObject.DESCRIPTION)?.let { description -> this.description = description }
        values.getAsLong(JtxContract.JtxICalObject.DTSTART)?.let { dtstart -> this.dtstart = dtstart }
        values.getAsString(JtxContract.JtxICalObject.DTSTART_TIMEZONE)?.let { dtstartTimezone -> this.dtstartTimezone = dtstartTimezone }
        values.getAsLong(JtxContract.JtxICalObject.DTEND)?.let { dtend -> this.dtend = dtend }
        values.getAsString(JtxContract.JtxICalObject.DTEND_TIMEZONE)?.let { dtendTimezone -> this.dtendTimezone = dtendTimezone }
        values.getAsString(JtxContract.JtxICalObject.STATUS)?.let { status -> this.status = status }
        values.getAsString(JtxContract.JtxICalObject.EXTENDED_STATUS)?.let { xstatus -> this.xstatus = xstatus }
        values.getAsString(JtxContract.JtxICalObject.CLASSIFICATION)?.let { classification -> this.classification = classification }
        values.getAsString(JtxContract.JtxICalObject.URL)?.let { url -> this.url = url }
        values.getAsString(JtxContract.JtxICalObject.CONTACT)?.let { contact -> this.contact = contact }
        values.getAsDouble(JtxContract.JtxICalObject.GEO_LAT)?.let { geoLat -> this.geoLat = geoLat }
        values.getAsDouble(JtxContract.JtxICalObject.GEO_LONG)?.let { geoLong -> this.geoLong = geoLong }
        values.getAsString(JtxContract.JtxICalObject.LOCATION)?.let { location -> this.location = location }
        values.getAsString(JtxContract.JtxICalObject.LOCATION_ALTREP)?.let { locationAltrep -> this.locationAltrep = locationAltrep }
        values.getAsInteger(JtxContract.JtxICalObject.GEOFENCE_RADIUS)?.let { geofenceRadius -> this.geofenceRadius = geofenceRadius  }
        values.getAsInteger(JtxContract.JtxICalObject.PERCENT)?.let { percent -> this.percent = percent }
        values.getAsInteger(JtxContract.JtxICalObject.PRIORITY)?.let { priority -> this.priority = priority }
        values.getAsLong(JtxContract.JtxICalObject.DUE)?.let { due -> this.due = due }
        values.getAsString(JtxContract.JtxICalObject.DUE_TIMEZONE)?.let { dueTimezone -> this.dueTimezone = dueTimezone }
        values.getAsLong(JtxContract.JtxICalObject.COMPLETED)?.let { completed -> this.completed = completed }
        values.getAsString(JtxContract.JtxICalObject.COMPLETED_TIMEZONE)?.let { completedTimezone -> this.completedTimezone = completedTimezone }
        values.getAsString(JtxContract.JtxICalObject.DURATION)?.let { duration -> this.duration = duration }
        values.getAsString(JtxContract.JtxICalObject.UID)?.let { uid -> this.uid = uid }
        values.getAsLong(JtxContract.JtxICalObject.CREATED)?.let { created -> this.created = created }
        values.getAsLong(JtxContract.JtxICalObject.DTSTAMP)?.let { dtstamp -> this.dtstamp = dtstamp }
        values.getAsLong(JtxContract.JtxICalObject.LAST_MODIFIED)?.let { lastModified -> this.lastModified = lastModified }
        values.getAsLong(JtxContract.JtxICalObject.SEQUENCE)?.let { sequence -> this.sequence = sequence }
        values.getAsInteger(JtxContract.JtxICalObject.COLOR)?.let { color -> this.color = color }

        values.getAsString(JtxContract.JtxICalObject.RRULE)?.let { rrule -> this.rrule = rrule }
        values.getAsString(JtxContract.JtxICalObject.EXDATE)?.let { exdate -> this.exdate = exdate }
        values.getAsString(JtxContract.JtxICalObject.RDATE)?.let { rdate -> this.rdate = rdate }
        values.getAsString(JtxContract.JtxICalObject.RECURID)?.let { recurid -> this.recurid = recurid }
        values.getAsString(JtxContract.JtxICalObject.RECURID_TIMEZONE)?.let { recuridTimezone -> this.recuridTimezone = recuridTimezone }

        this.collectionId = collection.id
        values.getAsString(JtxContract.JtxICalObject.DIRTY)?.let { dirty -> this.dirty = dirty == "1" || dirty == "true" }
        values.getAsString(JtxContract.JtxICalObject.DELETED)?.let { deleted -> this.deleted = deleted == "1" || deleted == "true" }

        values.getAsString(JtxContract.JtxICalObject.FILENAME)?.let { fileName -> this.fileName = fileName }
        values.getAsString(JtxContract.JtxICalObject.ETAG)?.let { eTag -> this.eTag = eTag }
        values.getAsString(JtxContract.JtxICalObject.SCHEDULETAG)?.let { scheduleTag -> this.scheduleTag = scheduleTag }
        values.getAsInteger(JtxContract.JtxICalObject.FLAGS)?.let { flags -> this.flags = flags }


        // Take care of categories
        getAsContentValues(
            uri = JtxContract.JtxCategory.CONTENT_URI.asSyncAdapter(collection.account),
            selection = "${JtxContract.JtxCategory.ICALOBJECT_ID} = ?",
            selectionArgs = arrayOf(this.id.toString())
        ).forEach { catValues ->
            val category = Category().apply {
                catValues.getAsLong(JtxContract.JtxCategory.ID)?.let { id -> this.categoryId = id }
                catValues.getAsString(JtxContract.JtxCategory.TEXT)?.let { text -> this.text = text }
                catValues.getAsString(JtxContract.JtxCategory.LANGUAGE)?.let { language -> this.language = language }
                catValues.getAsString(JtxContract.JtxCategory.OTHER)?.let { other -> this.other = other }
            }
            categories.add(category)
        }

        // Take care of comments
        getAsContentValues(
            uri = JtxContract.JtxComment.CONTENT_URI.asSyncAdapter(collection.account),
            selection = "${JtxContract.JtxComment.ICALOBJECT_ID} = ?",
            selectionArgs = arrayOf(this.id.toString())
        ).forEach { commentValues ->
            val comment = Comment().apply {
                commentValues.getAsLong(JtxContract.JtxComment.ID)?.let { id -> this.commentId = id }
                commentValues.getAsString(JtxContract.JtxComment.TEXT)?.let { text -> this.text = text }
                commentValues.getAsString(JtxContract.JtxComment.LANGUAGE)?.let { language -> this.language = language }
                commentValues.getAsString(JtxContract.JtxComment.OTHER)?.let { other -> this.other = other }
            }
            comments.add(comment)
        }

        // Take care of resources
        getAsContentValues(
            uri = JtxContract.JtxResource.CONTENT_URI.asSyncAdapter(collection.account),
            selection = "${JtxContract.JtxResource.ICALOBJECT_ID} = ?",
            selectionArgs = arrayOf(this.id.toString())
        ).forEach { resourceValues ->
            val resource = Resource().apply {
                resourceValues.getAsLong(JtxContract.JtxResource.ID)?.let { id -> this.resourceId = id }
                resourceValues.getAsString(JtxContract.JtxResource.TEXT)?.let { text -> this.text = text }
                resourceValues.getAsString(JtxContract.JtxResource.LANGUAGE)?.let { language -> this.language = language }
                resourceValues.getAsString(JtxContract.JtxResource.OTHER)?.let { other -> this.other = other }
            }
            resources.add(resource)
        }


        // Take care of related-to
        getAsContentValues(
            uri = JtxContract.JtxRelatedto.CONTENT_URI.asSyncAdapter(collection.account),
            selection = "${JtxContract.JtxRelatedto.ICALOBJECT_ID} = ? AND ${JtxContract.JtxRelatedto.RELTYPE} = ?",
            selectionArgs = arrayOf(this.id.toString(), JtxContract.JtxRelatedto.Reltype.PARENT.name)
        ).forEach { relatedToValues ->
            val relTo = RelatedTo().apply {
                relatedToValues.getAsLong(JtxContract.JtxRelatedto.ID)?.let { id -> this.relatedtoId = id }
                relatedToValues.getAsString(JtxContract.JtxRelatedto.TEXT)?.let { text -> this.text = text }
                relatedToValues.getAsString(JtxContract.JtxRelatedto.RELTYPE)?.let { reltype -> this.reltype = reltype }
                relatedToValues.getAsString(JtxContract.JtxRelatedto.OTHER)?.let { other -> this.other = other }

            }
            relatedTo.add(relTo)
        }

        // Take care of attendees
        getAsContentValues(
            uri = JtxContract.JtxAttendee.CONTENT_URI.asSyncAdapter(collection.account),
            selection = "${JtxContract.JtxAttendee.ICALOBJECT_ID} = ?",
            selectionArgs = arrayOf(this.id.toString())
        ).forEach { attendeeValues ->
            val attendee = Attendee().apply {
                attendeeValues.getAsLong(JtxContract.JtxAttendee.ID)?.let { id -> this.attendeeId = id }
                attendeeValues.getAsString(JtxContract.JtxAttendee.CALADDRESS)?.let { caladdress -> this.caladdress = caladdress }
                attendeeValues.getAsString(JtxContract.JtxAttendee.CUTYPE)?.let { cutype -> this.cutype = cutype }
                attendeeValues.getAsString(JtxContract.JtxAttendee.MEMBER)?.let { member -> this.member = member }
                attendeeValues.getAsString(JtxContract.JtxAttendee.ROLE)?.let { role -> this.role = role }
                attendeeValues.getAsString(JtxContract.JtxAttendee.PARTSTAT)?.let { partstat -> this.partstat = partstat }
                attendeeValues.getAsString(JtxContract.JtxAttendee.RSVP)?.let { rsvp -> this.rsvp = rsvp == "1" }
                attendeeValues.getAsString(JtxContract.JtxAttendee.DELEGATEDTO)?.let { delto -> this.delegatedto = delto }
                attendeeValues.getAsString(JtxContract.JtxAttendee.DELEGATEDFROM)?.let { delfrom -> this.delegatedfrom = delfrom }
                attendeeValues.getAsString(JtxContract.JtxAttendee.SENTBY)?.let { sentby -> this.sentby = sentby }
                attendeeValues.getAsString(JtxContract.JtxAttendee.CN)?.let { cn -> this.cn = cn }
                attendeeValues.getAsString(JtxContract.JtxAttendee.DIR)?.let { dir -> this.dir = dir }
                attendeeValues.getAsString(JtxContract.JtxAttendee.LANGUAGE)?.let { lang -> this.language = lang }
                attendeeValues.getAsString(JtxContract.JtxAttendee.OTHER)?.let { other -> this.other = other }
            }
            attendees.add(attendee)
        }

        // Take care of organizer
        getAsContentValues(
            uri = JtxContract.JtxOrganizer.CONTENT_URI.asSyncAdapter(collection.account),
            selection = "${JtxContract.JtxOrganizer.ICALOBJECT_ID} = ?",
            selectionArgs = arrayOf(this.id.toString())
        ).firstOrNull()?.let { organizerContentValues ->
            val orgnzr = Organizer().apply {
                organizerId = organizerContentValues.getAsLong(JtxContract.JtxOrganizer.ID) ?: 0L
                caladdress = organizerContentValues.getAsString(JtxContract.JtxOrganizer.CALADDRESS)
                sentby = organizerContentValues.getAsString(JtxContract.JtxOrganizer.SENTBY)
                cn = organizerContentValues.getAsString(JtxContract.JtxOrganizer.CN)
                dir = organizerContentValues.getAsString(JtxContract.JtxOrganizer.DIR)
                language = organizerContentValues.getAsString(JtxContract.JtxOrganizer.LANGUAGE)
                other = organizerContentValues.getAsString(JtxContract.JtxOrganizer.OTHER)
            }
            if(orgnzr.caladdress?.isNotEmpty() == true)   // we only take the organizer if there was a caladdress (otherwise an empty ORGANIZER is created)
                organizer = orgnzr
        }

        // Take care of attachments
        getAsContentValues(
            uri = JtxContract.JtxAttachment.CONTENT_URI.asSyncAdapter(collection.account),
            selection = "${JtxContract.JtxAttachment.ICALOBJECT_ID} = ?",
            selectionArgs = arrayOf(this.id.toString())
        ).forEach { attachmentValues ->
            val attachment = Attachment().apply {
                attachmentValues.getAsLong(JtxContract.JtxAttachment.ID)?.let { id -> this.attachmentId = id }
                attachmentValues.getAsString(JtxContract.JtxAttachment.URI)?.let { uri -> this.uri = uri }
                attachmentValues.getAsString(JtxContract.JtxAttachment.BINARY)?.let { value -> this.binary = value }
                attachmentValues.getAsString(JtxContract.JtxAttachment.FMTTYPE)?.let { fmttype -> this.fmttype = fmttype }
                attachmentValues.getAsString(JtxContract.JtxAttachment.OTHER)?.let { other -> this.other = other }
                attachmentValues.getAsString(JtxContract.JtxAttachment.FILENAME)?.let { filename -> this.filename = filename }
            }
            attachments.add(attachment)
        }

        // Take care of alarms
        getAsContentValues(
            uri = JtxContract.JtxAlarm.CONTENT_URI.asSyncAdapter(collection.account),
            selection = "${JtxContract.JtxAlarm.ICALOBJECT_ID} = ?",
            selectionArgs = arrayOf(this.id.toString())
        ).forEach { alarmValues ->
            val alarm = Alarm().apply {
                alarmValues.getAsLong(JtxContract.JtxAlarm.ID)?.let { id -> this.alarmId = id }
                alarmValues.getAsString(JtxContract.JtxAlarm.ACTION)?.let { action -> this.action = action }
                alarmValues.getAsString(JtxContract.JtxAlarm.DESCRIPTION)?.let { desc -> this.description = desc }
                alarmValues.getAsLong(JtxContract.JtxAlarm.TRIGGER_TIME)?.let { time -> this.triggerTime = time }
                alarmValues.getAsString(JtxContract.JtxAlarm.TRIGGER_TIMEZONE)?.let { tz -> this.triggerTimezone = tz }
                alarmValues.getAsString(JtxContract.JtxAlarm.TRIGGER_RELATIVE_TO)?.let { relative -> this.triggerRelativeTo = relative }
                alarmValues.getAsString(JtxContract.JtxAlarm.TRIGGER_RELATIVE_DURATION)?.let { duration -> this.triggerRelativeDuration = duration }
                alarmValues.getAsString(JtxContract.JtxAlarm.SUMMARY)?.let { summary -> this.summary = summary }
                alarmValues.getAsString(JtxContract.JtxAlarm.DURATION)?.let { dur -> this.duration = dur }
                alarmValues.getAsString(JtxContract.JtxAlarm.REPEAT)?.let { repeat -> this.repeat = repeat }
                alarmValues.getAsString(JtxContract.JtxAlarm.ATTACH)?.let { attach -> this.attach = attach }
                alarmValues.getAsString(JtxContract.JtxAlarm.OTHER)?.let { other -> this.other = other }
            }
            alarms.add(alarm)
        }


        // Take care of unknown properties
        getAsContentValues(
            uri = JtxContract.JtxUnknown.CONTENT_URI.asSyncAdapter(collection.account),
            selection = "${JtxContract.JtxUnknown.ICALOBJECT_ID} = ?",
            selectionArgs = arrayOf(this.id.toString()),
        ).forEach { unknownValues ->
            val unknwn = Unknown().apply {
                unknownValues.getAsLong(JtxContract.JtxUnknown.ID)?.let { id -> this.unknownId = id }
                unknownValues.getAsString(JtxContract.JtxUnknown.UNKNOWN_VALUE)?.let { value -> this.value = value }
            }
            unknown.add(unknwn)
        }


        if(rrule?.isNotEmpty() == true) {
            getAsContentValues(
                uri = JtxContract.JtxICalObject.CONTENT_URI.asSyncAdapter(collection.account),
                selection = "${JtxContract.JtxICalObject.UID} = ? AND ${JtxContract.JtxICalObject.RECURID} IS NOT NULL AND ${JtxContract.JtxICalObject.SEQUENCE} > 0",
                selectionArgs = arrayOf(uid)
            ).forEach { recurInstanceValues ->
                recurInstances.add(
                    JtxICalObject(collection).apply { populateFromContentValues(recurInstanceValues) }
                )
            }
        }
    }

    /**
     * Puts the current JtxICalObjects attributes into Content Values
     * @return The JtxICalObject attributes as [ContentValues] (exluding list properties)
     */
    private fun toContentValues() = contentValuesOf(
        JtxContract.JtxICalObject.ID to id,
        JtxContract.JtxICalObject.SUMMARY to summary,
        JtxContract.JtxICalObject.DESCRIPTION to description,
        JtxContract.JtxICalObject.COMPONENT to component,
        JtxContract.JtxICalObject.STATUS to status,
        JtxContract.JtxICalObject.EXTENDED_STATUS to xstatus,
        JtxContract.JtxICalObject.CLASSIFICATION to classification,
        JtxContract.JtxICalObject.PRIORITY to priority,
        JtxContract.JtxICalObject.ICALOBJECT_COLLECTIONID to collectionId,
        JtxContract.JtxICalObject.UID to uid,
        JtxContract.JtxICalObject.COLOR to color,
        JtxContract.JtxICalObject.URL to url,
        JtxContract.JtxICalObject.CONTACT to contact,
        JtxContract.JtxICalObject.GEO_LAT to geoLat,
        JtxContract.JtxICalObject.GEO_LONG to geoLong,
        JtxContract.JtxICalObject.LOCATION to location,
        JtxContract.JtxICalObject.LOCATION_ALTREP to locationAltrep,
        JtxContract.JtxICalObject.GEOFENCE_RADIUS to geofenceRadius,
        JtxContract.JtxICalObject.PERCENT to percent,
        JtxContract.JtxICalObject.DTSTAMP to dtstamp,
        JtxContract.JtxICalObject.DTSTART to dtstart,
        JtxContract.JtxICalObject.DTSTART_TIMEZONE to dtstartTimezone,
        JtxContract.JtxICalObject.DTEND to dtend,
        JtxContract.JtxICalObject.DTEND_TIMEZONE to dtendTimezone,
        JtxContract.JtxICalObject.COMPLETED to completed,
        JtxContract.JtxICalObject.COMPLETED_TIMEZONE to completedTimezone,
        JtxContract.JtxICalObject.DUE to due,
        JtxContract.JtxICalObject.DUE_TIMEZONE to dueTimezone,
        JtxContract.JtxICalObject.DURATION to duration,
        JtxContract.JtxICalObject.CREATED to created,
        JtxContract.JtxICalObject.LAST_MODIFIED to lastModified,
        JtxContract.JtxICalObject.SEQUENCE to sequence,
        JtxContract.JtxICalObject.RRULE to rrule,
        JtxContract.JtxICalObject.RDATE to rdate,
        JtxContract.JtxICalObject.EXDATE to exdate,
        JtxContract.JtxICalObject.RECURID to recurid,
        JtxContract.JtxICalObject.RECURID_TIMEZONE to recuridTimezone,

        JtxContract.JtxICalObject.FILENAME to fileName,
        JtxContract.JtxICalObject.ETAG to eTag,
        JtxContract.JtxICalObject.SCHEDULETAG to scheduleTag,
        JtxContract.JtxICalObject.FLAGS to flags,
        JtxContract.JtxICalObject.DIRTY to dirty
    )

    /**
     * @return The result of the given query as content values of the given JtxICalObject as a list of ContentValues
     */
    private fun getAsContentValues(
        uri: Uri,
        projection: Array<String>? = null,
        selection: String,
        selectionArgs: Array<String>,
        sortOrder: String? = null
    ): List<ContentValues> {

        val values: MutableList<ContentValues> = mutableListOf()
        collection.client.query(uri, projection, selection, selectionArgs, sortOrder
        )?.use { cursor ->
            while (cursor.moveToNext()) { values.add(cursor.toContentValues()) }
        }
        return values
    }
}
