package at.bitfire.davdroid.ui.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

@Composable
fun PermissionSwitchRow(
    text: String,
    allPermissionsGranted: Boolean,
    summaryWhenGranted: String,
    summaryWhenNotGranted: String,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight = FontWeight.Normal,
    onLaunchRequest: () -> Unit
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = text,
                modifier = Modifier.fillMaxWidth(),
                fontWeight = fontWeight,
                style = MaterialTheme.typography.body1
            )
            Text(
                text = if (allPermissionsGranted) summaryWhenGranted else summaryWhenNotGranted,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.body2
            )
        }
        Switch(
            checked = allPermissionsGranted,
            enabled = !allPermissionsGranted,
            onCheckedChange = { checked ->
                if (checked) {
                    onLaunchRequest()
                }
            }
        )
    }
}

@Composable
@OptIn(ExperimentalPermissionsApi::class)
fun PermissionSwitchRow(
    text: String,
    permissions: List<String>,
    summaryWhenGranted: String,
    summaryWhenNotGranted: String,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight = FontWeight.Normal
) {
    if (LocalInspectionMode.current) {
        // preview
        PermissionSwitchRow(
            text = text,
            fontWeight = fontWeight,
            summaryWhenGranted = summaryWhenGranted,
            summaryWhenNotGranted = summaryWhenNotGranted,
            allPermissionsGranted = false,
            onLaunchRequest = {},
            modifier = modifier
        )
        return
    }

    val state = rememberMultiplePermissionsState(permissions = permissions.toList())
    PermissionSwitchRow(
        text = text,
        fontWeight = fontWeight,
        summaryWhenGranted = summaryWhenGranted,
        summaryWhenNotGranted = summaryWhenNotGranted,
        allPermissionsGranted = state.allPermissionsGranted,
        onLaunchRequest = state::launchMultiplePermissionRequest,
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun PermissionSwitchRow_Preview_NotGranted() {
    PermissionSwitchRow(
        text = "Contacts",
        allPermissionsGranted = false,
        summaryWhenGranted = "Granted",
        summaryWhenNotGranted = "Not granted",
        onLaunchRequest = {}
    )
}

@Preview(showBackground = true)
@Composable
fun PermissionSwitchRow_Preview_Granted() {
    PermissionSwitchRow(
        text = "Contacts",
        allPermissionsGranted = true,
        summaryWhenGranted = "Granted",
        summaryWhenNotGranted = "Not granted",
        onLaunchRequest = {}
    )
}