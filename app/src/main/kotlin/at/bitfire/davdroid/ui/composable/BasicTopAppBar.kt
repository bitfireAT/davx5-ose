package at.bitfire.davdroid.ui.composable

import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import at.bitfire.davdroid.R

@Composable
fun BasicTopAppBar(
    @StringRes titleStringRes: Int,
    onSupportNavigateUp: () -> Unit
) {
    TopAppBar(
        title = { Text(stringResource(titleStringRes)) },
        navigationIcon = {
            IconButton(
                onClick = onSupportNavigateUp
            ) {
                Icon(Icons.AutoMirrored.Default.ArrowBack, stringResource(R.string.navigate_up))
            }
        }
    )
}

@Composable
fun AppCompatActivity.BasicTopAppBar(@StringRes titleStringRes: Int) {
    BasicTopAppBar(titleStringRes, ::onSupportNavigateUp)
}
