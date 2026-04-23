/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.push

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import at.bitfire.davdroid.R
import at.bitfire.davdroid.settings.Settings
import at.bitfire.davdroid.settings.SettingsManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.unifiedpush.android.connector.LinkActivityHelper
import javax.inject.Inject

@AndroidEntryPoint
class PushDistributorSelectionActivity : AppCompatActivity() {
    private val helper = LinkActivityHelper(this)

    @Inject
    lateinit var registrationManager: PushRegistrationManager

    @Inject
    lateinit var settings : SettingsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!helper.startLinkActivityForResult()) {
            // No distributor found
            AlertDialog.Builder(this)
                .setTitle(R.string.push_no_distributor_title)
                .setMessage(R.string.push_no_distributor_message)
                .setPositiveButton(R.string.push_no_distributor_more_info) { _, _ ->
                    startActivity(
                        Intent(Intent.ACTION_VIEW, "https://manual.davx5.com/webdav_push.html".toUri())
                    )
                    finish()
                }
                .setNegativeButton(R.string.push_no_distributor_disable) { _, _ ->
                    settings.putBoolean(Settings.PUSH_DISABLED, true)
                    Toast.makeText(this, R.string.push_disabled_message, Toast.LENGTH_SHORT).show()
                    finish()
                }
                .setOnCancelListener { finish() }
                .show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (helper.onLinkActivityResult(requestCode, resultCode, data)) {
            // The distributor is saved, reschedule worker
            lifecycleScope.launch(Dispatchers.IO) {
                registrationManager.update()

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@PushDistributorSelectionActivity, R.string.push_distributor_selected, Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        } else {
            // An error occurred, consider no distributor found for the moment
            super.onActivityResult(requestCode, resultCode, data)
            finish()
        }
    }
}
