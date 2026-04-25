/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.push

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import at.bitfire.davdroid.ui.composable.AppTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class PushSettingsActivity: AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val viewModel = viewModel<PushSettingsViewModel>()
            val state by viewModel.uiState.collectAsStateWithLifecycle()

            AppTheme {
                PushSettingsScreen(
                    state = state,
                    onNavUp = ::onSupportNavigateUp
                )
            }
        }
    }
}
