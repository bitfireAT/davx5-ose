/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.repository

import android.accounts.AccountManager
import android.content.Context
import at.bitfire.davdroid.R
import at.bitfire.davdroid.servicedetection.DavResourceFinder
import at.bitfire.synctools.vcard.GroupMethod
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
internal class DbAccountRepositoryTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var accountManager: AccountManager

    @Inject
    @ApplicationContext
    lateinit var context: Context

    @Inject
    lateinit var dbAccountRepository: DbAccountRepository

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun testCreateDbAccount_doesNotCreateAndroidAccount() = runTest {
        val config = DavResourceFinder.Configuration(cardDAV = null, calDAV = null, encountered401 = false)

        dbAccountRepository.create("DB Account", null, config, GroupMethod.GROUP_VCARDS, null)

        assertTrue(accountManager.getAccountsByType(context.getString(R.string.account_type_db)).isEmpty())
    }
}
