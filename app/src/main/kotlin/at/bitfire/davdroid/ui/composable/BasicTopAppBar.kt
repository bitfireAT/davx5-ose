package at.bitfire.davdroid.ui.composable

import android.app.Activity
import android.content.Intent
import androidx.annotation.StringRes
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import at.bitfire.davdroid.R
import kotlin.reflect.KClass

@Composable
fun Activity.BasicTopAppBar(@StringRes titleStringRes: Int, parentActivity: KClass<*>) {
    TopAppBar(
        title = { Text(stringResource(titleStringRes)) },
        navigationIcon = {
            IconButton(
                onClick = {
                    // Finish the current activity
                    finish()
                    // And start the parent one
                    startActivity(Intent(this, parentActivity.java))
                }
            ) {
                Icon(Icons.AutoMirrored.Default.ArrowBack, stringResource(R.string.back))
            }
        }
    )
}
