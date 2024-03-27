/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.widget

import androidx.compose.foundation.text.ClickableText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle

@OptIn(ExperimentalTextApi::class)
@Composable
fun ClickableTextWithLink(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default
) {
    val uriHandler = LocalUriHandler.current

    ClickableText(
        text = text,
        style = style,
        modifier = modifier
    ) { index ->
        // Get the tapped position, and check if there's any link
        text.getUrlAnnotations(index, index).firstOrNull()?.item?.url?.let { url ->
            uriHandler.openUri(url)
        }
    }
}