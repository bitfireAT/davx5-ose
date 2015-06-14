/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */
package at.bitfire.davdroid.syncadapter;

import android.accounts.AbstractAccountAuthenticator;
import android.accounts.Account;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.AccountManager;
import android.accounts.NetworkErrorException;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;

import at.bitfire.davdroid.ui.setup.AddAccountActivity;

public class AccountAuthenticatorService extends Service {
	private static AccountAuthenticator accountAuthenticator;

	private AccountAuthenticator getAuthenticator() {
		if (accountAuthenticator != null)
			return accountAuthenticator;
		return accountAuthenticator = new AccountAuthenticator(this);
	}
	
	@Override
	public IBinder onBind(Intent intent) {
		if (intent.getAction().equals(android.accounts.AccountManager.ACTION_AUTHENTICATOR_INTENT))
			return getAuthenticator().getIBinder(); 
		return null;
	}
	
	
	private static class AccountAuthenticator extends AbstractAccountAuthenticator {
		final Context context;
		
		public AccountAuthenticator(Context context) {
			super(context);
			this.context = context;
		}

		@Override
		public Bundle addAccount(AccountAuthenticatorResponse response, String accountType, String authTokenType,
				String[] requiredFeatures, Bundle options) throws NetworkErrorException {
			Intent intent = new Intent(context, AddAccountActivity.class);
			intent.putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, response);
			Bundle bundle = new Bundle();
			bundle.putParcelable(AccountManager.KEY_INTENT, intent);
			return bundle;
		}

		@Override
		public Bundle confirmCredentials(AccountAuthenticatorResponse response, Account account, Bundle options) throws NetworkErrorException {
			return null;
		}

		@Override
		public Bundle editProperties(AccountAuthenticatorResponse response, String accountType) {
			return null;
		}

		@Override
		public Bundle getAuthToken(AccountAuthenticatorResponse response, Account account, String authTokenType, Bundle options) throws NetworkErrorException {
			return null;
		}

		@Override
		public String getAuthTokenLabel(String authTokenType) {
			return null;
		}

		@Override
		public Bundle hasFeatures(AccountAuthenticatorResponse response, Account account, String[] features) throws NetworkErrorException {
			return null;
		}

		@Override
		public Bundle updateCredentials(AccountAuthenticatorResponse response, Account account, String authTokenType, Bundle options) throws NetworkErrorException {
			return null;
		}
	}
}
