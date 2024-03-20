/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui.widget

import android.accounts.Account
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.AlertDialog
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Error
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import at.bitfire.dav4jvm.exception.HttpException
import at.bitfire.davdroid.R
import at.bitfire.davdroid.ui.DebugInfoActivity
import okhttp3.HttpUrl
import java.io.IOException

@Composable
fun ExceptionInfoDialog(
    exception: Throwable,
    account: Account? = null,
    remoteResource: HttpUrl? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    val titleRes = when (exception) {
        is HttpException -> R.string.exception_httpexception
        is IOException   -> R.string.exception_ioexception
        else -> R.string.exception
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Error, null)
                Text(
                    text = stringResource(titleRes),
                    modifier = Modifier.weight(1f).padding(start = 8.dp)
                )
            }
        },
        text = {
            Text(
                exception::class.java.name + "\n" + exception.localizedMessage,
                style = MaterialTheme.typography.body1
            )
        },
        dismissButton = {
            TextButton(
                onClick = {
                    val intent = DebugInfoActivity.IntentBuilder(context).withCause(exception)
                    if (account != null)
                        intent.withAccount(account)
                    if (remoteResource != null)
                        intent.withRemoteResource(remoteResource)
                    context.startActivity(intent.build())
                }
            ) {
                Text(stringResource(R.string.exception_show_details).uppercase())
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.ok).uppercase())
            }
        }
    )
}