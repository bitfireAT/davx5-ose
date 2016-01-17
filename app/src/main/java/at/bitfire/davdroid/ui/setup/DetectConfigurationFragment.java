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
import android.support.v4.app.DialogFragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.Loader;
import android.support.v7.app.AlertDialog;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;

import at.bitfire.davdroid.Constants;
import at.bitfire.davdroid.R;
import at.bitfire.davdroid.resource.DavResourceFinder;
import at.bitfire.davdroid.resource.DavResourceFinder.Configuration;
import at.bitfire.davdroid.ui.DebugInfoActivity;
import lombok.Cleanup;

public class DetectConfigurationFragment extends DialogFragment implements LoaderManager.LoaderCallbacks<Configuration> {

    static final String ARG_LOGIN_CREDENTIALS = "credentials";

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
        return new ServerConfigurationLoader(getContext(), args);
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
        final LoginCredentialsFragment.LoginCredentials credentials;

        public ServerConfigurationLoader(Context context, Bundle args) {
            super(context);
            this.context = context;
            credentials = (LoginCredentialsFragment.LoginCredentials)args.getSerializable(ARG_LOGIN_CREDENTIALS);
        }

        @Override
        protected void onStartLoading() {
            forceLoad();
        }

        @Override
        public Configuration loadInBackground() {
            DavResourceFinder finder = new DavResourceFinder(context, credentials);
            Configuration configuration = finder.findInitialConfiguration();

            try {
                @Cleanup BufferedReader logStream = new BufferedReader(new StringReader(configuration.logs));
                Constants.log.info("Resource detection finished:");
                String line;
                while ((line = logStream.readLine()) != null)
                    Constants.log.info(line);
            } catch (IOException e) {
                Constants.log.error("Couldn't read resource detection logs", e);
            }

            return configuration;
        }
    }
}
