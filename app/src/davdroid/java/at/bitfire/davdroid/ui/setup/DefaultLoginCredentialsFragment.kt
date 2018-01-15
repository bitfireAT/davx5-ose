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
import android.os.Handler
import android.os.Looper
import android.security.KeyChain
import android.security.KeyChainAliasCallback
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.Toast
import at.bitfire.dav4android.Constants
import at.bitfire.davdroid.R
import kotlinx.android.synthetic.standard.login_credentials_fragment.view.*
import java.net.IDN
import java.net.URI
import java.net.URISyntaxException
import java.util.logging.Level

class DefaultLoginCredentialsFragment: Fragment(), CompoundButton.OnCheckedChangeListener {

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
                    v.login_type_urlpwd.isChecked = true
                    v.urlpwd_base_url.setText(url)
                    v.urlpwd_user_name.setText(username)
                    v.urlpwd_password.setText(password)
                } else {
                    v.login_type_email.isChecked = true
                    v.email_address.setText(username)
                    v.email_password.setText(password)
                }
            }
        }

        v.urlcert_select_cert.setOnClickListener {
            KeyChain.choosePrivateKeyAlias(activity, KeyChainAliasCallback { alias ->
                Handler(Looper.getMainLooper()).post({
                    v.urlcert_cert_alias.text = alias
                    v.urlcert_cert_alias.error = null
                })
            }, null, null, null, -1, view.urlcert_cert_alias.text.toString())
        }

        v.login.setOnClickListener {
            validateLoginData()?.let { info ->
                DetectConfigurationFragment.newInstance(info).show(fragmentManager, null)
            }
        }

        // initialize to Login by email
        onCheckedChanged(v)

        v.login_type_email.setOnCheckedChangeListener(this)
        v.login_type_urlpwd.setOnCheckedChangeListener(this)
        v.login_type_urlcert.setOnCheckedChangeListener(this)

        return v
    }

    override fun onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) {
        onCheckedChanged(view)
    }

    private fun onCheckedChanged(v: View) {
        v.login_type_email_details.visibility = if (v.login_type_email.isChecked) View.VISIBLE else View.GONE
        v.login_type_urlpwd_details.visibility = if (v.login_type_urlpwd.isChecked) View.VISIBLE else View.GONE
        v.login_type_urlcert_details.visibility = if (v.login_type_urlcert.isChecked) View.VISIBLE else View.GONE
    }

    private fun validateLoginData(): LoginInfo? {
        when {
            // Login with email address
            view.login_type_email.isChecked -> {
                var uri: URI? = null
                var valid = true

                val email = view.email_address.text.toString()
                if (!email.matches(Regex(".+@.+"))) {
                    view.email_address.error = getString(R.string.login_email_address_error)
                    valid = false
                } else
                    try {
                        uri = URI("mailto", email, null)
                    } catch (e: URISyntaxException) {
                        view.email_address.error = e.localizedMessage
                        valid = false
                    }

                val password = view.email_password.getText().toString()
                if (password.isEmpty()) {
                    view.email_password.error = getString(R.string.login_password_required)
                    valid = false
                }

                return if (valid && uri != null)
                    LoginInfo(uri, email, password)
                else
                    null

            }

            // Login with URL and user name
            view.login_type_urlpwd.isChecked -> {
                var valid = true

                val baseUrl = Uri.parse(view.urlpwd_base_url.text.toString())
                val uri = validateBaseUrl(baseUrl, false, { message ->
                    view.urlpwd_base_url.error = message
                    valid = false
                })

                val userName = view.urlpwd_user_name.text.toString()
                if (userName.isBlank()) {
                    view.urlpwd_user_name.error = getString(R.string.login_user_name_required)
                    valid = false
                }

                val password = view.urlpwd_password.text.toString()
                if (password.isEmpty()) {
                    view.urlpwd_password.error = getString(R.string.login_password_required)
                    valid = false
                }

                return if (valid && uri != null)
                    LoginInfo(uri, userName, password)
                else
                    null
            }

            // Login with URL and client certificate
            view.login_type_urlcert.isChecked -> {
                var valid = true

                val baseUrl = Uri.parse(view.urlcert_base_url.text.toString())
                val uri = validateBaseUrl(baseUrl, true, { message ->
                    view.urlcert_base_url.error = message
                    valid = false
                })

                val alias = view.urlcert_cert_alias.text.toString()
                if (alias.isEmpty()) {
                    view.urlcert_cert_alias.error = ""
                    valid = false
                }

                if (valid && uri != null)
                    return LoginInfo(uri, certificateAlias = alias)
            }
        }

        return null
    }

    private fun validateBaseUrl(baseUrl: Uri, httpsRequired: Boolean, reportError: (String) -> Unit): URI? {
        var uri: URI? = null
        val scheme = baseUrl.scheme
        if ((!httpsRequired && scheme.equals("http", true)) || scheme.equals("https", true)) {
            var host = baseUrl.host
            if (host.isNullOrBlank())
                reportError(getString(R.string.login_url_host_name_required))
            else
                try {
                    host = IDN.toASCII(host)
                } catch (e: IllegalArgumentException) {
                    Constants.log.log(Level.WARNING, "Host name not conforming to RFC 3490", e)
                }

            val path = baseUrl.encodedPath
            val port = baseUrl.port
            try {
                uri = URI(baseUrl.scheme, null, host, port, path, null, null)
            } catch (e: URISyntaxException) {
                reportError(e.localizedMessage)
            }
        } else
            reportError(getString(if (httpsRequired)
                    R.string.login_url_must_be_https
                else
                    R.string.login_url_must_be_http_or_https))
        return uri
    }


    class Factory: ILoginCredentialsFragment {

        override fun getFragment() = DefaultLoginCredentialsFragment()

    }

}
