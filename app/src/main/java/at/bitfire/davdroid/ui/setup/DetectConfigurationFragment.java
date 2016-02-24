/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.ui.setup;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.Loader;
import android.support.v7.app.AlertDialog;

import at.bitfire.davdroid.R;
import at.bitfire.davdroid.ui.DebugInfoActivity;
import at.bitfire.davdroid.ui.setup.DavResourceFinder.Configuration;

public class DetectConfigurationFragment extends DialogFragment implements LoaderManager.LoaderCallbacks<Configuration> {
    protected static final String ARG_LOGIN_CREDENTIALS = "credentials";

    public static DetectConfigurationFragment newInstance(LoginCredentials credentials) {
        DetectConfigurationFragment frag = new DetectConfigurationFragment();
        Bundle args = new Bundle(1);
        args.putParcelable(ARG_LOGIN_CREDENTIALS, credentials);
        frag.setArguments(args);
        return frag;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        ProgressDialog dialog = new ProgressDialog(getActivity());
        dialog.setCanceledOnTouchOutside(false);
        setCancelable(false);

        dialog.setTitle(R.string.login_configuration_detection);
        dialog.setIndeterminate(true);
        dialog.setMessage(getString(R.string.login_querying_server));
        return dialog;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getLoaderManager().initLoader(0, getArguments(), this);
    }

    @Override
    public Loader<Configuration> onCreateLoader(int id, Bundle args) {
        return new ServerConfigurationLoader(getContext(), (LoginCredentials)args.getParcelable(ARG_LOGIN_CREDENTIALS));
    }

    @Override
    public void onLoadFinished(Loader<Configuration> loader, Configuration data) {
        if (data.calDAV == null && data.cardDAV == null)
            // no service found: show error message
            getFragmentManager().beginTransaction()
                    .add(NothingDetectedFragment.newInstance(data.logs), null)
                    .commitAllowingStateLoss();
        else
            // service found: continue
            getFragmentManager().beginTransaction()
                    .replace(R.id.fragment, AccountDetailsFragment.newInstance(data))
                    .addToBackStack(null)
                    .commitAllowingStateLoss();

        dismissAllowingStateLoss();
    }

    @Override
    public void onLoaderReset(Loader<Configuration> loader) {
    }


    public static class NothingDetectedFragment extends DialogFragment {
        private static String KEY_LOGS = "logs";

        public static NothingDetectedFragment newInstance(String logs) {
            Bundle args = new Bundle();
            args.putString(KEY_LOGS, logs);
            NothingDetectedFragment fragment = new NothingDetectedFragment();
            fragment.setArguments(args);
            return fragment;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.login_configuration_detection)
                    .setIcon(R.drawable.ic_error_dark)
                    .setMessage(R.string.login_no_caldav_carddav)
                    .setNeutralButton(R.string.login_view_logs, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Intent intent = new Intent(getActivity(), DebugInfoActivity.class);
                            intent.putExtra(DebugInfoActivity.KEY_LOGS, getArguments().getString(KEY_LOGS));
                            startActivity(intent);
                        }
                    })
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            // dismiss
                        }
                    })
                    .create();
        }
    }

    static class ServerConfigurationLoader extends AsyncTaskLoader<Configuration> {
        final Context context;
        final LoginCredentials credentials;

        public ServerConfigurationLoader(Context context, LoginCredentials credentials) {
            super(context);
            this.context = context;
            this.credentials = credentials;
        }

        @Override
        protected void onStartLoading() {
            forceLoad();
        }

        @Override
        public Configuration loadInBackground() {
            DavResourceFinder finder = new DavResourceFinder(context, credentials);
            return finder.findInitialConfiguration();
        }
    }
}
