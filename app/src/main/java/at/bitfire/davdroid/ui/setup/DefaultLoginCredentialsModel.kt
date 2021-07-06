/*
 * Copyright © Ricki Hirner (bitfire web engineering) and other contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.ui.setup

import android.app.Application
import android.content.Context
import android.content.Intent
import android.text.Editable
import android.widget.ArrayAdapter
import android.widget.Filter
import android.widget.RadioGroup
import androidx.annotation.MainThread
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import at.bitfire.davdroid.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.InputStreamReader
import java.util.regex.Pattern

class DefaultLoginCredentialsModel(app: Application): AndroidViewModel(app) {

    private var initialized = false

    val loginWithEmailAddress = MutableLiveData<Boolean>()
    val loginWithUrlAndUsername = MutableLiveData<Boolean>()
    val loginAdvanced = MutableLiveData<Boolean>()

    val baseUrlAdapter = MutableLiveData<LoginUrlAdapter>()
    val baseUrl = MutableLiveData<String>()
    val baseUrlError = MutableLiveData<String>()

    /** user name or email address */
    val username = MutableLiveData<String>()
    val usernameError = MutableLiveData<String>()

    val password = MutableLiveData<String>()
    val passwordError = MutableLiveData<String>()

    val certificateAlias = MutableLiveData<String>()
    val certificateAliasError = MutableLiveData<String>()

    val loginUseUsernamePassword = MutableLiveData<Boolean>()
    val loginUseClientCertificate = MutableLiveData<Boolean>()

    init {
        loginWithEmailAddress.value = true
        loginUseClientCertificate.value = false
        loginUseUsernamePassword.value = false
    }

    fun clearUrlError(s: Editable) {
        if (s.toString() != "https://") {
            baseUrlError.value = null
        }
    }

    fun clearUsernameError(s: Editable) {
        usernameError.value = null
    }

    fun clearPasswordError(s: Editable) {
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

        // load base URL presets
        viewModelScope.launch(Dispatchers.IO) {
            baseUrlAdapter.postValue(LoginUrlAdapter(getApplication()))
        }
    }


    class LoginUrlAdapter(context: Context): ArrayAdapter<String>(context, R.layout.text_list_item, android.R.id.text1) {

        /**
         * list of known host names/domains (without https://), like "example.com" or "carddav.example.com"
         */
        val knownUrls = mutableListOf<String>()

        init {
            InputStreamReader(context.assets.open("known-base-urls.txt")).use { reader ->
                knownUrls.addAll(reader.readLines())
            }
        }

        override fun getFilter(): Filter = object: Filter() {
            override fun performFiltering(constraint: CharSequence): FilterResults {
                val str = constraint.removePrefix("https://").toString()
                var results = if (str.isEmpty())
                    knownUrls
                else {
                    val regex = Pattern.compile("(\\.|\\b)" + Pattern.quote(str))
                    knownUrls.filter { url ->
                        regex.matcher(url).find()
                    }.map { url -> "https://" + url }
                }
                return FilterResults().apply {
                    values = results
                    count = results.size
                }
            }
            override fun publishResults(constraint: CharSequence?, results: FilterResults) {
                clear()
                (results.values as List<String>?)?.let { suggestions ->
                    addAll(suggestions)
                }
            }
        }

    }

}
