/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid

import android.accounts.Account
import android.content.ContentUris
import android.content.ContentValues
import android.provider.CalendarContract
import androidx.test.platform.app.InstrumentationRegistry
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.resource.LocalCalendar
import at.bitfire.davdroid.resource.LocalEvent
import at.bitfire.ical4android.AndroidCalendar
import at.bitfire.ical4android.Event
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.RRule
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * JUnit ClassRule which initializes the AOSP CalendarProvider
 * Needed for some "flaky" tests which would otherwise only succeed on second run
 */
class InitCalendarProviderRule : TestRule {

    companion object {
        private val account = Account("LocalCalendarTest", CalendarContract.ACCOUNT_TYPE_LOCAL)
        private val context = InstrumentationRegistry.getInstrumentation().targetContext
        private val provider = context.contentResolver.acquireContentProviderClient(CalendarContract.AUTHORITY)!!
        private val uri = AndroidCalendar.create(account, provider, ContentValues())
        private val calendar = AndroidCalendar.findByID(account, provider, LocalCalendar.Factory, ContentUris.parseId(uri))
    }

    override fun apply(base: Statement, description: Description): Statement {
        Logger.log.info("Before test: ${description.displayName}")

        Logger.log.info("Initializing CalendarProvider (InitCalendarProviderRule)")

        // single event init
        val normalEvent = Event().apply {
            dtStart = DtStart("20220120T010203Z")
            summary = "Event with 1 instance"
        }
        val normalLocalEvent = LocalEvent(calendar, normalEvent, null, null, null, 0)
        normalLocalEvent.add()
        LocalEvent.numInstances(provider, account, normalLocalEvent.id!!)

        // recurring event init
        val recurringEvent = Event().apply {
            dtStart = DtStart("20220120T010203Z")
            summary = "Event over 22 years"
            rRules.add(RRule("FREQ=YEARLY;UNTIL=20740119T010203Z"))     // year needs to be  >2074 (not supported by Android <11 Calendar Storage)
        }
        val localRecurringEvent = LocalEvent(calendar, recurringEvent, null, null, null, 0)
        localRecurringEvent.add()
        LocalEvent.numInstances(provider, account, localRecurringEvent.id!!)

        // Run test
        Logger.log.info("Evaluating test..")
        return try {
            object : Statement() {
                @Throws(Throwable::class)
                override fun evaluate() {
                    base.evaluate()
                }
            }
        } finally {
            Logger.log.info("After test: $description")
            calendar.delete()
        }
    }
}
