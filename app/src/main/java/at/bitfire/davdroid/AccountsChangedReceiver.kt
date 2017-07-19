/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid

import android.accounts.AccountManager
import android.accounts.OnAccountsUpdateListener
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.*

class AccountsChangedReceiver: BroadcastReceiver() {

    companion object {

        private val listeners = LinkedList<OnAccountsUpdateListener>()

        @JvmStatic
        fun registerListener(listener: OnAccountsUpdateListener, callImmediately: Boolean) {
            listeners += listener
            if (callImmediately)
                listener.onAccountsUpdated(null)
        }

        @JvmStatic
        fun unregisterListener(listener: OnAccountsUpdateListener) {
            listeners -= listener
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == AccountManager.LOGIN_ACCOUNTS_CHANGED_ACTION) {
            val serviceIntent = Intent(context, DavService::class.java)
            serviceIntent.action = DavService.ACTION_ACCOUNTS_UPDATED
            context.startService(serviceIntent)

            for (listener in listeners)
                listener.onAccountsUpdated(null)
        }
    }

}
