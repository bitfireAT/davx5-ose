/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.contacts.handler

import android.content.ContentValues
import android.provider.ContactsContract.CommonDataKinds.Event
import androidx.annotation.VisibleForTesting
import at.bitfire.synctools.mapping.contacts.Contact
import at.bitfire.synctools.mapping.contacts.LabeledProperty
import at.bitfire.synctools.mapping.contacts.handler.EventHandler.fullDateFormat
import at.bitfire.synctools.mapping.contacts.handler.EventHandler.fullDateTimeFormats
import at.bitfire.synctools.util.trimToNull
import at.bitfire.synctools.vcard.property.XAbDate
import ezvcard.property.Anniversary
import ezvcard.property.Birthday
import ezvcard.util.PartialDate
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.Temporal

/**
 * Maps contact events (like birthdays and anniversaries) to vCard properties.
 *
 * Android stores the events as date/date-time strings, so we have to parse these strings.
 * Unfortunately, the format is not specified in the ContactsContract ("as the user entered it"):
 * https://developer.android.com/reference/android/provider/ContactsContract.CommonDataKinds.Event?hl=en#START_DATE
 *
 * At least we know the formats used by AOSP Contacts:
 * https://android.googlesource.com/platform/packages/apps/Contacts/+/c326c157541978c180be4e3432327eceb1e66637/src/com/android/contacts/util/CommonDateUtils.java#25
 * so we support at least these formats.
 */
object EventHandler : DataRowHandler() {

    /**
     * Date formats for full date with time (taken from Android's CommonDateUtils).
     */
    private val fullDateTimeFormats = listOf(
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX"),
        // "yyyy-MM-dd'T'HH:mm:ssXXX"
        DateTimeFormatter.ISO_OFFSET_DATE_TIME,
    )

    /**
     * Date format for full date without time (taken from Android's CommonDateUtils).
     */
    private val fullDateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")


    override fun forMimeType() = Event.CONTENT_ITEM_TYPE

    override fun handle(values: ContentValues, contact: Contact) {
        super.handle(values, contact)

        val dateStr = values.getAsString(Event.START_DATE) ?: return
        val full: Temporal? = parseFullDate(dateStr)
        val partial: PartialDate? = if (full == null)
            parsePartialDate(dateStr)
        else
            null

        if (full != null || partial != null)
            when (values.getAsInteger(Event.TYPE)) {
                Event.TYPE_ANNIVERSARY ->
                    contact.anniversary =
                        if (full != null) Anniversary(full) else Anniversary(partial)

                Event.TYPE_BIRTHDAY ->
                    contact.birthDay = if (full != null) Birthday(full) else Birthday(partial)
                /* Event.TYPE_OTHER,
                Event.TYPE_CUSTOM */
                else -> {
                    val abDate = if (full != null) XAbDate(full) else XAbDate(partial)
                    val label = values.getAsString(Event.LABEL).trimToNull()
                    contact.customDates += LabeledProperty(abDate, label)
                }
            }
    }

    /**
     * Tries to parse a contact event date string into a [Temporal] object using multiple acceptable formats.
     *
     * "Full" means "with year" in this context.
     *
     * @param dateString The contact event date string to parse.
     *
     * @return The parsed [Temporal] if successful, or `null` if none of the formats match. If format is:
     * - `yyyy-MM-dd'T'HH:mm:ss.SSS'Z'` or `yyyy-MM-dd'T'HH:mm:ssXXX` ([fullDateTimeFormats]) -> [OffsetDateTime]
     * - `yyyy-MM-dd` ([fullDateFormat]) -> [LocalDate]
     * - else -> `null`
     */
    @VisibleForTesting
    internal fun parseFullDate(dateString: String): Temporal? {
        // try to parse as full date-time
        for (formatter in fullDateTimeFormats) {
            try {
                return OffsetDateTime.parse(dateString, formatter)
            } catch (_: DateTimeParseException) {
                // ignore: given date is not valid
            }
        }

        // try to parse as full date (without time)
        try {
            return LocalDate.parse(dateString, fullDateFormat)
        } catch (_: DateTimeParseException) {
            // ignore: given date is not valid
        }

        // could not parse date
        return null
    }

    /**
     * Tries to parse a contact event date string into a [PartialDate] object, covering the cases
     * from Android's CommonDateUtils:
     *
     * - `--MM-dd`
     * - `--MM-dd'T'HH:mm:ss.SSS'Z'`
     *
     * Does some preprocessing to handle the 'Z' suffix and strip nanoseconds
     * (both not supported by [PartialDate.parse]).
     *
     * "Partial" means "without year" in this context.
     *
     * @param dateString The date string to parse.
     * @return The parsed [PartialDate] or `null` if parsing fails.
     */
    @VisibleForTesting
    internal fun parsePartialDate(dateString: String): PartialDate? {
        return try {
            // convert Android partial date/date-time to vCard partial date/date-time so that it can be parsed by ez-vcard

            val withoutZ = if (dateString.endsWith('Z')) {
                // 'Z' is not supported for suffix in PartialDate, replace with actual offset
                dateString.removeSuffix("Z") + "+00:00"
            } else
                dateString

            // PartialDate.parse() does not accept fractions of seconds, so strip them if present
            val subSecondsRegex = "\\.\\d+".toRegex()   // 2025-12-05T010203.456+00:30
            //                  ^^^^ (number of digits may vary)
            val subSecondsMatch = subSecondsRegex.find(withoutZ)
            val withoutSubSeconds = if (subSecondsMatch != null)
                withoutZ.removeRange(subSecondsMatch.range)
            else
                withoutZ

            PartialDate.parse(withoutSubSeconds)
        } catch (_: IllegalArgumentException) {
            // An error was thrown by PartialDate.parse
            null
        }
    }

}