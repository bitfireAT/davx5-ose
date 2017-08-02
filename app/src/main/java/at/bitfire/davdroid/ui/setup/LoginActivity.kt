/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.ui.setup

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem
import at.bitfire.davdroid.CustomCertificates
import at.bitfire.davdroid.R
import java.util.*

/**
 * Activity to initially connect to a server and create an account.
 * Fields for server/user data can be pre-filled with extras in the Intent.
 */
class LoginActivity: AppCompatActivity() {

    companion object {
        /**
         * When set, "login by URL" will be activated by default, and the URL field will be set to this value.
         * When not set, "login by email" will be activated by default.
         */
        @JvmField
        val EXTRA_URL = "url"

        /**
         * When set, and {@link #EXTRA_PASSWORD} is set too, the user name field will be set to this value.
         * When set, and {@link #EXTRA_URL} is not set, the email address field will be set to this value.
         */
        @JvmField
        val EXTRA_USERNAME = "username"

        /**
         * When set, the password field will be set to this value.
         */
        @JvmField
        val EXTRA_PASSWORD = "password"
    }

    val loginFragmentLoader = ServiceLoader.load(ILoginCredentialsFragment::class.java)!!


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null)
            // first call, add fragment
            fragmentManager.beginTransaction()
                    .replace(android.R.id.content, loginFragmentLoader.first().getFragment())
                    .commit()
    }

    override fun onResume() {
        super.onResume()
        CustomCertificates.certManager?.let { it.appInForeground = true }
    }

    override fun onPause() {
        super.onPause()
        CustomCertificates.certManager?.let { it.appInForeground = false }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.activity_login, menu)
        return true
    }

    fun showHelp(item: MenuItem) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.login_help_url))))
    }

}