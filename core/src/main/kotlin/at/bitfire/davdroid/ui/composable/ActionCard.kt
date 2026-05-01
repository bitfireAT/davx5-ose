/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationAdd
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun ActionCard(
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    actionText: String,
    onAction: () -> Unit,
    content: @Composable () -> Unit
) {
    ActionCard(
        modifier = modifier,
        icon = icon?.buildComposable(),
        content = content,
        actionText = actionText,
        onAction = onAction
    )
}

@Composable
fun ActionCard(
    modifier: Modifier = Modifier,
    painterIcon: Painter? = null,
    actionText: String,
    onAction: () -> Unit,
    content: @Composable () -> Unit
) {
    ActionCard(
        modifier = modifier,
        icon = painterIcon?.buildComposable(),
        content = content,
        actionText = actionText,
        onAction = onAction
    )
}

@Composable
private fun ActionCard(
    modifier: Modifier,
    icon: @Composable (RowScope.() -> Unit)?,
    content: @Composable (() -> Unit),
    actionText: String?,
    onAction: () -> Unit
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

            if (actionText != null)
                OutlinedButton(
                    onClick = onAction,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text(actionText)
                }
        }
    }
}

@Composable
private fun ImageVector.buildComposable(): @Composable (RowScope.() -> Unit) {
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


@Composable
private fun Painter.buildComposable(): @Composable (RowScope.() -> Unit) {
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

@Composable
@Preview
fun ActionCard_Sample() {
    ActionCard(
        icon = Icons.Default.NotificationAdd,
        actionText = "Some Action",
        onAction = {}
    ) {
        Column {
            Text("Some Content. Some Content. Some Content. Some Content. ")
            Text("Other Content. Other Content. Other Content. Other Content. Other Content. Other Content. Other Content. ", style = MaterialTheme.typography.bodyMedium)
        }
    }
}