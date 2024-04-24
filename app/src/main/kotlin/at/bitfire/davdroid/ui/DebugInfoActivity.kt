/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ShareCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import at.bitfire.davdroid.BuildConfig
import at.bitfire.davdroid.R
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import javax.inject.Inject

/**
 * Debug info activity. Provides verbose information for debugging and support. Should enable users
 * to debug problems themselves, but also to send it to the support.
 *
 * Important use cases to test:
 *
 * - debug info from App settings / Debug info (should provide debug info)
 * - login with some broken login URL (should provide debug info + logs; check logs, too)
 * - enable App settings / Verbose logs, then open debug info activity (should provide debug info + logs; check logs, too)
 */
@AndroidEntryPoint
class DebugInfoActivity : AppCompatActivity() {

    @Inject lateinit var modelFactory: DebugInfoModel.Factory
    private val model by viewModels<DebugInfoModel> {
        object: ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                modelFactory.create(intent.extras) as T
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        model.zipFile.observe(this) { zipFile ->
            if (zipFile == null) return@observe

            // ZIP file is ready
            shareFile(
                zipFile,
                subject = "${getString(R.string.app_name)} ${BuildConfig.VERSION_NAME} debug info",
                text = getString(R.string.debug_info_attached),
                type = "*/*",    // application/zip won't show all apps that can manage binary files, like ShareViaHttp
            )

            // only share ZIP file once
            model.zipFile.value = null
        }

        setContent { 
            M2Theme {
                DebugInfoScreen(
                    model,
                    onShareFile = { shareFile(it) },
                    onViewFile = { viewFile(it) },
                    onNavUp = { onNavigateUp() }
                )
            }
        }
    }

    private fun shareFile(
        file: File,
        subject: String? = null,
        text: String? = null,
        type: String = "text/plain"
    ) {
        val uri = FileProvider.getUriForFile(
            this,
            getString(R.string.authority_debug_provider),
            file
        )
        ShareCompat.IntentBuilder(this)
            .setSubject(subject)
            .setText(text)
            .setType(type)
            .setStream(uri)
            .startChooser()
    }

    private fun viewFile(
        file: File,
        title: String? = null
    ) {
        val uri = FileProvider.getUriForFile(
            this,
            getString(R.string.authority_debug_provider),
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "text/plain")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, title))
    }

}