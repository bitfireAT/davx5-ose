/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.ical4android.util

import android.accounts.Account
import android.net.Uri
import at.bitfire.ical4android.util.MiscUtils.asSyncAdapter
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MiscUtilsTest {

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