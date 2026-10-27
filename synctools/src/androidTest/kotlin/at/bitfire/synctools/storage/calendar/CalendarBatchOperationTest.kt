/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.storage.calendar

import android.Manifest
import android.accounts.Account
import android.provider.CalendarContract
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import at.bitfire.synctools.storage.BatchOperation
import at.bitfire.synctools.storage.LocalStorageClient
import at.bitfire.synctools.storage.calendar.EventsContract.asSyncAdapter
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class CalendarBatchOperationTest {

    @get:Rule
    val permissionRule = GrantPermissionRule.grant(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)

    private val testAccount = Account(javaClass.name, CalendarContract.ACCOUNT_TYPE_LOCAL)

    private lateinit var client: LocalStorageClient

    @Before
    fun setUp() {
        val provider = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
            .acquireContentProviderClient(CalendarContract.AUTHORITY)!!
        client = LocalStorageClient(provider)
    }

    @After
    fun tearDown() {
        // delete all events in test account
        client.delete(
            CalendarContract.Events.CONTENT_URI,
            "${CalendarContract.Events.ACCOUNT_TYPE}=? AND ${CalendarContract.Events.ACCOUNT_NAME}=?",
            arrayOf(testAccount.type, testAccount.name)
        )
        client.close()
    }


    @Test
    fun testCalendarProvider_OperationsPerYieldPoint_501() {
        val batch = CalendarBatchOperation(client)

        // 501 operations should succeed with CalendarBatchOperation
        repeat(501) { idx ->
            batch += BatchOperation.CpoBuilder.newInsert(CalendarContract.Events.CONTENT_URI.asSyncAdapter(testAccount))
                .withValue(CalendarContract.Events.TITLE, "Event $idx")
        }
        batch.commit()
    }

}