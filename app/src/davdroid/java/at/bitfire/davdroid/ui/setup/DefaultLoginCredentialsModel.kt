package at.bitfire.davdroid.ui.setup

import android.content.Intent
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class DefaultLoginCredentialsModel: ViewModel() {

    private var initialized = false

    val loginWithEmailAddress = MutableLiveData<Boolean>()
    val loginWithUrlAndUsername = MutableLiveData<Boolean>()
    val loginWithUrlAndCertificate = MutableLiveData<Boolean>()

    val baseUrl = MutableLiveData<String>()
    val baseUrlError = MutableLiveData<String>()

    val emailAddress = MutableLiveData<String>()
    val emailAddressError = MutableLiveData<String>()

    val username = MutableLiveData<String>()
    val usernameError = MutableLiveData<String>()

    val password = MutableLiveData<String>()
    val passwordError = MutableLiveData<String>()

    val certificateAlias = MutableLiveData<String>()
    val certificateAliasError = MutableLiveData<String>()

    init {
        loginWithEmailAddress.value = true
    }

    fun initialize(intent: Intent) {
        if (initialized)
            return

        // we've got initial login data
        val givenUrl = intent.getStringExtra(LoginActivity.EXTRA_URL)
        val givenUsername = intent.getStringExtra(LoginActivity.EXTRA_USERNAME)
        val givenPassword = intent.getStringExtra(LoginActivity.EXTRA_PASSWORD)

        if (givenUrl != null) {
            loginWithUrlAndUsername.value = true
            baseUrl.value = givenUrl
        } else {
            loginWithEmailAddress.value = true
            emailAddress.value = givenUsername
        }
        password.value = givenPassword

        initialized = true
    }

}