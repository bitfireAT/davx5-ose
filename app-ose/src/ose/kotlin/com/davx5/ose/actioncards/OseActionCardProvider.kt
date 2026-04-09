/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.davx5.ose.actioncards

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import at.bitfire.davdroid.R
import at.bitfire.davdroid.settings.SettingsManager
import at.bitfire.davdroid.ui.UiUtils.toAnnotatedString
import at.bitfire.davdroid.ui.actioncards.ActionCardProvider
import at.bitfire.davdroid.ui.composable.ActionCard
import javax.inject.Inject

class OseActionCardProvider @Inject constructor(
    private val settings: SettingsManager
) : ActionCardProvider {

    @Composable
    override fun ProvideActionCards(modifier: Modifier) {
        val dismissed by settings.getBooleanFlow(KEY_KEEP_ANDROID_OPEN_DISMISSED).collectAsStateWithLifecycle(false)
        if (dismissed != true) {
            ActionCard(
                icon = Icons.Default.Info,
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
         * OSE action card specific boolean setting. Tracks whether the keep-android-open
         * action card has been dismissed already.
         */
        private const val KEY_KEEP_ANDROID_OPEN_DISMISSED = "keep_android_open_dismissed"
    }

}