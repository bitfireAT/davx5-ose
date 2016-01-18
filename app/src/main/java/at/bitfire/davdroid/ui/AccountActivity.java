/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.ui;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;

import java.io.IOException;

import at.bitfire.davdroid.Constants;
import at.bitfire.davdroid.R;

public class AccountActivity extends AppCompatActivity implements Toolbar.OnMenuItemClickListener {

    public static final String EXTRA_ACCOUNT_NAME = "account_name";

    Account account;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final String accountName = getIntent().getStringExtra(EXTRA_ACCOUNT_NAME);
        if (accountName == null)
            // invalid account name
            finish();
        account = new Account(accountName, Constants.ACCOUNT_TYPE);
        setTitle(accountName);

        setContentView(R.layout.activity_account);

        Toolbar toolbar = (Toolbar)findViewById(R.id.carddav_menu);
        toolbar.inflateMenu(R.menu.carddav_actions);
        toolbar.setOnMenuItemClickListener(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_account, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.delete_account:
                DialogFragment frag = new DialogFragment() {
                    @Override
                    public Dialog onCreateDialog(Bundle savedInstanceState) {
                        return new AlertDialog.Builder(AccountActivity.this)
                                .setIcon(R.drawable.ic_error_dark)
                                .setTitle(R.string.account_delete_confirmation_title)
                                .setMessage(R.string.account_delete_confirmation_text)
                                .setNegativeButton(android.R.string.no, null)
                                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        deleteAccount();
                                    }
                                })
                                .create();
                    }
                };
                frag.show(getSupportFragmentManager(), null);
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        return false;
    }


    protected void deleteAccount() {
        AccountManager accountManager = AccountManager.get(this);

        if (Build.VERSION.SDK_INT >= 22)
            accountManager.removeAccount(account, this, new AccountManagerCallback<Bundle>() {
                @Override
                public void run(AccountManagerFuture<Bundle> future) {
                    try {
                        if (future.getResult().getBoolean(AccountManager.KEY_BOOLEAN_RESULT))
                            finish();
                    } catch(OperationCanceledException|IOException|AuthenticatorException e) {
                        Constants.log.error("Couldn't remove account", e);
                    }
                }
            }, null);
        else
            accountManager.removeAccount(account, new AccountManagerCallback<Boolean>() {
                @Override
                public void run(AccountManagerFuture<Boolean> future) {
                    try {
                        if (future.getResult())
                            finish();
                    } catch (OperationCanceledException|IOException|AuthenticatorException e) {
                        Constants.log.error("Couldn't remove account", e);
                    }
                }
            }, null);
    }

}
