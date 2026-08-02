/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.contacts.builder

import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.Event
import at.bitfire.synctools.mapping.contacts.Contact
import at.bitfire.synctools.storage.BatchOperation
import ezvcard.property.DateOrTimeProperty
import ezvcard.util.PartialDate
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.LinkedList
import java.util.Locale

class EventBuilder(dataRowUri: Uri, rawContactId: Long?, contact: Contact, readOnly: Boolean)
    : DataRowBuilder(Factory.mimeType(), dataRowUri, rawContactId, contact, readOnly) {

    companion object {
        const val DATE_AND_TIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
        const val FULL_DATE_FORMAT = "yyyy-MM-dd"
        const val NO_YEAR_DATE_FORMAT = "--MM-dd"
        //const val NO_YEAR_DATE_AND_TIME_FORMAT = "--MM-dd'T'HH:mm:ss.SSS'Z'"

        const val TIME_PART = "'T'HH:mm:ss.SSS'Z'"
    }

    override fun build(): List<BatchOperation.CpoBuilder> {
        val result = LinkedList<BatchOperation.CpoBuilder>()

        buildEvent(contact.birthDay, Event.TYPE_BIRTHDAY)?.let { result += it }
        buildEvent(contact.anniversary, Event.TYPE_ANNIVERSARY)?.let { result += it }

        for (customDate in contact.customDates) {
            val label = customDate.label
            val typeCode = if (label != null)
                Event.TYPE_CUSTOM
            else
                Event.TYPE_OTHER
            buildEvent(customDate.property, typeCode, label)?.let { result += it }
        }

        return result
    }

    private fun buildEvent(dateOrTime: DateOrTimeProperty?, typeCode: Int, label: String? = null): BatchOperation.CpoBuilder? {
        if (dateOrTime == null)
            return null

        // See here for formats supported by AOSP Contacts:
        // https://android.googlesource.com/platform/packages/apps/Contacts/+/refs/tags/android-13.0.0_r49/src/com/android/contacts/util/CommonDateUtils.java

        val androidStr: String? =
            when {
                dateOrTime.date != null -> {
                    when (val date = dateOrTime.date) {
                        is Instant -> {
                            val utc = OffsetDateTime.ofInstant(date, ZoneOffset.UTC)
                            DateTimeFormatter.ofPattern(DATE_AND_TIME_FORMAT, Locale.US).format(utc)
                        }
                        is LocalDate ->
                            DateTimeFormatter.ofPattern(FULL_DATE_FORMAT, Locale.US).format(date)
                        is LocalDateTime ->
                            DateTimeFormatter.ofPattern(DATE_AND_TIME_FORMAT, Locale.US).format(date)
                        is OffsetDateTime -> {
                            // time zones not supported by Contacts storage, convert to UTC
                            val utc = date.atZoneSameInstant(ZoneOffset.UTC)
                            DateTimeFormatter.ofPattern(DATE_AND_TIME_FORMAT, Locale.US).format(utc)
                        }
                        else -> {
                            logger.warning("Unsupported date/time class: ${date::class.java.name}")
                            null
                        }
                    }
                }
                dateOrTime.partialDate != null ->
                    partialDateToAndroid(dateOrTime.partialDate)
                else ->
                    null
            }
        if (androidStr == null) {
            logger.warning("Ignoring date/time without supported (partial) date: $dateOrTime")
            return null
        }

        val builder = newDataRow()
            .withValue(Event.TYPE, typeCode)
            .withValue(Event.START_DATE, androidStr)

        if (label != null)
            builder.withValue(Event.LABEL, label)

        return builder
    }

    private fun partialDateToAndroid(partialDate: PartialDate): String? {
        // possible values: see RFC 6350 4.3.4 DATE-AND-OR-TIME
        val dateStr =
            if (partialDate.hasDateComponent()) {
                if (partialDate.month != null && partialDate.date != null) {
                    if (partialDate.year == null) {
                        val date = LocalDate.of(
                            /* dummy, won't be used */ 2000,
                            partialDate.month,
                            partialDate.date
                        )
                        DateTimeFormatter.ofPattern(NO_YEAR_DATE_FORMAT, Locale.US).format(date)
                    } else /* partialDate.year != null */ {
                        val date = LocalDate.of(
                            partialDate.year,
                            partialDate.month,
                            partialDate.date
                        )
                        DateTimeFormatter.ofPattern(FULL_DATE_FORMAT, Locale.US).format(date)
                    }
                } else  // no month and/or day-of-month
                    null
            } else  // no date component
                null

        if (dateStr == null)
            return null

        val str = StringBuilder(dateStr)
        // we have a (partial) date, append time if possible
        if (partialDate.hasTimeComponent()) {
            val timeStr = DateTimeFormatter.ofPattern(TIME_PART).format(
                LocalTime.of(
                    partialDate.hour ?: 0,
                    partialDate.minute ?: 0,
                    partialDate.second ?: 0
                )
            )
            str.append(timeStr)
        }

        return str.toString()
    }


    object Factory: DataRowBuilder.Factory<EventBuilder> {
        override fun mimeType() = Event.CONTENT_ITEM_TYPE
        override fun newInstance(dataRowUri: Uri, rawContactId: Long?, contact: Contact, readOnly: Boolean) =
            EventBuilder(dataRowUri, rawContactId, contact, readOnly)
    }

}