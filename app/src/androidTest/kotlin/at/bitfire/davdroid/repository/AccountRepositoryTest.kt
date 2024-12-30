/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.repository

import android.content.Context
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.sync.account.TestAccountAuthenticator
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject
import at.bitfire.davdroid.db.Account as DbAccount

@HiltAndroidTest
class AccountRepositoryTest {

    @Inject
    lateinit var accountRepository: AccountRepository

    @Inject @ApplicationContext
    lateinit var context: Context

    @Inject
    lateinit var db: AppDatabase

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Before
    fun setUp() {
        hiltRule.inject()
    }


    @Test
    fun testRemoveOrphanedInDb() {
        TestAccountAuthenticator.provide(accountType = context.getString(R.string.account_type)) { systemAccount ->
            val dao = db.accountDao()
            dao.insertOrIgnore(DbAccount(id = 1, name = systemAccount.name))
            dao.insertOrIgnore(DbAccount(id = 2, name = "no-corresponding-system-account"))

            accountRepository.removeOrphanedInDb()

            // now the account without a corresponding system account should be removed
            assertEquals(listOf(DbAccount(id = 1, name = systemAccount.name)), db.accountDao().getAll())
        }
    }

}