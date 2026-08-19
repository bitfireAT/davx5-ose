/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource.local

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import at.bitfire.davdroid.R
import at.bitfire.davdroid.accounts.AccountId
import at.bitfire.davdroid.accounts.DbAccountId
import at.bitfire.davdroid.accounts.LegacyAccount
import at.bitfire.synctools.util.setAndVerifyUserData
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Deals with reading and writing user data of an address book account.
 */
class AddressBookAccountProperties @Inject constructor(
    private val accountManager: AccountManager,
    @ApplicationContext private val context: Context
) {
    private val mainAccountType = context.getString(R.string.account_type)
    private val addressBookAccountType = context.getString(R.string.account_type_address_book)


    /**
     * Get [AccountId] of the app account that the given address book account belongs to.
     */
    fun getAppAccount(account: Account): AccountId? {
        requireAddressBookAccount(account)

        val ownerName = accountManager.getUserData(account, USER_DATA_ACCOUNT_NAME)
        val ownerType = accountManager.getUserData(account, USER_DATA_ACCOUNT_TYPE)

        return if (ownerName != null && ownerType == mainAccountType) {
            LegacyAccount(Account(ownerName, ownerType))
        } else {
            null
        }
    }

    /**
     * Set [AccountId] of the app account that the given address book account belongs to.
     */
    fun setAppAccount(account: Account, owner: AccountId) {
        requireAddressBookAccount(account)

        when (owner) {
            is LegacyAccount -> {
                accountManager.setAndVerifyUserData(account, USER_DATA_ACCOUNT_NAME, owner.androidAccount.name)
                accountManager.setAndVerifyUserData(account, USER_DATA_ACCOUNT_TYPE, owner.androidAccount.type)
            }
            is DbAccountId -> TODO("Operation not implemented")
        }
    }

    /**
     * Get the collection ID for the given address book account.
     */
    fun getCollectionId(account: Account): Long? {
        requireAddressBookAccount(account)

        return accountManager.getUserData(account, USER_DATA_COLLECTION_ID)?.toLongOrNull()
    }

    /**
     * Set the collection ID for the given address book account.
     */
    fun setCollectionId(account: Account, collectionId: Long?) {
        requireAddressBookAccount(account)

        accountManager.setAndVerifyUserData(account, USER_DATA_COLLECTION_ID, collectionId?.toString())
    }

    private fun requireAddressBookAccount(account: Account) {
        require(account.type == addressBookAccountType) { "Account.type needs to be $addressBookAccountType" }
    }


    companion object {
        private const val USER_DATA_ACCOUNT_NAME = "account_name"
        private const val USER_DATA_ACCOUNT_TYPE = "account_type"

        /**
         * ID of the corresponding database [at.bitfire.davdroid.db.Collection].
         *
         * User data of the address book account (Long).
         */
        private const val USER_DATA_COLLECTION_ID = "collection_id"
    }
}
