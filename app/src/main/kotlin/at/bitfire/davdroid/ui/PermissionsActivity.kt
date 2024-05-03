/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity

class PermissionsActivity: AppCompatActivity() {

    val model by viewModels<PermissionsModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AppTheme {
                PermissionsScreen(
                    model = model,
                    onNavigateUp = ::onSupportNavigateUp
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        model.checkPermissions()
    }

}
