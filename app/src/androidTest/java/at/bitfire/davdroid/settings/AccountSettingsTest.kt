/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.settings

import android.accounts.Account
import android.accounts.AccountManager
import android.content.ContentResolver
import android.os.Build
import android.provider.CalendarContract
import android.provider.ContactsContract
import androidx.test.platform.app.InstrumentationRegistry
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.Credentials
import at.bitfire.davdroid.syncadapter.AccountUtils
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidTest
class AccountSettingsTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var settingsManager: SettingsManager


    val context = InstrumentationRegistry.getInstrumentation().targetContext

    val account = Account("Test Account", context.getString(R.string.account_type))
    val fakeCredentials = Credentials("test", "test")

    @Before
    fun setUp() {
        hiltRule.inject()

        assertTrue(AccountUtils.createAccount(context, account, AccountSettings.initialUserData(fakeCredentials)))
        ContentResolver.setIsSyncable(account, CalendarContract.AUTHORITY, 1)
        ContentResolver.setIsSyncable(account, ContactsContract.AUTHORITY, 0)
    }

    @After
    fun removeAccount() {
        val futureResult = AccountManager.get(context).removeAccount(account, {}, null)
        assertTrue(futureResult.getResult(10, TimeUnit.SECONDS))
    }


    @Test
    fun testSyncIntervals() {
        val settings = AccountSettings(context, account)
        val presetIntervals = context.resources.getStringArray(R.array.settings_sync_interval_seconds)
                .map { it.toLong() }
                .filter { it != AccountSettings.SYNC_INTERVAL_MANUALLY }
        for (interval in presetIntervals) {
            assertTrue(settings.setSyncInterval(CalendarContract.AUTHORITY, interval))
            assertEquals(interval, settings.getSyncInterval(CalendarContract.AUTHORITY))
        }
    }

    @Test
    fun testSyncIntervals_IsNotSyncable() {
        val settings = AccountSettings(context, account)
        val interval = 15*60L    // 15 min
        val result = settings.setSyncInterval(ContactsContract.AUTHORITY, interval)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)     // below Android 7, Android returns true for whatever reason
            assertFalse(result)
    }

    @Test
    fun testSyncIntervals_TooShort() {
        val settings = AccountSettings(context, account)
        val interval = 60L      // 1 min is not supported by Android
        val result = settings.setSyncInterval(CalendarContract.AUTHORITY, interval)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)     // below Android 7, Android returns true for whatever reason
            assertFalse(result)
    }

}