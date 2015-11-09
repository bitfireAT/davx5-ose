/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */
package at.bitfire.davdroid.ui.setup;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.LoaderManager.LoaderCallbacks;
import android.app.ProgressDialog;
import android.content.AsyncTaskLoader;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.Loader;
import android.os.Bundle;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;

import at.bitfire.davdroid.Constants;
import at.bitfire.davdroid.R;
import at.bitfire.davdroid.log.StringLogger;
import at.bitfire.davdroid.resource.DavResourceFinder;
import at.bitfire.davdroid.resource.LocalTaskList;
import at.bitfire.davdroid.resource.ServerInfo;
import at.bitfire.davdroid.ui.DebugInfoActivity;
import lombok.Cleanup;

public class QueryServerDialogFragment extends DialogFragment implements LoaderCallbacks<ServerInfo> {
    public static final String KEY_SERVER_INFO = "server_info";

    public static QueryServerDialogFragment newInstance(ServerInfo serverInfo) {
        Bundle args = new Bundle();
        args.putSerializable(KEY_SERVER_INFO, serverInfo);
        QueryServerDialogFragment fragment = new QueryServerDialogFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        ProgressDialog dialog = new ProgressDialog(getActivity());
        dialog.setCanceledOnTouchOutside(false);
        setCancelable(false);

        dialog.setTitle(R.string.setup_resource_detection);
        dialog.setIndeterminate(true);
        dialog.setMessage(getString(R.string.setup_querying_server));
        return dialog;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Loader<ServerInfo> loader = getLoaderManager().initLoader(0, getArguments(), this);
    }

    @Override
    public Loader<ServerInfo> onCreateLoader(int id, Bundle args) {
        return new ServerInfoLoader(getActivity(), args);
    }

    @Override
    @SuppressLint("CommitTransaction")
    public void onLoadFinished(Loader<ServerInfo> loader, ServerInfo serverInfo) {
        if (serverInfo.isEmpty()) {
            // resource detection didn't find anything
            getFragmentManager().beginTransaction()
                    .add(NothingDetectedFragment.newInstance(serverInfo.getLogs()), null)
                    .commitAllowingStateLoss();

        } else {
            ((AddAccountActivity)getActivity()).serverInfo = serverInfo;

            // resource detection brought some results
            Fragment nextFragment;
            if (serverInfo.getTaskLists().length > 0 && !LocalTaskList.tasksProviderAvailable(getActivity().getContentResolver()))
                nextFragment = new InstallAppsFragment();
            else
                nextFragment = new SelectCollectionsFragment();

            getFragmentManager().beginTransaction()
                    .replace(R.id.right_pane, nextFragment)
                    .addToBackStack(null)
                    .commitAllowingStateLoss();
        }

        getDialog().dismiss();
    }

    @Override
    public void onLoaderReset(Loader<ServerInfo> arg0) {
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
                    .setTitle(R.string.setup_resource_detection)
                    .setIcon(android.R.drawable.ic_dialog_info)
                    .setMessage(R.string.setup_no_collections_found)
                    .setNeutralButton(R.string.setup_view_logs, new DialogInterface.OnClickListener() {
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

    static class ServerInfoLoader extends AsyncTaskLoader<ServerInfo> {
        private static final String TAG = "davdroid.ServerInfoLoader";
        final Bundle args;
        final Context context;

        public ServerInfoLoader(Context context, Bundle args) {
            super(context);
            this.context = context;
            this.args = args;
        }

        @Override
        protected void onStartLoading() {
            forceLoad();
        }

        @Override
        public ServerInfo loadInBackground() {
            ServerInfo serverInfo = (ServerInfo)args.getSerializable(KEY_SERVER_INFO);

            StringLogger logger = new StringLogger("DavResourceFinder", true);
            DavResourceFinder finder = new DavResourceFinder(logger, context, serverInfo);
            finder.findResources();

            // duplicate logs to ADB
            String logs = logger.toString();
            try {
                @Cleanup BufferedReader logStream = new BufferedReader(new StringReader(logs));
                Constants.log.info("Successful resource detection:");
                String line;
                while ((line = logStream.readLine()) != null)
                    Constants.log.debug(line);
            } catch (IOException e) {
                Constants.log.error("Couldn't read resource detection logs", e);
            }

            serverInfo.setLogs(logger.toString());

            return serverInfo;
        }
    }

}
