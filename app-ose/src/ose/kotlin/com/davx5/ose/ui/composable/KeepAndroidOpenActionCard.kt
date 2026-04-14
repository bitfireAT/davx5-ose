/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.davx5.ose.ui.composable

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import at.bitfire.davdroid.R
import at.bitfire.davdroid.settings.SettingsManager
import at.bitfire.davdroid.ui.UiUtils.toAnnotatedString
import at.bitfire.davdroid.ui.composable.ActionCard
import at.bitfire.davdroid.ui.composable.FlavorComposable
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Inject

class KeepAndroidOpenActionCard @Inject constructor(
    private val settings: SettingsManager
) : FlavorComposable {

    @Composable
    override fun Render(modifier: Modifier) {
        val dismissed by settings.getBooleanFlow(KEY_KEEP_ANDROID_OPEN_DISMISSED).collectAsStateWithLifecycle(false)
        if (dismissed != true) {
            ActionCard(
                painterIcon = painterResource(R.drawable.ic_warning_notify),
                actionText = stringResource(R.string.dismiss_forever),
                onAction = { settings.putBoolean(KEY_KEEP_ANDROID_OPEN_DISMISSED, true) },
                modifier = modifier.padding(vertical = 4.dp)
            ) {
                Text(HtmlCompat.fromHtml(
                    stringResource(R.string.keep_android_open, "https://keepandroidopen.org/"),
                    HtmlCompat.FROM_HTML_MODE_LEGACY
                ).toAnnotatedString())
            }
        }
    }

    companion object {

        /**
         * Settings key to track whether the keep-android-open action card has been dismissed.
         */
        private const val KEY_KEEP_ANDROID_OPEN_DISMISSED = "keep_android_open_dismissed"
    }

    @Module
    @InstallIn(SingletonComponent::class)
    interface KeepAndroidOpenComposableModule {

        @Binds
        @IntoSet
        @JvmSuppressWildcards
        fun keepAndroidOpenActionCard(impl: KeepAndroidOpenActionCard): FlavorComposable
    }

}