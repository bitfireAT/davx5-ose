/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.settings.migration

import android.accounts.Account
import androidx.work.WorkManager
import at.bitfire.davdroid.accounts.toAccountId
import at.bitfire.davdroid.di.SupportLibsModule
import at.bitfire.davdroid.sync.AutomaticSyncManager
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit4.MockKRule
import io.mockk.verify
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
@UninstallModules(SupportLibsModule::class)
class AccountSettingsMigration19Test {

    @BindValue
    @RelaxedMockK
    lateinit var automaticSyncManager: AutomaticSyncManager

    @Inject
    lateinit var migration: AccountSettingsMigration19

    @BindValue @MockK(relaxed = true)
    lateinit var workManager: WorkManager

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @get:Rule
    val mockkRule = MockKRule(this)


    @Before
    fun setUp() {
        hiltRule.inject()
    }


    @Test
    fun testMigrate_CancelsOldWorkersAndUpdatesAutomaticSync() {
        val account = Account("Some", "Test")
        migration.migrate(account)

        verify {
            workManager.cancelUniqueWork("periodic-sync at.bitfire.davdroid.addressbooks Test/Some")
            workManager.cancelUniqueWork("periodic-sync com.android.calendar Test/Some")
            workManager.cancelUniqueWork("periodic-sync at.techbee.jtx.provider Test/Some")
            workManager.cancelUniqueWork("periodic-sync org.dmfs.tasks Test/Some")
            workManager.cancelUniqueWork("periodic-sync org.tasks.opentasks Test/Some")

            automaticSyncManager.updateAutomaticSync(account.toAccountId())
        }
    }

}