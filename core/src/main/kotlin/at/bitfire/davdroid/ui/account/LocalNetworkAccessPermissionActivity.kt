/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.account

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class LocalNetworkAccessPermissionActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_HOSTNAME = "hostname"

        fun createIntent(context: Context, hostname: String): Intent {
            return Intent(context, LocalNetworkAccessPermissionActivity::class.java).apply {
                putExtra(EXTRA_HOSTNAME, hostname)
            }
        }
    }

    private val hostname: String by lazy {
        intent.getStringExtra(EXTRA_HOSTNAME)
            ?: throw IllegalArgumentException("EXTRA_HOSTNAME must be set")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            LocalNetworkAccessPermissionScreen(
                hostname = hostname,
                onNavUp = ::onSupportNavigateUp
            )
        }
    }

    override fun supportShouldUpRecreateTask(targetIntent: Intent) = true

}
