/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.test.account

import android.accounts.AbstractAccountAuthenticator
import android.accounts.Account
import android.accounts.AccountAuthenticatorResponse
import android.accounts.AccountManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Bundle

/**
 * Minimal authenticator used only by instrumented tests.
 *
 * The tasks provider purges lists for unknown non-local accounts, so tests that need non-local
 * deletion semantics must register a real [AccountManager] account type first. This authenticator
 * exists only to make that account type valid during androidTest runs.
 */
class TestAccountAuthenticatorService : Service() {

    private lateinit var authenticator: Authenticator

    override fun onCreate() {
        authenticator = Authenticator(this)
    }

    override fun onBind(intent: Intent?) =
        authenticator.iBinder.takeIf { intent?.action == AccountManager.ACTION_AUTHENTICATOR_INTENT }


    private class Authenticator(
        context: Context
    ) : AbstractAccountAuthenticator(context) {

        override fun addAccount(
            response: AccountAuthenticatorResponse?,
            accountType: String?,
            authTokenType: String?,
            requiredFeatures: Array<String>?,
            options: Bundle?
        ) = Bundle().apply {
            putParcelable(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, response)
            putInt(AccountManager.KEY_ERROR_CODE, AccountManager.ERROR_CODE_UNSUPPORTED_OPERATION)
            putString(AccountManager.KEY_ERROR_MESSAGE, "androidTest authenticator is no-op")
        }

        override fun editProperties(response: AccountAuthenticatorResponse?, accountType: String?) = null
        override fun getAuthTokenLabel(authTokenType: String?) = null
        override fun confirmCredentials(response: AccountAuthenticatorResponse?, account: Account?, options: Bundle?) = null
        override fun updateCredentials(
            response: AccountAuthenticatorResponse?,
            account: Account?,
            authTokenType: String?,
            options: Bundle?
        ) = null

        override fun getAuthToken(
            response: AccountAuthenticatorResponse?,
            account: Account?,
            authTokenType: String?,
            options: Bundle?
        ) = null

        override fun hasFeatures(
            response: AccountAuthenticatorResponse?,
            account: Account?,
            features: Array<out String>?
        ) = null
    }

}
