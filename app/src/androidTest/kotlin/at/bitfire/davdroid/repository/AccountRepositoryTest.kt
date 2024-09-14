/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.repository

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import at.bitfire.davdroid.R
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.settings.SettingsManager
import at.bitfire.davdroid.sync.TasksAppManager
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.mockk
import org.junit.After
import org.junit.Before
import org.junit.Rule
import java.util.logging.Logger
import javax.inject.Inject

@HiltAndroidTest
class AccountRepositoryTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    @ApplicationContext
    lateinit var context: Context

    private val addressBookAccountType by lazy { context.getString(R.string.account_type_address_book) }
    private val addressBookAccount by lazy { Account("sub", addressBookAccountType) }

    private val accountManager by lazy { AccountManager.get(context) }

    private val collectionRepository = mockk<DavCollectionRepository>(relaxed = true)
    private val serviceRepository = mockk<DavServiceRepository>(relaxed = true)
    private val accountRepository by lazy { AccountRepository(
        mockk<AccountSettings.Factory>(relaxed = true),
        context,
        collectionRepository,
        mockk<DavHomeSetRepository>(relaxed = true),
        mockk<Logger>(relaxed = true),
        mockk<SettingsManager>(relaxed = true),
        serviceRepository,
        mockk<Lazy<TasksAppManager>>(relaxed = true),
    ) }

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @After
    fun tearDown() {
        accountManager.removeAccountExplicitly(addressBookAccount)
    }

}