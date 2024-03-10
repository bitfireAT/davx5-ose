/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui.setup

import android.app.Application
import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.Credentials
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.servicedetection.DavResourceFinder
import at.bitfire.davdroid.ui.AppTheme
import at.bitfire.davdroid.ui.DebugInfoActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.runInterruptible
import java.net.URI
import java.util.logging.Level

class DetectConfigurationFragment: Fragment() {

    private val loginModel by activityViewModels<LoginModel>()
    private val model by viewModels<DetectConfigurationModel>()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val baseURI = loginModel.baseURI ?: return

        model.result.observe(this) { result ->
            // save result for next step
            loginModel.configuration = result

            // remove "Detecting configuration" fragment, it shouldn't come back
            parentFragmentManager.popBackStack()

            if (result.calDAV != null || result.cardDAV != null)
                parentFragmentManager.beginTransaction()
                        .replace(android.R.id.content, AccountDetailsFragment())
                        .addToBackStack(null)
                        .commit()
            else
                parentFragmentManager.beginTransaction()
                        .add(NothingDetectedFragment(), null)
                        .commit()
        }

        model.start(baseURI, loginModel.credentials)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) =
        ComposeView(requireContext()).apply {
            setContent {
                AppTheme {
                    DetectConfigurationView()
                }

                // Cancel service detection only when back button is pressed
                BackHandler {
                    model.cancel()

                    parentFragmentManager.popBackStack()
                }
            }
        }


    class DetectConfigurationModel(application: Application): AndroidViewModel(application) {

        val scope = MainScope() + CoroutineName("DetectConfigurationModel")
        var result = MutableLiveData<DavResourceFinder.Configuration>()

        /**
         * Starts service detection in a global scope which is independent of the ViewModel scope so
         * that service detection won't be cancelled when the user for instance switches to another app.
         *
         * The service detection result will be posted into [result].
         */
        fun start(baseURI: URI, credentials: Credentials?) {
            scope.launch(Dispatchers.Default) {
                runInterruptible {
                    try {
                        DavResourceFinder(getApplication(), baseURI, credentials).use { finder ->
                            val configuration = finder.findInitialConfiguration()
                            result.postValue(configuration)
                        }
                    } catch(e: Exception) {
                        // This shouldn't happen; instead configuration should be empty
                        Logger.log.log(Level.WARNING, "Uncaught exception during service detection, shouldn't happen", e)
                    }
                }
            }
        }

        /**
         * Cancels a potentially running service detection.
         */
        fun cancel() {
            Logger.log.info("Aborting resource detection")
            try {
                scope.cancel()
            } catch (ignored: IllegalStateException) { }
        }

    }


    class NothingDetectedFragment: DialogFragment() {

        val model by activityViewModels<LoginModel>()

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            var message = getString(R.string.login_no_caldav_carddav)
            if (model.configuration?.encountered401 == true)
                message += "\n\n" + getString(R.string.login_username_password_wrong)

            return MaterialAlertDialogBuilder(requireActivity())
                    .setTitle(R.string.login_configuration_detection)
                    .setIcon(R.drawable.ic_error)
                    .setMessage(message)
                    .setNeutralButton(R.string.login_view_logs) { _, _ ->
                        val intent = DebugInfoActivity.IntentBuilder(requireActivity())
                            .withLogs(model.configuration?.logs)
                            .build()
                        startActivity(intent)
                    }
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        // just dismiss
                    }
                    .create()
        }

    }

}


@Composable
fun DetectConfigurationView() {
    Column(Modifier
        .fillMaxWidth()
        .padding(8.dp)) {
        Text(
            stringResource(R.string.login_configuration_detection),
            style = MaterialTheme.typography.h5
        )
        LinearProgressIndicator(
            color = MaterialTheme.colors.secondary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 16.dp))
        Text(
            stringResource(R.string.login_querying_server),
            style = MaterialTheme.typography.body1
        )
    }
}

@Composable
@Preview
fun DetectConfigurationView_Preview() {
    DetectConfigurationView()
}