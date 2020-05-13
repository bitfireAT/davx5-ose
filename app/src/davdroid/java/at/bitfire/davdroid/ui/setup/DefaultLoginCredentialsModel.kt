/*
 * Copyright © Ricki Hirner (bitfire web engineering) and other contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.ui.setup

import android.content.Intent
import android.widget.RadioGroup
import androidx.annotation.MainThread
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class DefaultLoginCredentialsModel: ViewModel() {

    private var initialized = false

    val loginWithEmailAddress = MutableLiveData<Boolean>()
    val loginWithUrlAndUsername = MutableLiveData<Boolean>()
    val loginWithUrlAndCertificate = MutableLiveData<Boolean>()

    val baseUrl = MutableLiveData<String>()
    val baseUrlError = MutableLiveData<String>()

    /** user name or email address */
    val username = MutableLiveData<String>()
    val usernameError = MutableLiveData<String>()

    val password = MutableLiveData<String>()
    val passwordError = MutableLiveData<String>()

    val certificateAlias = MutableLiveData<String>()
    val certificateAliasError = MutableLiveData<String>()

    init {
        loginWithEmailAddress.value = true
    }

    fun clearPasswordError(s: CharSequence, start: Int, before: Int, count: Int) {
        passwordError.value = null
    }

    fun clearErrors(group: RadioGroup, checkedId: Int) {
        usernameError.value = null
        passwordError.value = null
        baseUrlError.value = null
    }

    @MainThread
    fun initialize(intent: Intent) {
        if (initialized)
            return
        initialized = true

        // we've got initial login data
        val givenUrl = intent.getStringExtra(LoginActivity.EXTRA_URL)
        val givenUsername = intent.getStringExtra(LoginActivity.EXTRA_USERNAME)
        val givenPassword = intent.getStringExtra(LoginActivity.EXTRA_PASSWORD)

        if (givenUrl != null) {
            loginWithUrlAndUsername.value = true
            baseUrl.value = givenUrl
        } else
            loginWithEmailAddress.value = true
        username.value = givenUsername
        password.value = givenPassword
    }

}
