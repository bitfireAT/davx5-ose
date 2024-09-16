/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.widget

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle

@Composable
@Deprecated(
    message = "Use Text with annotatedString instead",
    ReplaceWith(
        expression = "Text(text = text, style = style, modifier = modifier)",
        "androidx.compose.material3.Text"
    )
)
fun ClickableTextWithLink(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current
) {
    Text(
        text = text,
        style = style,
        modifier = modifier
    )
}