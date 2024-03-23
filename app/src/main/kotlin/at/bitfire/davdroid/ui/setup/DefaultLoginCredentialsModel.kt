/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui.setup

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.Editable
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Filter
import android.widget.RadioGroup
import androidx.annotation.MainThread
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import at.bitfire.davdroid.R
import java.io.InputStreamReader
import java.util.regex.Pattern

class DefaultLoginCredentialsModel(app: Application): AndroidViewModel(app) {

    private var initialized = false

    val loginWithEmailAddress = MutableLiveData(true)
    val loginWithUrlAndUsername = MutableLiveData(false)
    val loginAdvanced = MutableLiveData(false)
    val loginGoogle = MutableLiveData(false)
    val loginNextcloud = MutableLiveData(false)

    val baseUrl = MutableLiveData<String>()
    val baseUrlError = MutableLiveData<String>()

    /** user name or email address */
    val username = MutableLiveData<String>()
    val usernameError = MutableLiveData<String>()

    val password = MutableLiveData<String>()
    val passwordError = MutableLiveData<String>()

    val certificateAlias = MutableLiveData<String>()
    val certificateAliasError = MutableLiveData<String>()

    val loginUseUsernamePassword = MutableLiveData(false)
    val loginUseClientCertificate = MutableLiveData(false)


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

        var givenUrl: String? = null
        var givenUsername: String? = null
        var givenPassword: String? = null

        intent.data?.normalizeScheme()?.let { uri ->
            // We've got initial login data from the Intent.
            // We can't use uri.buildUpon() because this keeps the user info (it's readable, but not writable).
            val realScheme = when (uri.scheme) {
                "caldav", "carddav" -> "http"
                "caldavs", "carddavs", "davx5" -> "https"
                "http", "https" -> uri.scheme
                else -> null
            }
            if (realScheme != null) {
                val realUri = Uri.Builder()
                    .scheme(realScheme)
                    .authority(uri.host)
                    .path(uri.path)
                    .query(uri.query)
                givenUrl = realUri.build().toString()

                // extract user info
                uri.userInfo?.split(':')?.let { userInfo ->
                    givenUsername = userInfo.getOrNull(0)
                    givenPassword = userInfo.getOrNull(1)
                }
            }
        }

        // no login data from the Intent, let's look up the extras
        givenUrl ?: intent.getStringExtra(LoginActivity2.EXTRA_URL)

        // always prefer username/password from the extras
        if (intent.hasExtra(LoginActivity2.EXTRA_USERNAME))
            givenUsername = intent.getStringExtra(LoginActivity2.EXTRA_USERNAME)
        if (intent.hasExtra(LoginActivity2.EXTRA_PASSWORD))
            givenPassword = intent.getStringExtra(LoginActivity2.EXTRA_PASSWORD)

        if (givenUrl != null) {
            loginWithUrlAndUsername.value = true
            baseUrl.value = givenUrl
        } else
            loginWithEmailAddress.value = true
        username.value = givenUsername
        password.value = givenPassword
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

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val v = super.getView(position, convertView, parent)
            v.findViewById<View>(android.R.id.text2).visibility = View.GONE
            return v
        }

        override fun getFilter(): Filter = object: Filter() {
            override fun performFiltering(constraint: CharSequence): FilterResults {
                val str = constraint.removePrefix("https://").toString()
                val results = if (str.isEmpty())
                    knownUrls
                else {
                    val regex = Pattern.compile("(\\.|\\b)" + Pattern.quote(str))
                    knownUrls.filter { url ->
                        regex.matcher(url).find()
                    }.map { url -> "https://$url" }
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
