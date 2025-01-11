/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.settings.migration

import android.accounts.Account
import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import at.bitfire.davdroid.sync.AutomaticSyncManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.MockKAnnotations
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.SpyK
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class AccountSettingsMigration19Test {

    @Inject @ApplicationContext
    @SpyK
    lateinit var context: Context

    @MockK(relaxed = true)
    lateinit var automaticSyncManager: AutomaticSyncManager

    @InjectMockKs
    lateinit var migration: AccountSettingsMigration19

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @get:Rule
    val hiltRule = HiltAndroidRule(this)


    @Before
    fun setUp() {
        hiltRule.inject()

        // Initialize WorkManager for instrumentation tests.
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .setWorkerFactory(workerFactory)
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)

        MockKAnnotations.init(this)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }


    @Test
    fun testMigrate_CancelsOldWorkersAndUpdatesAutomaticSync() {
        val workManager = WorkManager.getInstance(context)
        mockkObject(workManager)

        val account = Account("Some", "Test")
        migration.migrate(account)

        verify {
            workManager.cancelUniqueWork("periodic-sync at.bitfire.davdroid.addressbooks Test/Some")
            workManager.cancelUniqueWork("periodic-sync com.android.calendar Test/Some")
            workManager.cancelUniqueWork("periodic-sync at.techbee.jtx.provider Test/Some")
            workManager.cancelUniqueWork("periodic-sync org.dmfs.tasks Test/Some")
            workManager.cancelUniqueWork("periodic-sync org.tasks.opentasks Test/Some")

            automaticSyncManager.updateAutomaticSync(account)
        }
    }

}