/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.ui.setup

import android.app.Application
import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.lifecycle.*
import at.bitfire.davdroid.R
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.ui.DebugInfoActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.lang.ref.WeakReference
import java.util.logging.Level
import kotlin.concurrent.thread

class DetectConfigurationFragment: Fragment() {

    private lateinit var loginModel: LoginModel
    private lateinit var model: DetectConfigurationModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loginModel = ViewModelProviders.of(requireActivity()).get(LoginModel::class.java)
        model = ViewModelProviders.of(this).get(DetectConfigurationModel::class.java)

        model.detectConfiguration(loginModel).observe(this, Observer<DavResourceFinder.Configuration> { result ->
            // save result for next step
            loginModel.configuration = result

            // remove "Detecting configuration" fragment, it shouldn't come back
            requireFragmentManager().popBackStack()

            if (result.calDAV != null || result.cardDAV != null)
                requireFragmentManager().beginTransaction()
                        .replace(android.R.id.content, AccountDetailsFragment())
                        .addToBackStack(null)
                        .commit()
            else
                requireFragmentManager().beginTransaction()
                        .add(NothingDetectedFragment(), null)
                        .commit()
        })
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
            inflater.inflate(R.layout.detect_configuration, container, false)!!


    class DetectConfigurationModel(
            application: Application
    ): AndroidViewModel(application) {

        private var detectionThread: WeakReference<Thread>? = null
        private var result = MutableLiveData<DavResourceFinder.Configuration>()

        fun detectConfiguration(loginModel: LoginModel): LiveData<DavResourceFinder.Configuration> {
            synchronized(result) {
                if (detectionThread != null)
                    // detection already running
                    return result
            }

            thread {
                synchronized(result) {
                    detectionThread = WeakReference(Thread.currentThread())
                }

                try {
                    DavResourceFinder(getApplication(), loginModel).use { finder ->
                        result.postValue(finder.findInitialConfiguration())
                    }
                } catch(e: Exception) {
                    // exception, shouldn't happen
                    Logger.log.log(Level.SEVERE, "Internal resource detection error", e)
                }
            }
            return result
        }

        override fun onCleared() {
            synchronized(result) {
                detectionThread?.get()?.let { thread ->
                    Logger.log.info("Aborting resource detection")
                    thread.interrupt()
                }
                detectionThread = null
            }
        }
    }


    class NothingDetectedFragment: DialogFragment() {

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            val model = ViewModelProviders.of(requireActivity()).get(LoginModel::class.java)
            return MaterialAlertDialogBuilder(requireActivity())
                    .setTitle(R.string.login_configuration_detection)
                    .setIcon(R.drawable.ic_error_dark)
                    .setMessage(R.string.login_no_caldav_carddav)
                    .setNeutralButton(R.string.login_view_logs) { _, _ ->
                        val intent = Intent(activity, DebugInfoActivity::class.java)
                        intent.putExtra(DebugInfoActivity.KEY_LOGS, model.configuration?.logs)
                        startActivity(intent)
                    }
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        // just dismiss
                    }
                    .create()!!
        }

    }

}
