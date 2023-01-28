/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.syncadapter

import android.accounts.Account
import android.accounts.AccountManager
import android.accounts.OnAccountsUpdateListener
import android.content.Context
import androidx.annotation.AnyThread
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

class AccountsUpdatedListener private constructor(
    val context: Context
): OnAccountsUpdateListener {

    @Module
    @InstallIn(SingletonComponent::class)
    object AccountsUpdatedListenerModule {
        @Provides
        @Singleton
        fun accountsUpdatedListener(@ApplicationContext context: Context) = AccountsUpdatedListener(context)
    }

    fun listen() {
        val accountManager = AccountManager.get(context)
        accountManager.addOnAccountsUpdatedListener(this, null, true)
    }

    /**
     * Called when the system accounts have been updated. The interesting case for us is when
     * a DAVx5 account has been removed. Then we enqueue an [AccountsCleanupWorker] to remove
     * the orphaned entries from the database.
     */
    @AnyThread
    override fun onAccountsUpdated(accounts: Array<out Account>) {
        AccountsCleanupWorker.enqueue(context)
    }

}