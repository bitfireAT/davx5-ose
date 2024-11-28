/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import dagger.BindsOptionalOf
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.components.ActivityComponent
import java.util.Optional
import javax.inject.Inject
import kotlin.jvm.optionals.getOrNull

@AndroidEntryPoint
class AboutActivity: AppCompatActivity() {

    val model by viewModels<AboutModel>()

    @Inject
    lateinit var licenseInfoProvider: Optional<AppLicenseInfoProvider>


    @OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AppTheme {
                AboutScreen(
                    viewModel = model,
                    licenseInfoProvider = licenseInfoProvider.getOrNull(),
                    onBackRequested = ::onSupportNavigateUp
                )
            }
        }
    }


    interface AppLicenseInfoProvider {
        @Composable
        fun LicenseInfo()
    }

    @Module
    @InstallIn(ActivityComponent::class)
    interface AppLicenseInfoProviderModule {
        @BindsOptionalOf
        fun appLicenseInfoProvider(): AppLicenseInfoProvider
    }

}
