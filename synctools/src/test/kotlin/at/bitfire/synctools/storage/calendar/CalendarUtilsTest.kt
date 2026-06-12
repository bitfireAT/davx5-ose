/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.storage.calendar

import android.accounts.Account
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CalendarUtilsTest {

    @Test
    fun testUriHelper_asSyncAdapter() {
        val account = Account("testName", "testType")
        val baseUri = Uri.parse("test://example.com/")
        assertEquals(
            Uri.parse("$baseUri?account_name=testName&account_type=testType&caller_is_syncadapter=true"),
            baseUri.asSyncAdapter(account)
        )
    }

}
