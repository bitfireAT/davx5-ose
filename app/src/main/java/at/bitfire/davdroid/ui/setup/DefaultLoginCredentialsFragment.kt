/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.ui.setup

import android.app.Fragment
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import at.bitfire.dav4android.Constants
import at.bitfire.davdroid.R
import kotlinx.android.synthetic.main.login_credentials_fragment.view.*
import java.net.IDN
import java.net.URI
import java.net.URISyntaxException
import java.util.logging.Level

class DefaultLoginCredentialsFragment(): Fragment(), CompoundButton.OnCheckedChangeListener {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val v = inflater.inflate(R.layout.login_credentials_fragment, container, false)

        if (savedInstanceState == null) {
            // first call
            activity?.intent?.let {
                // we've got initial login data
                val url = it.getStringExtra(LoginActivity.EXTRA_URL)
                val username = it.getStringExtra(LoginActivity.EXTRA_USERNAME)
                val password = it.getStringExtra(LoginActivity.EXTRA_PASSWORD)

                if (url != null) {
                    v.login_type_url.isChecked = true
                    v.base_url.setText(url)
                    v.user_name.setText(username)
                    v.url_password.setText(password)
                } else {
                    v.login_type_email.isChecked = true
                    v.email_address.setText(username)
                    v.email_password.setText(password)
                }
            }
        }

        v.login.setOnClickListener(View.OnClickListener() { _ ->
            validateLoginData()?.let { credentials ->
                DetectConfigurationFragment.newInstance(credentials).show(fragmentManager, null)
            }
        })

        // initialize to Login by email
        onCheckedChanged(v)

        v.login_type_email.setOnCheckedChangeListener(this)
        v.login_type_url.setOnCheckedChangeListener(this)

        return v
    }

    override fun onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) {
        onCheckedChanged(view)
    }

    private fun onCheckedChanged(v: View) {
        val loginByEmail = !v.login_type_url.isChecked
        v.login_type_email_details.visibility = if (loginByEmail) View.VISIBLE else View.GONE
        v.login_type_url_details.visibility = if (loginByEmail) View.GONE else View.VISIBLE
        (if (loginByEmail) v.email_address else v.base_url).requestFocus()
    }

    private fun validateLoginData(): LoginCredentials? {
        if (view.login_type_email.isChecked) {
            var uri: URI? = null
            var valid = true

            val email = view.email_address.text.toString()
            if (!email.matches(Regex(".+@.+"))) {
                view.email_address.error = getString(R.string.login_email_address_error)
                valid = false
            } else
                try {
                    uri = URI("mailto", email, null)
                } catch(e: URISyntaxException) {
                    view.email_address.error = e.localizedMessage
                    valid = false
                }

            val password = view.email_password.getText().toString()
            if (password.isEmpty()) {
                view.email_password.setError(getString(R.string.login_password_required))
                valid = false
            }

            return if (valid && uri != null)
                LoginCredentials(uri, email, password)
            else
                null

        } else if (view.login_type_url.isChecked) {
            var uri: URI? = null
            var valid = true

            val baseUrl = Uri.parse(view.base_url.text.toString())
            val scheme = baseUrl.scheme
            if (scheme.equals("http", true) || scheme.equals("https", true)) {
                var host = baseUrl.host
                if (host.isNullOrBlank()) {
                    view.base_url.error = getString(R.string.login_url_host_name_required)
                    valid = false
                } else
                    try {
                        host = IDN.toASCII(host)
                    } catch(e: IllegalArgumentException) {
                        Constants.log.log(Level.WARNING, "Host name not conforming to RFC 3490", e)
                    }

                val path = baseUrl.encodedPath
                val port = baseUrl.port
                try {
                    uri = URI(baseUrl.scheme, null, host, port, path, null, null)
                } catch(e: URISyntaxException) {
                    view.base_url.error = e.localizedMessage
                    valid = false
                }
            } else {
                view.base_url.error = getString(R.string.login_url_must_be_http_or_https)
                valid = false
            }

            val userName = view.user_name.text.toString()
            if (userName.isBlank()) {
                view.user_name.error = getString(R.string.login_user_name_required)
                valid = false
            }

            val password = view.url_password.getText().toString()
            if (password.isEmpty()) {
                view.url_password.setError(getString(R.string.login_password_required))
                valid = false
            }

            return if (valid && uri != null)
                LoginCredentials(uri, userName, password)
            else
                null
        }

        return null;
    }

}
