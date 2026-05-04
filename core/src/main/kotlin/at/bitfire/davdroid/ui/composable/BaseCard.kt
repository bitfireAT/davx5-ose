/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Internal base card composable that provides shared layout for ActionCard and IconCard.
 * Handles the Card wrapper, column layout, optional icon row, text styling, and optional footer.
 */
@Composable
internal fun BaseCard(
    modifier: Modifier = Modifier,
    icon: @Composable (RowScope.() -> Unit)? = null,
    footer: @Composable () -> Unit = {},
    content: @Composable () -> Unit
) {
    Card(
        Modifier
            .fillMaxWidth()
            .then(modifier)
    ) {
        Column(
            Modifier
                .padding(8.dp)
                .fillMaxWidth(),
        ) {
            ProvideTextStyle(MaterialTheme.typography.bodyLarge) {
                if (icon != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        icon()
                        content()
                    }
                } else {
                    content()
                }
            }

            footer()
        }
    }
}

/**
 * Extension function to convert an ImageVector into a composable icon for use in RowScope.
 * Provides consistent icon styling (aligned to top, 8dp padding).
 */
@Composable
internal fun ImageVector.buildComposable(): @Composable (RowScope.() -> Unit) {
    return {
        Icon(
            imageVector = this@buildComposable,
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.Top)
                .padding(8.dp)
        )
    }
}

/**
 * Extension function to convert a Painter into a composable icon for use in RowScope.
 * Provides consistent icon styling (aligned to top, 8dp padding).
 */
@Composable
internal fun Painter.buildComposable(): @Composable (RowScope.() -> Unit) {
    return {
        Icon(
            painter = this@buildComposable,
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.Top)
                .padding(8.dp)
        )
    }
}
