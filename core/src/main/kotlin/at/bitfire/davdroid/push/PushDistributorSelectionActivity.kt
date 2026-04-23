/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.push

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!helper.startLinkActivityForResult()) {
            // No distributor found
            finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (helper.onLinkActivityResult(requestCode, resultCode, data)) {
            // The distributor is saved, reschedule worker
            lifecycleScope.launch(Dispatchers.IO) {
                registrationManager.update()

                withContext(Dispatchers.Main) {
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
