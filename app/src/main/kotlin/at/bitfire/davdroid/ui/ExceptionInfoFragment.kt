/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui

import android.accounts.Account
import android.app.Dialog
import android.os.Bundle
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.AlertDialog
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Error
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.BundleCompat
import androidx.fragment.app.DialogFragment
import at.bitfire.dav4jvm.exception.HttpException
import at.bitfire.davdroid.R
import com.google.accompanist.themeadapter.material.MdcTheme
import java.io.IOException

class ExceptionInfoFragment: DialogFragment() {

    companion object {
        const val ARG_ACCOUNT = "account"
        const val ARG_EXCEPTION = "exception"

        fun newInstance(exception: Exception, account: Account?): ExceptionInfoFragment {
            val frag = ExceptionInfoFragment()
            val args = Bundle(2)
            args.putSerializable(ARG_EXCEPTION, exception)
            args.putParcelable(ARG_ACCOUNT, account)
            frag.arguments = args
            return frag
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val args = requireNotNull(arguments)
        val exception = args.getSerializable(ARG_EXCEPTION) as Exception
        val account: Account? = BundleCompat.getParcelable(args, ARG_ACCOUNT, Account::class.java)

        val dialog = Dialog(requireContext()).apply {
            setContentView(
                ComposeView(requireContext()).apply {
                    setContent {
                        MdcTheme {
                            ExceptionInfoDialog(
                                account, exception
                            ) { dismiss() }
                        }
                    }
                }
            )
        }

        isCancelable = false
        return dialog
    }

}

@Composable
fun ExceptionInfoDialog(
    account: Account?,
    exception: Throwable,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current

    val titleRes = when (exception) {
        is HttpException -> R.string.exception_httpexception
        is IOException   -> R.string.exception_ioexception
        else -> R.string.exception
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
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
        text = { Text(exception::class.java.name + "\n" + exception.localizedMessage) },
        dismissButton = {
            TextButton(
                onClick = {
                    val intent = DebugInfoActivity.IntentBuilder(context)
                        .withAccount(account)
                        .withCause(exception)
                        .build()
                    context.startActivity(intent)
                }
            ) {
                Text(stringResource(R.string.exception_show_details).uppercase())
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(android.R.string.ok).uppercase())
            }
        }
    )
}
