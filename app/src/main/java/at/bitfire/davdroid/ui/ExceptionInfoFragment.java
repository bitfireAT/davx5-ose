/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.ui;

import android.accounts.Account;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;

import java.io.IOException;

import at.bitfire.dav4android.exception.HttpException;
import at.bitfire.davdroid.R;

public class ExceptionInfoFragment extends DialogFragment {
    protected static final String
            ARG_ACCOUNT = "account",
            ARG_EXCEPTION = "exception";

    public static ExceptionInfoFragment newInstance(@NonNull Exception exception, Account account) {
        ExceptionInfoFragment frag = new ExceptionInfoFragment();
        Bundle args = new Bundle(1);
        args.putSerializable(ARG_EXCEPTION, exception);
        args.putParcelable(ARG_ACCOUNT, account);
        frag.setArguments(args);
        return frag;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Bundle args = getArguments();
        final Exception exception = (Exception)args.getSerializable(ARG_EXCEPTION);
        final Account account = args.getParcelable(ARG_ACCOUNT);

        int title = R.string.exception;
        if (exception instanceof HttpException)
            title = R.string.exception_httpexception;
        else if (exception instanceof IOException)
            title = R.string.exception_ioexception;

        Dialog dialog = new AlertDialog.Builder(getContext())
                .setIcon(R.drawable.ic_error_dark)
                .setTitle(title)
                .setMessage(exception.getClass().getCanonicalName() + "\n" + exception.getLocalizedMessage())
                .setNegativeButton(R.string.exception_show_details, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Intent intent = new Intent(getContext(), DebugInfoActivity.class);
                        intent.putExtra(DebugInfoActivity.KEY_THROWABLE, exception);
                        if (account != null)
                            intent.putExtra(DebugInfoActivity.KEY_ACCOUNT, account);
                        startActivity(intent);
                    }
                })
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                    }
                })
                .create();
        setCancelable(false);
        return dialog;
    }
}
