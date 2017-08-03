/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.ui

import android.accounts.Account
import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.support.v4.app.DialogFragment
import android.support.v7.app.AlertDialog
import at.bitfire.dav4android.exception.HttpException
import at.bitfire.davdroid.R
import java.io.IOException

class ExceptionInfoFragment: DialogFragment() {

    companion object {
        val ARG_ACCOUNT = "account"
        val ARG_EXCEPTION = "exception"

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
        val exception = arguments.getSerializable(ARG_EXCEPTION) as Exception
        val account: Account? = arguments.getParcelable(ARG_ACCOUNT)

        val title = when (exception) {
            is HttpException -> R.string.exception_httpexception
            is IOException   -> R.string.exception_ioexception
            else -> R.string.exception
        }

        val dialog = AlertDialog.Builder(context)
                .setIcon(R.drawable.ic_error_dark)
                .setTitle(title)
                .setMessage(exception::class.java.name + "\n" + exception.localizedMessage)
                .setNegativeButton(R.string.exception_show_details, { _, _ ->
                    val intent = Intent(getContext(), DebugInfoActivity::class.java)
                    intent.putExtra(DebugInfoActivity.KEY_THROWABLE, exception)
                    account?.let { intent.putExtra(DebugInfoActivity.KEY_ACCOUNT, it) }
                    startActivity(intent)
                })
                .setPositiveButton(android.R.string.ok, { _, _ -> })
                .create()
        isCancelable = false
        return dialog
    }

}
