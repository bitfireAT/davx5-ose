/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.storage.calendar

import android.Manifest
import android.accounts.Account
import android.content.ContentProviderClient
import android.provider.CalendarContract
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import at.bitfire.ical4android.util.MiscUtils.asSyncAdapter

import at.bitfire.synctools.storage.BatchOperation
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class CalendarBatchOperationTest {

    @get:Rule
    val permissionRule = GrantPermissionRule.grant(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)

    private val testAccount = Account(javaClass.name, CalendarContract.ACCOUNT_TYPE_LOCAL)

    private lateinit var provider: ContentProviderClient

    @Before
    fun setUp() {
        provider = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
            .acquireContentProviderClient(CalendarContract.AUTHORITY)!!
    }

    @After
    fun tearDown() {
        // delete all events in test account
        provider.delete(
            CalendarContract.Events.CONTENT_URI,
            "${CalendarContract.Events.ACCOUNT_TYPE}=? AND ${CalendarContract.Events.ACCOUNT_NAME}=?",
            arrayOf(testAccount.type, testAccount.name)
        )
        provider.close()
    }


    @Test
    fun testCalendarProvider_OperationsPerYieldPoint_501() {
        val batch = CalendarBatchOperation(provider)

        // 501 operations should succeed with CalendarBatchOperation
        repeat(501) { idx ->
            batch += BatchOperation.CpoBuilder.newInsert(CalendarContract.Events.CONTENT_URI.asSyncAdapter(testAccount))
                .withValue(CalendarContract.Events.TITLE, "Event $idx")
        }
        batch.commit()
    }

}