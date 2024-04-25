/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun ActionCard(
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    actionText: String? = null,
    onAction: () -> Unit = {},
    content: @Composable () -> Unit
) {
    Card(Modifier
        .fillMaxWidth()
        .then(modifier)
    ) {
        Column(Modifier
            .padding(top = 8.dp, start = 8.dp, end = 8.dp)
            .fillMaxWidth(),
        ) {
            ProvideTextStyle(MaterialTheme.typography.bodyLarge) {
                if (icon != null)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(icon, "", Modifier
                            .align(Alignment.CenterVertically)
                            .padding(8.dp))
                        content()
                    }
                else
                    content()
            }

            if (actionText != null)
                OutlinedButton(
                    onClick = onAction,
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    Text(actionText)
                }
        }
    }
}

@Composable
@Preview
fun ActionCard_Sample() {
    ActionCard(
        icon = Icons.Default.NotificationAdd,
        actionText = "Some Action"
    ) {
        Text("Some Content")
    }
}