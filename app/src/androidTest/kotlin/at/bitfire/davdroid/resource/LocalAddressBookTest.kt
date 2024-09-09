/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import at.bitfire.davdroid.R
import at.bitfire.davdroid.repository.AccountRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import javax.inject.Inject

@HiltAndroidTest
class LocalAddressBookTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    @ApplicationContext
    lateinit var context: Context

    @Inject
    lateinit var accountRepository: AccountRepository

    private val addressBookAccountType by lazy { context.getString(R.string.account_type_address_book) }
    private val addressBookAccount by lazy { Account("sub", addressBookAccountType) }

    private val accountManager by lazy { AccountManager.get(context) }

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @After
    fun tearDown() {
        accountManager.removeAccountExplicitly(addressBookAccount)
    }

}