/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun IconCard(
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    content: @Composable () -> Unit
) {
    IconCard(
        modifier = modifier,
        icon = icon?.buildComposable(), 
        content = content
    )
}

@Composable
fun IconCard(
    modifier: Modifier = Modifier,
    painterIcon: Painter? = null,
    content: @Composable () -> Unit
) {
    IconCard(
        modifier = modifier,
        icon = painterIcon?.buildComposable(),
        content = content
    )
}

@Composable
private fun IconCard(
    modifier: Modifier,
    icon: @Composable (RowScope.() -> Unit)?,
    content: @Composable (() -> Unit)
) {
    BaseCard(
        modifier = modifier,
        icon = icon,
        footer = {} // No footer for IconCard
    ) {
        content()
    }
}

@Composable
@Preview
fun IconCard_Sample() {
    IconCard(
        icon = Icons.Default.Event
    ) {
        Column {
            Text("Some Content. Some Content. Some Content. Some Content. ")
            Text("Other Content. Other Content. Other Content. Other Content. Other Content. Other Content. Other Content. ", style = MaterialTheme.typography.bodyMedium)
        }
    }
}