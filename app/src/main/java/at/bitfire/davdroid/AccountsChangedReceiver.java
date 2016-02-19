/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.OnAccountsUpdateListener;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.util.LinkedList;
import java.util.List;

public class AccountsChangedReceiver extends BroadcastReceiver {

    protected static final List<OnAccountsUpdateListener> listeners = new LinkedList<>();

    @Override
    public void onReceive(Context context, Intent intent) {
        Intent serviceIntent = new Intent(context, DavService.class);
        serviceIntent.setAction(DavService.ACTION_ACCOUNTS_UPDATED);
        context.startService(serviceIntent);

        for (OnAccountsUpdateListener listener : listeners)
            listener.onAccountsUpdated(null);
    }

    public static void registerListener(OnAccountsUpdateListener listener, boolean callImmediately) {
        listeners.add(listener);
        if (callImmediately)
            listener.onAccountsUpdated(null);
    }

    public static void unregisterListener(OnAccountsUpdateListener listener) {
        listeners.remove(listener);
    }

}
