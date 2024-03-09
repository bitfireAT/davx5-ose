package at.bitfire.davdroid.ui.widget

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Icon
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun SettingsHeader(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalTextStyle provides MaterialTheme.typography.h6.copy(
            color = MaterialTheme.colors.secondary
        )
    ) {
        Row(Modifier
            .padding(top = 32.dp, start = 48.dp, end = 16.dp, bottom = 8.dp)
            .fillMaxWidth()
        ) {
            content()
        }
    }
}

@Composable
@Preview
fun SettingsHeader_Sample() {
    SettingsHeader {
        Text("Some Settings Section")
    }
}


@Composable
fun Setting(
    icon: @Composable () -> Unit,
    name: @Composable () -> Unit,
    summary: String?,
    end: @Composable () -> Unit = {},
    enabled: Boolean = true,
    onClick: () -> Unit = {}
) {
    var modifier = Modifier.fillMaxWidth()
    if (enabled)
        modifier = modifier.clickable(onClick = onClick)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .width(40.dp)
                .padding(start = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            icon()
        }

        Column(Modifier
            .padding(start = 8.dp)
            .weight(1f)
        ) {
            name()

            if (summary != null)
                Text(summary, style = MaterialTheme.typography.body2)
        }

        end()
    }
}

@Composable
fun Setting(
    name: String,
    summary: String? = null,
    icon: ImageVector? = null,
    onClick: () -> Unit = {}
) {
    Setting(
        icon = {
            if (icon != null)
                Icon(icon, contentDescription = name)
        },
        name = {
            Text(name, style = MaterialTheme.typography.body1)
        },
        summary = summary,
        onClick = onClick
    )
}

@Composable
@Preview
fun Setting_Sample() {
    Setting(
        icon = Icons.Default.Folder,
        name = "Setting",
        summary = "Currently off"
    )
}

@Composable
fun SwitchSetting(
    checked: Boolean,
    name: String,
    summaryOn: String? = null,
    summaryOff: String? = null,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    onCheckedChange: (Boolean) -> Unit = {}
) {
    Setting(
        icon = {
            if (icon != null)
                Icon(icon, name)
        },
        name = {
            Text(name)
        },
        summary = if (checked) summaryOn else summaryOff,
        end = {
            Switch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = onCheckedChange
            )
        },
        enabled = enabled
    ) {
        onCheckedChange(!checked)
    }
}

@Composable
@Preview
fun SwitchSetting_Sample() {
    SwitchSetting(
        name = "Some Switched Setting",
        checked = true
    )
}