/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */
package at.bitfire.davdroid.syncadapter

import android.accounts.*
import android.app.Service
import android.content.Context
import android.content.Intent
import android.database.DatabaseUtils
import android.os.Bundle
import at.bitfire.davdroid.R
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.model.ServiceDB
import at.bitfire.davdroid.resource.LocalAddressBook
import at.bitfire.davdroid.ui.setup.LoginActivity
import at.bitfire.vcard4android.ContactsStorageException
import java.util.*
import java.util.logging.Level


/**
 * Account authenticator for the main DAVdroid account type.
 *
 * Gets started when a DAVdroid account is removed, too, so it also watches for account removals
 * and contains the corresponding cleanup code.
 */
class AccountAuthenticatorService: Service(), OnAccountsUpdateListener {

    companion object {

        fun cleanupAccounts(context: Context) {
            Logger.log.info("Cleaning up orphaned accounts")

            ServiceDB.OpenHelper(context).use { dbHelper ->
                val db = dbHelper.writableDatabase

                val sqlAccountNames = LinkedList<String>()
                val accountNames = HashSet<String>()
                val accountManager = AccountManager.get(context)
                for (account in accountManager.getAccountsByType(context.getString(R.string.account_type))) {
                    sqlAccountNames.add(DatabaseUtils.sqlEscapeString(account.name))
                    accountNames += account.name
                }

                // delete orphaned address book accounts
                accountManager.getAccountsByType(context.getString(R.string.account_type_address_book))
                        .map { LocalAddressBook(context, it, null) }
                        .forEach {
                            try {
                                if (!accountNames.contains(it.mainAccount.name))
                                    it.delete()
                            } catch(e: Exception) {
                                Logger.log.log(Level.SEVERE, "Couldn't delete address book account", e)
                            }
                        }

                // delete orphaned services in DB
                if (sqlAccountNames.isEmpty())
                    db.delete(ServiceDB.Services._TABLE, null, null)
                else
                    db.delete(ServiceDB.Services._TABLE, "${ServiceDB.Services.ACCOUNT_NAME} NOT IN (${sqlAccountNames.joinToString(",")})", null)
            }
        }

    }


    private lateinit var accountManager: AccountManager
    private lateinit var accountAuthenticator: AccountAuthenticator

    override fun onCreate() {
        accountManager = AccountManager.get(this)
        accountManager.addOnAccountsUpdatedListener(this, null, true)

        accountAuthenticator = AccountAuthenticator(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        accountManager.removeOnAccountsUpdatedListener(this)
    }

    override fun onBind(intent: Intent?) =
            accountAuthenticator.iBinder.takeIf { intent?.action == android.accounts.AccountManager.ACTION_AUTHENTICATOR_INTENT }


    override fun onAccountsUpdated(accounts: Array<out Account>?) {
        cleanupAccounts(this)
    }


    private class AccountAuthenticator(
            val context: Context
    ): AbstractAccountAuthenticator(context) {

        override fun addAccount(response: AccountAuthenticatorResponse?, accountType: String?, authTokenType: String?, requiredFeatures: Array<String>?, options: Bundle?): Bundle {
            val intent = Intent(context, LoginActivity::class.java)
            intent.putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, response)
            val bundle = Bundle(1)
            bundle.putParcelable(AccountManager.KEY_INTENT, intent)
            return bundle
        }

        override fun editProperties(response: AccountAuthenticatorResponse?, accountType: String?)  = null
        override fun getAuthTokenLabel(p0: String?) = null
        override fun confirmCredentials(p0: AccountAuthenticatorResponse?, p1: Account?, p2: Bundle?) = null
        override fun updateCredentials(p0: AccountAuthenticatorResponse?, p1: Account?, p2: String?, p3: Bundle?) = null
        override fun getAuthToken(p0: AccountAuthenticatorResponse?, p1: Account?, p2: String?, p3: Bundle?) = null
        override fun hasFeatures(p0: AccountAuthenticatorResponse?, p1: Account?, p2: Array<out String>?) = null

    }
}
