/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.ui.setup

import android.app.*
import android.content.*
import android.os.Bundle
import at.bitfire.davdroid.R
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.ui.DebugInfoActivity
import at.bitfire.davdroid.ui.setup.DavResourceFinder.Configuration
import kotlin.concurrent.thread

@Suppress("DEPRECATION")
class DetectConfigurationFragment: DialogFragment(), LoaderManager.LoaderCallbacks<Configuration> {

    companion object {
        val ARG_LOGIN_CREDENTIALS = "credentials"

        fun newInstance(credentials: LoginCredentials): DetectConfigurationFragment {
            val frag = DetectConfigurationFragment()
            val args = Bundle(1)
            args.putParcelable(ARG_LOGIN_CREDENTIALS, credentials)
            frag.arguments = args
            return frag
        }
    }


    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val progress = ProgressDialog(activity)
        progress.setTitle(R.string.login_configuration_detection)
        progress.setMessage(getString(R.string.login_querying_server))
        progress.isIndeterminate = true
        progress.setCanceledOnTouchOutside(false)
        return progress
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loaderManager.initLoader(0, arguments, this)
    }

    override fun onCancel(dialog: DialogInterface?) {
        Logger.log.info("Cancelling resource detection")
        loaderManager.getLoader<Configuration>(0)?.cancelLoad()
    }


    override fun onCreateLoader(id: Int, args: Bundle) =
            ServerConfigurationLoader(activity, args.getParcelable(ARG_LOGIN_CREDENTIALS))

    override fun onLoadFinished(loader: Loader<Configuration>, data: Configuration?) {
        data?.let {
            if (it.calDAV == null && it.cardDAV == null)
                // no service found: show error message
                fragmentManager.beginTransaction()
                        .add(NothingDetectedFragment.newInstance(it.logs), null)
                        .commitAllowingStateLoss()
            else
                // service found: continue
                fragmentManager.beginTransaction()
                        .replace(android.R.id.content, AccountDetailsFragment.newInstance(data))
                        .addToBackStack(null)
                        .commitAllowingStateLoss()
        }

        dismissAllowingStateLoss()
    }

    override fun onLoaderReset(loader: Loader<Configuration>) {}


    class NothingDetectedFragment: DialogFragment() {

        companion object {
            val KEY_LOGS = "logs"

            fun newInstance(logs: String): NothingDetectedFragment {
                val args = Bundle()
                args.putString(KEY_LOGS, logs)
                val fragment = NothingDetectedFragment()
                fragment.arguments = args
                return fragment
            }
        }

        override fun onCreateDialog(savedInstanceState: Bundle?) =
                AlertDialog.Builder(activity)
                        .setTitle(R.string.login_configuration_detection)
                        .setIcon(R.drawable.ic_error_dark)
                        .setMessage(R.string.login_no_caldav_carddav)
                        .setNeutralButton(R.string.login_view_logs, { _, _ ->
                            val intent = Intent(activity, DebugInfoActivity::class.java)
                            intent.putExtra(DebugInfoActivity.KEY_LOGS, arguments.getString(KEY_LOGS))
                            startActivity(intent)
                        })
                        .setPositiveButton(android.R.string.ok, { _, _ ->
                            // dismiss
                        })
                        .create()!!

    }


    class ServerConfigurationLoader(
            context: Context,
            private val credentials: LoginCredentials
    ): AsyncTaskLoader<Configuration>(context) {

        var resourceFinder: DavResourceFinder? = null

        override fun onStartLoading() = forceLoad()

        override fun cancelLoadInBackground() {
            thread {
                resourceFinder?.cancel()
                resourceFinder = null
            }
        }

        override fun loadInBackground(): Configuration {
            val finder = DavResourceFinder(context, credentials)
            resourceFinder = finder
            return finder.findInitialConfiguration()
        }

    }

}
