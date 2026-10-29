/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.about

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import at.bitfire.davdroid.R
import coil3.compose.AsyncImage

@Composable
fun TranslationsTab(
    weblateTranslators: String,
    transifexTranslators: String
) {
    Column(Modifier
        .padding(8.dp)
        .verticalScroll(rememberScrollState())) {
            Column {
                Translations_Engage(
                    modifier = Modifier.fillMaxWidth()
                )
                Translations_Translators(
                    weblateTranslators,
                    transifexTranslators,
                    modifier = Modifier.padding(top = 16.dp)
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

    AsyncImage(
        model = "https://hosted.weblate.org/widget/davx5/open-graph.png",
        contentDescription = stringResource(R.string.about_translations_contribute),
        modifier = modifier
            .aspectRatio(1200f / 630)
            .clickable(role = Role.Image) { uriHandler.openUri("https://hosted.weblate.org/engage/davx5/") }
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