/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid

import android.Manifest
import android.accounts.Account
import android.content.ContentUris
import android.content.ContentValues
import android.os.Build
import android.provider.CalendarContract
import androidx.annotation.RequiresApi
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.resource.LocalCalendar
import at.bitfire.davdroid.resource.LocalEvent
import at.bitfire.ical4android.AndroidCalendar
import at.bitfire.ical4android.Event
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.RRule
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * JUnit ClassRule which initializes the AOSP CalendarProvider.
 * Needed for some "flaky" tests which would otherwise only succeed on second run.
 *
 * Currently tested on development machine (Ryzen) with Android 12 images (with/without Google Play).
 * Calendar provider behaves quite randomly, so it may or may not work. If you (the reader
 * if this comment) can find out on how to initialize the calendar provider so that the
 * tests are reliably run after `adb shell pm clear com.android.providers.calendar`,
 * please let us know!
 *
 * If you run tests manually, just make sure to ignore the first run after the calendar
 * provider has been accessed the first time.
 */
class InitCalendarProviderRule private constructor(): TestRule {

    companion object {
        fun getInstance() = RuleChain
            .outerRule(GrantPermissionRule.grant(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
            .around(InitCalendarProviderRule())
    }

    override fun apply(base: Statement, description: Description): Statement {
        Logger.log.info("Initializing calendar provider before running ${description.displayName}")
        return InitCalendarProviderStatement(base)
    }


    class InitCalendarProviderStatement(val base: Statement): Statement() {

        override fun evaluate() {
            if (Build.VERSION.SDK_INT < 31)
                Logger.log.warning("Calendar provider initialization may or may not work. See InitCalendarProviderRule")
            initCalendarProvider()

            base.evaluate()
        }

        private fun initCalendarProvider() {
            val account = Account("LocalCalendarTest", CalendarContract.ACCOUNT_TYPE_LOCAL)
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val provider = context.contentResolver.acquireContentProviderClient(CalendarContract.AUTHORITY)!!
            val uri = AndroidCalendar.create(account, provider, ContentValues())
            val calendar = AndroidCalendar.findByID(account, provider, LocalCalendar.Factory, ContentUris.parseId(uri))
            try {
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
            } finally {
                calendar.delete()
            }
        }
    }

}