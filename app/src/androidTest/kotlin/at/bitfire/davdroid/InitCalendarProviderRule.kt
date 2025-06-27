/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid

import android.Manifest
import android.accounts.Account
import android.content.ContentProviderClient
import android.content.ContentUris
import android.content.ContentValues
import android.os.Build
import android.provider.CalendarContract
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import at.bitfire.davdroid.resource.LocalCalendar
import at.bitfire.ical4android.AndroidCalendar
import at.bitfire.ical4android.AndroidEvent
import at.bitfire.ical4android.Event
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.RRule
import org.junit.Assert.assertNotNull
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import java.util.logging.Logger

/**
 * JUnit ClassRule which initializes the AOSP CalendarProvider.
 *
 * It seems that the calendar provider unfortunately forgets the very first requests when it is used the very first time,
 * maybe by some wrongly synchronized database initialization. So things like querying the instances
 * fails in this case.
 *
 * So this rule is needed to allow tests which need the calendar provider to succeed even when the calendar provider
 * is used the very first time (especially in CI tests / a fresh emulator).
 *
 * See [at.bitfire.davdroid.resource.LocalCalendarTest] for an example of how to use this rule.
 */
class InitCalendarProviderRule private constructor(): ExternalResource() {

    companion object {

        private var isInitialized = false
        private val logger = Logger.getLogger(InitCalendarProviderRule::javaClass.name)

        fun getInstance(): RuleChain = RuleChain
            .outerRule(GrantPermissionRule.grant(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
            .around(InitCalendarProviderRule())

    }

    override fun before() {
        if (!isInitialized) {
            logger.info("Initializing calendar provider")
            if (Build.VERSION.SDK_INT < 31)
                logger.warning("Calendar provider initialization may or may not work. See InitCalendarProviderRule")

            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val client = context.contentResolver.acquireContentProviderClient(CalendarContract.AUTHORITY)
            assertNotNull("Couldn't acquire calendar provider", client)

            client!!.use {
                initCalendarProvider(client)
                isInitialized = true
            }
        }
    }

    private fun initCalendarProvider(provider: ContentProviderClient) {
        val account = Account("LocalCalendarTest", CalendarContract.ACCOUNT_TYPE_LOCAL)

        // Sometimes, the calendar provider returns an ID for the created calendar, but then fails to find it.
        var calendarOrNull: LocalCalendar? = null
        for (i in 0..50) {
            calendarOrNull = createAndVerifyCalendar(account, provider)
            if (calendarOrNull != null)
                break
            else
                Thread.sleep(100)
        }
        val calendar = calendarOrNull ?: throw IllegalStateException("Couldn't create calendar")

        try {
            // single event init
            val normalEvent = Event().apply {
                dtStart = DtStart("20220120T010203Z")
                summary = "Event with 1 instance"
            }
            val normalLocalEvent = AndroidEvent(calendar, normalEvent, null, null, null, 0)
            normalLocalEvent.add()
            AndroidEvent.numInstances(provider, account, normalLocalEvent.id!!)

            // recurring event init
            val recurringEvent = Event().apply {
                dtStart = DtStart("20220120T010203Z")
                summary = "Event over 22 years"
                rRules.add(RRule("FREQ=YEARLY;UNTIL=20740119T010203Z"))     // year needs to be  >2074 (not supported by Android <11 Calendar Storage)
            }
            val localRecurringEvent = AndroidEvent(calendar, recurringEvent, null, null, null, 0)
            localRecurringEvent.add()
            AndroidEvent.numInstances(provider, account, localRecurringEvent.id!!)
        } finally {
            calendar.delete()
        }
    }

    private fun createAndVerifyCalendar(account: Account, provider: ContentProviderClient): LocalCalendar? {
        val uri = AndroidCalendar.create(account, provider, ContentValues())

        return try {
            AndroidCalendar.findByID(
                account,
                provider,
                LocalCalendar.Factory,
                ContentUris.parseId(uri)
            )
        } catch (e: Exception) {
            logger.warning("Couldn't find calendar after creation: $e")
            null
        }
    }

}