/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.composable

import android.accounts.Account
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import at.bitfire.dav4jvm.okhttp.exception.HttpException
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
            Text(
                text = stringResource(titleRes)
            )
        },
        icon = {
            Icon(Icons.Rounded.Error, null)
        },
        text = {
            val message = if (exception is HttpException) {
                 when (exception.statusCode) {
                    403 -> context.getString(R.string.debug_info_http_403_description)
                    404 -> context.getString(R.string.debug_info_http_404_description)
                    405 -> context.getString(R.string.debug_info_http_405_description)
                     in 500..599 -> context.getString(R.string.debug_info_http_5xx_description)
                    else -> null
                }
            } else null
            Text(
                text = message ?: "${exception::class.java.name}\n${exception.localizedMessage}"
            )
        },
        dismissButton = {
            OutlinedButton(
                onClick = {
                    val intent = DebugInfoActivity.IntentBuilder(context).withCause(exception)
                    if (account != null)
                        intent.withAccount(account)
                    if (remoteResource != null)
                        intent.withRemoteResource(remoteResource)
                    context.startActivity(intent.build())
                }
            ) {
                Text(stringResource(R.string.exception_show_details))
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(stringResource(android.R.string.ok))
            }
        }
    )
}

@Composable
@Preview
fun ExceptionInfoDialog_Preview() {
    ExceptionInfoDialog(
        exception = Exception("Test exception"),
        onDismiss = {}
    )
}