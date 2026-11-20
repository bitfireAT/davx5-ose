/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.accountscheck

import android.accounts.AccountManager
import android.content.Context
import at.bitfire.davdroid.R
import at.bitfire.davdroid.accounts.AndroidAccountManager
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.repository.DbAccountRepository
import at.bitfire.davdroid.sync.account.TestAccount
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.logging.Logger
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

@HiltAndroidTest
class AccountsCheckViewModelTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var accountManager: AccountManager

    @Inject @ApplicationContext
    lateinit var context: Context

    @Inject
    lateinit var db: AppDatabase

    @Inject
    internal lateinit var dbAccountRepository: DbAccountRepository

    @Inject
    lateinit var logger: Logger

    lateinit var model: AccountsCheckViewModel

    @Before
    fun setUp() {
        hiltRule.inject()

        // real IO dispatcher because the injected test dispatcher doesn't run without advancing its scheduler
        val androidAccountManager = AndroidAccountManager({ accountManager }, context, db, Dispatchers.IO, logger)
        model = AccountsCheckViewModel(androidAccountManager, dbAccountRepository)
    }

    @After
    fun tearDown() {
        for (type in arrayOf(R.string.account_type, R.string.account_type_db))
            for (account in accountManager.getAccountsByType(context.getString(type)))
                accountManager.removeAccountExplicitly(account)
    }


    @Test
    fun testAccounts_UpdatesWhenAccountIsAdded() = runWithSubscribedModel {
        awaitAccounts { accounts -> accounts.none { it.name == "New account" } }

        TestAccount.create(accountName = "New account")

        awaitAccounts { accounts -> AccountStatus.Legacy("New account") in accounts }
    }

    @Test
    fun testAccounts_UpdatesWhenAccountIsRenamed() = runWithSubscribedModel {
        val account = TestAccount.create(accountName = "Old name")
        awaitAccounts { accounts -> accounts.any { it.name == "Old name" } }

        TestAccount.rename(account, "New name")

        awaitAccounts { accounts -> accounts.any { it.name == "New name" } && accounts.none { it.name == "Old name" } }
    }

    @Test
    fun testAccounts_UpdatesWhenAccountIsRemoved() = runWithSubscribedModel {
        val account = TestAccount.create(accountName = "Removed account")
        awaitAccounts { accounts -> accounts.any { it.name == "Removed account" } }

        TestAccount.remove(account)

        awaitAccounts { accounts -> accounts.none { it.name == "Removed account" } }
    }


    /**
     * Keeps [AccountsCheckViewModel.accounts] subscribed while [block] runs, so that it only changes by live updates.
     */
    private fun runWithSubscribedModel(block: suspend CoroutineScope.() -> Unit) = runBlocking {
        val subscription = launch { model.accounts.collect {} }
        try {
            block()
        } finally {
            subscription.cancel()
        }
    }

    /**
     * Waits until the accounts match [predicate]; fails with a timeout otherwise.
     */
    private suspend fun awaitAccounts(predicate: (List<AccountStatus>) -> Boolean) {
        withTimeout(5.seconds) {
            model.accounts.filterNotNull().first(predicate)
        }
    }

}
