/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.ui.setup

import android.content.Intent
import android.net.MailTo
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.security.KeyChain
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import at.bitfire.davdroid.R
import at.bitfire.davdroid.databinding.LoginCredentialsFragmentBinding
import at.bitfire.davdroid.model.Credentials
import java.net.URI
import java.net.URISyntaxException

class DefaultLoginCredentialsFragment : Fragment() {

    val loginModel by activityViewModels<LoginModel>()
    val model by viewModels<DefaultLoginCredentialsModel>()


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val v = LoginCredentialsFragmentBinding.inflate(inflater, container, false)
        v.lifecycleOwner = viewLifecycleOwner
        v.model = model

        // initialize model on first call
        if (savedInstanceState == null)
            activity?.intent?.let { model.initialize(it) }

        v.selectCertificate.setOnClickListener {
            KeyChain.choosePrivateKeyAlias(requireActivity(), { alias ->
                Handler(Looper.getMainLooper()).post {
                    model.certificateAlias.value = alias
                }
            }, null, null, null, -1, model.certificateAlias.value)
        }


        v.login.setOnClickListener {
            if (validate())
                parentFragmentManager.beginTransaction()
                        .replace(android.R.id.content, DetectConfigurationFragment(), null)
                        .addToBackStack(null)
                        .commit()
        }

        return v.root
    }

    private fun validate(): Boolean {
        var valid = false

        fun validateUrl() {
            model.baseUrlError.value = null
            try {
                val originalUrl = model.baseUrl.value.orEmpty()
                val uri = URI(originalUrl)
                if (uri.scheme.equals("http", true) || uri.scheme.equals("https", true)) {
                    // http:// or https:// scheme → OK
                    valid = true
                    loginModel.baseURI = uri
                } else if (uri.scheme == null) {
                    // empty URL scheme, assume https://
                    model.baseUrl.value = "https://$originalUrl"
                    validateUrl()
                } else
                    model.baseUrlError.value = getString(R.string.login_url_must_be_http_or_https)
            } catch (e: Exception) {
                model.baseUrlError.value = e.localizedMessage
            }
        }

        fun validatePassword(): String? {
            model.passwordError.value = null
            val password = model.password.value
            if (password.isNullOrEmpty()) {
                valid = false
                model.passwordError.value = getString(R.string.login_password_required)
            }
            return password
        }

        when {
            model.loginWithEmailAddress.value == true -> {
                // login with email address
                model.usernameError.value = null
                val email = model.username.value.orEmpty()
                if (email.matches(Regex(".+@.+"))) {
                    // already looks like an email address
                    try {
                        loginModel.baseURI = URI(MailTo.MAILTO_SCHEME, email, null)
                        valid = true
                    } catch (e: URISyntaxException) {
                        model.usernameError.value = e.localizedMessage
                    }
                } else {
                    valid = false
                    model.usernameError.value = getString(R.string.login_email_address_error)
                }

                val password = validatePassword()

                if (valid)
                    loginModel.credentials = Credentials(email, password, null)
            }

            model.loginWithUrlAndUsername.value == true -> {
                validateUrl()

                model.usernameError.value = null
                val username = model.username.value
                if (username.isNullOrEmpty()) {
                    valid = false
                    model.usernameError.value = getString(R.string.login_user_name_required)
                }

                val password = validatePassword()

                if (valid)
                    loginModel.credentials = Credentials(username, password, null)
            }


            model.loginAdvanced.value == true -> {
                validateUrl()

                model.certificateAliasError.value = null
                val alias = model.certificateAlias.value
                if (model.loginUseClientCertificate.value == true && alias.isNullOrBlank()) {
                    valid = false
                    model.certificateAliasError.value = ""      // error icon without text
                }


                model.usernameError.value = null
                model.passwordError.value = null
                val username = model.username.value
                val password = model.password.value

                if (model.loginUseUsernamePassword.value == true) {
                    if (username.isNullOrEmpty()) {
                        valid = false
                        model.usernameError.value = getString(R.string.login_user_name_required)
                    }

                    if (password.isNullOrEmpty()) {
                        valid = false
                        model.passwordError.value = getString(R.string.login_password_required)
                    }
                }


                //loginModel.credentials stays null if Login is tried with Base URL only!
                if (valid && model.loginUseUsernamePassword.value == true && model.loginUseClientCertificate.value == true)
                    loginModel.credentials = Credentials(username, password, alias)
                if (valid && model.loginUseUsernamePassword.value == true && model.loginUseClientCertificate.value == false)
                    loginModel.credentials = Credentials(username, password, null)
                if (valid && model.loginUseUsernamePassword.value == false && model.loginUseClientCertificate.value == true)
                    loginModel.credentials = Credentials(null, null, alias)

            }

        }

        return valid
    }


    class Factory : LoginCredentialsFragment {

        override fun getFragment(intent: Intent) = DefaultLoginCredentialsFragment()

    }

}
