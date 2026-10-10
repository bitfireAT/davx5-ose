/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.util

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import android.os.Bundle
import java.util.logging.Level
import java.util.logging.Logger

object AndroidAccountUtils {

    private val logger
        get() = Logger.getLogger(javaClass.name)

    /**
     * Creates a system account and makes sure the user data are set correctly.
     *
     * @param context  operating context
     * @param account  account to create
     * @param userData user data to set
     * @param password password to set
     *
     * @return whether the account has been created; false also when Android denies creation
     * with a [SecurityException], which is logged with available registered provider information
     */
    fun createAccount(
        context: Context,
        account: Account,
        userData: Map<String, String> = emptyMap(),
        password: SensitiveString? = null
    ): Boolean {
        val userDataBundle = Bundle(userData.size).apply {
            for ((key, value) in userData)
                putString(key, value)
        }

        // create account
        val manager = AccountManager.get(context)
        val created = try {
            manager.addAccountExplicitly(account, password?.asString(), userDataBundle)
        } catch (e: SecurityException) {
            logger.log(Level.WARNING, "Account creation denied", e)
            try {
                val providers = manager.authenticatorTypes
                    .filter { it.type == account.type }
                    .map { it.packageName }
                logger.warning("Registered account providers: ${providers.joinToString().ifEmpty { "unknown (none visible)" }}")
            } catch (e: RuntimeException) {
                logger.log(Level.WARNING, "Registered account providers unavailable", e)
            }
            return false
        }
        if (!created)
            return false

        // Android seems to lose the initial user data sometimes, so make sure that the values are set
        for ((key, value) in userData)
            manager.setAndVerifyUserData(account, key, value)

        return true
    }

}
