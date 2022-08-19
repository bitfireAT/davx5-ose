/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui.setup

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import at.bitfire.davdroid.App
import at.bitfire.davdroid.R
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.ui.UiUtils
import com.infomaniak.sync.ui.InfomaniakDetectConfigurationFragment
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Activity to initially connect to a server and create an account.
 * Fields for server/user data can be pre-filled with extras in the Intent.
 */
@AndroidEntryPoint
class LoginActivity: AppCompatActivity() {

    companion object {

        /**
         * When set, "login by URL" will be activated by default, and the URL field will be set to this value.
         * When not set, "login by email" will be activated by default.
         */
        const val EXTRA_URL = "url"

        /**
         * When set, and {@link #EXTRA_PASSWORD} is set too, the user name field will be set to this value.
         * When set, and {@link #EXTRA_URL} is not set, the email address field will be set to this value.
         */
        const val EXTRA_USERNAME = "username"

        /**
         * When set, the password field will be set to this value.
         */
        const val EXTRA_PASSWORD = "password"

    }

//    @Inject
//    lateinit var loginFragmentFactories: Map<Int, @JvmSuppressWildcards LoginCredentialsFragmentFactory>


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            // first call, add first login fragment
//            val factories = loginFragmentFactories      // get factories from hilt
//                .toSortedMap()                          // sort by Int key
//                .values.reversed()                      // take reverse-sorted values (because high priority numbers shall be processed first)
            var fragment: Fragment? = null
//            for (factory in factories) {
//                Logger.log.info("Login fragment factory: $factory")
//                fragment = fragment ?: factory.getFragment(intent)
//            }

            if (intent != null) {
                val code = intent.getStringExtra("code")
                if (!code.isNullOrBlank()) {
                    fragment = InfomaniakDetectConfigurationFragment.newInstance(code)
                }
            } else {
                Toast.makeText(this, getString(R.string.an_error_has_occurred), Toast.LENGTH_LONG).show()
                onBackPressed()
            }

            if (fragment != null) {
                supportFragmentManager.beginTransaction()
                    .replace(android.R.id.content, fragment)
                    .commit()
            } else
                Logger.log.severe("Couldn't create LoginFragment")
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.activity_login, menu)
        return true
    }

    fun showHelp(item: MenuItem) {
        UiUtils.launchUri(this,
            App.homepageUrl(this).buildUpon().appendPath("tested-with").build())
    }

}
