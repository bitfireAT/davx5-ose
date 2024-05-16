/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.composable

import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import at.bitfire.davdroid.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Deprecated("Directly use TopAppBar instead.", replaceWith = ReplaceWith("TopAppBar"))
fun BasicTopAppBar(
    @StringRes titleStringRes: Int,
    actions: @Composable () -> Unit = {},
    onNavigateUp: () -> Unit
) {
    TopAppBar(
        title = { Text(stringResource(titleStringRes)) },
        navigationIcon = {
            IconButton(
                onClick = onNavigateUp
            ) {
                Icon(Icons.AutoMirrored.Default.ArrowBack, stringResource(R.string.navigate_up))
            }
        }
    )
}