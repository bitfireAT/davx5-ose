/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.about

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import at.bitfire.davdroid.ui.composable.WebViewCompat

@Composable
fun TranslationsTab(
    weblateTranslators: String,
    transifexTranslators: String,
    windowSizeClass: WindowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
) {
    Column(Modifier
        .padding(8.dp)
        .verticalScroll(rememberScrollState())) {

        val sideBySide = windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)
        if (sideBySide)
            Row {
                Translations_Engage(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                )
                Translations_Translators(
                    weblateTranslators = weblateTranslators,
                    transifexTranslators = transifexTranslators,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp)
                )
            }
        else
            Column {
                Translations_Translators(
                    weblateTranslators,
                    transifexTranslators,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Translations_Engage()
            }
    }
}

@Composable
fun Translations_Translators(
    weblateTranslators: String,
    transifexTranslators: String,
    modifier: Modifier
) {
    Column(modifier) {
        Text(
            text = "Over Weblate",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(vertical = 4.dp)
        )
        Text(
            text = weblateTranslators,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Text(
            text = "Over Transifex",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(vertical = 4.dp)
        )
        Text(
            text = transifexTranslators,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
fun Translations_Engage(modifier: Modifier = Modifier) {
    WebViewCompat(
        url = "https://hosted.weblate.org/engage/davx5/",
        modifier = modifier
    )
}

@Composable
@PreviewScreenSizes
fun TranslationsTab_Previews() {
    TranslationsTab(
        weblateTranslators = "User A, User B, and User C",
        transifexTranslators = "User A and User D"
    )
}