/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui.widget

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationAdd
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import at.bitfire.davdroid.R

@Composable
fun ActionCard(
    icon: ImageVector? = null,
    actionText: String? = null,
    onAction: () -> Unit = {},
    content: @Composable () -> Unit
) {
    Card(Modifier
        .padding(horizontal = 8.dp, vertical = 4.dp)
        .fillMaxWidth()
    ) {
        Column(Modifier.padding(top = 8.dp, start = 8.dp, end = 8.dp)) {
            if (icon != null)
                Row {
                    Icon(icon, "", Modifier
                        .align(Alignment.CenterVertically)
                        .padding(end = 8.dp))
                    content()
                }
            else
                content()

            if (actionText != null)
                TextButton(onClick = onAction) {
                    Text(
                        actionText.uppercase(),
                        style = MaterialTheme.typography.button
                    )
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