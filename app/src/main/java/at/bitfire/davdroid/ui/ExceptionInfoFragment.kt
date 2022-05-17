/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui

import android.accounts.Account
import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import at.bitfire.dav4jvm.exception.HttpException
import at.bitfire.davdroid.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
        val account: Account? = args.getParcelable(ARG_ACCOUNT)

        val title = when (exception) {
            is HttpException -> R.string.exception_httpexception
            is IOException   -> R.string.exception_ioexception
            else -> R.string.exception
        }

        val dialog = MaterialAlertDialogBuilder(requireActivity())
                .setIcon(R.drawable.ic_error)
                .setTitle(title)
                .setMessage(exception::class.java.name + "\n" + exception.localizedMessage)
                .setNegativeButton(R.string.exception_show_details) { _, _ ->
                    val intent = DebugInfoActivity.IntentBuilder(requireActivity())
                        .withAccount(account)
                        .withCause(exception)
                        .build()
                    startActivity(intent)
                }
                .setPositiveButton(android.R.string.ok) { _, _ -> }
                .create()
        isCancelable = false
        return dialog
    }

}
