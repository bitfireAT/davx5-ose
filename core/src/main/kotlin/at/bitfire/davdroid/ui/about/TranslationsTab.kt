/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.about

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import at.bitfire.davdroid.R

@Composable
fun TranslationsTab(
    weblateTranslators: String,
    transifexTranslators: String
) {
    Column(Modifier
        .padding(8.dp)
        .verticalScroll(rememberScrollState())) {
            Column {
                Translations_Translators(
                    weblateTranslators,
                    transifexTranslators,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Translations_Engage(
                    modifier = Modifier.fillMaxWidth()
                )
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
            text = stringResource(R.string.about_translations_thanks),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            text = stringResource(R.string.about_translations_over_weblate),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(vertical = 4.dp)
        )
        Text(
            text = weblateTranslators,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            text = stringResource(R.string.about_translations_over_transifex),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(vertical = 4.dp)
        )
        Text(
            text = transifexTranslators,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun Translations_Engage(modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    Button(
        modifier = modifier,
        onClick = { uriHandler.openUri("https://hosted.weblate.org/engage/davx5/") }
    ) { Text(stringResource(R.string.about_translations_contribute)) }
}

@Composable
@PreviewScreenSizes
fun TranslationsTab_Previews() {
    TranslationsTab(
        weblateTranslators = "User A, User B, and User C",
        transifexTranslators = "User A and User D"
    )
}