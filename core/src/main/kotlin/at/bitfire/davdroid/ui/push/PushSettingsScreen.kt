/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.push

import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.core.text.HtmlCompat
import at.bitfire.davdroid.R
import at.bitfire.davdroid.ui.UiUtils.toAnnotatedString
import at.bitfire.davdroid.ui.composable.AppTheme
import at.bitfire.davdroid.ui.push.PushSettingsContract.PushCollectionsMessage
import at.bitfire.davdroid.ui.push.PushSettingsContract.PushDistributorInfo
import at.bitfire.davdroid.ui.push.PushSettingsContract.State

//TODO: proper edge-to-edge
//TODO: whole "use push" row needs to be clickable
//TODO: test on a device without play services to ensure FCM option isn't displayed
//TODO: link text should show selected/active state (tab navigation)
//TODO: listen to app install/uninstall events while the screen is active -> update list of push distributors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PushSettingsScreen(
    state: State,
    onNavUp: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Push (Experimental)") },
                navigationIcon = {
                    IconButton(
                        onClick = onNavUp
                    ) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, stringResource(R.string.navigate_up))
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(Modifier
            .verticalScroll(rememberScrollState())
            .padding(paddingValues)
            .padding(16.dp)
            .fillMaxSize()
        ) {
            Text(
                text = "Use Push to have CalDAV/CardDAV/WebDAV servers instantly notify DAVx⁵ of available changes to your data on the server.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(16.dp))

            //TODO: Use an image where a computer sends changes to the server and the server notifies the mobile device

            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Use Push",
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f)
                            .align(Alignment.CenterVertically)
                    )
                    Switch(checked = state.isPushEnabled, onCheckedChange = { }, modifier = Modifier.align(Alignment.CenterVertically))
                }
            }

            val pushCollectionText = when (state.pushCollectionsMessage) {
                PushCollectionsMessage.Disabled -> null
                PushCollectionsMessage.NonePushCapable -> "None of your server collections currently support WebDAV-Push."
                PushCollectionsMessage.SomePushCapable -> "Only some of your server collections currently support WebDAV-Push."
            }

            if (pushCollectionText != null) {
                Spacer(modifier = Modifier.height(24.dp))
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Info,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp)
                        )

                        Text(
                            text = pushCollectionText,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            if (state.showSelectDefaultPushDistributorCard) {
                Spacer(modifier = Modifier.height(24.dp))
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth()
                    ) {
                        Text(
                            text = "There are multiple UnifiedPush Distributor Apps installed. Select a system-wide default so UnifiedPush-enabled apps don't have to ask which one to use.",
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButton(onClick = {}, modifier = Modifier.align(Alignment.End)) {
                            Text("Set system-wide default")
                        }
                    }
                }
            }

            if (state.pushDistributors.isEmpty()) {
                Spacer(modifier = Modifier.height(24.dp))
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Info,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp)
                        )

                        val infoText = HtmlCompat.fromHtml(
                            """No Push Services found. Visit <a href="https://unifiedpush.org">UnifiedPush.org</a> for a list of Distributor Apps.""",
                            HtmlCompat.FROM_HTML_MODE_COMPACT
                        ).toAnnotatedString()
                        Text(
                            text = infoText,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(32.dp))
                Column(modifier = Modifier.selectableGroup()) {

                    Text("Push Service", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)

                    state.pushDistributors.forEach { pushDistributor ->
                        val isSelected = pushDistributor.packageName == state.selectedPushDistributor
                        val isEnabled = state.isPushEnabled
                        Row(
                            Modifier
                                .clip(shape = RoundedCornerShape(16.dp))
                                .selectable(
                                    selected = isSelected,
                                    enabled = isEnabled,
                                    onClick = { },
                                    role = Role.RadioButton
                                )
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                enabled = isEnabled,
                                onClick = null // null recommended for accessibility with screen readers
                            )

                            val title = if (state.defaultPushDistributor == pushDistributor.packageName) {
                                buildAnnotatedString {
                                    append(pushDistributor.appName)
                                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                        append(" (Default)")
                                    }
                                }
                            } else {
                                AnnotatedString(pushDistributor.appName)
                            }

                            Text(
                                text = title,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier
                                    .padding(start = 16.dp, end = 8.dp)
                                    .weight(1f)
                            )
                            pushDistributor.appIcon?.let { icon ->
                                Image(
                                    bitmap = icon.toBitmap().asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }
                }

                if (state.showAdvertiseUnifiedPushCard) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Info,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 8.dp)
                            )

                            val infoText = HtmlCompat.fromHtml(
                                """This app also supports decentralized Push solutions via <a href="https://unifiedpush.org">UnifiedPush</a>.""",
                                HtmlCompat.FROM_HTML_MODE_COMPACT
                            ).toAnnotatedString()
                            Text(
                                text = infoText,
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
            Icon(Icons.Outlined.Info, "Info")
            Spacer(modifier = Modifier.height(8.dp))
            val infoText = HtmlCompat.fromHtml(
                """ Push messages are always encrypted.
                    Learn more about WebDAV-Push from the <a href="https://manual.davx5.com/webdav_push.html">DAVx⁵ manual</a>.
                """.trimIndent(),
                HtmlCompat.FROM_HTML_MODE_COMPACT
            ).toAnnotatedString()
            Text(
                text = infoText,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Preview
@Composable
private fun PushSettingsScreenPreview_NoExternalPushDistributors() {
    Theme {
        val context = LocalContext.current

        PushSettingsScreen(
            state = State(
                isPushEnabled = true,
                pushCollectionsMessage = PushCollectionsMessage.Disabled,
                selectedPushDistributor = context.packageName,
                pushDistributors = listOf(
                    PushDistributorInfo(
                        packageName = context.packageName,
                        appName = "FCM (Google Play)",
                        appIcon = AppCompatResources.getDrawable(context, R.drawable.product_logomark_cloud_messaging_full_color)
                    )
                ),
                showSelectDefaultPushDistributorCard = false,
                showAdvertiseUnifiedPushCard = true,
            ),
            onNavUp = {}
        )
    }
}

@Preview
@Composable
private fun PushSettingsScreenPreview_AbsolutelyNoPushDistributors() {
    Theme {
        PushSettingsScreen(
            state = State(
                isPushEnabled = true,
                pushDistributors = listOf(),
                showSelectDefaultPushDistributorCard = false,
            ),
            onNavUp = {}
        )
    }
}

@Preview
@Composable
private fun PushSettingsScreenPreview_OneUnifiedPushDistributor() {
    Theme {
        val context = LocalContext.current

        PushSettingsScreen(
            state = State(
                isPushEnabled = true,
                pushCollectionsMessage = PushCollectionsMessage.SomePushCapable,
                selectedPushDistributor = "ntfy",
                pushDistributors = listOf(
                    PushDistributorInfo(
                        packageName = "ntfy",
                        appName = "ntfy",
                        appIcon = null
                    ),
                    PushDistributorInfo(
                        packageName = context.packageName,
                        appName = "FCM (Google Play)",
                        appIcon = AppCompatResources.getDrawable(context, R.drawable.product_logomark_cloud_messaging_full_color)
                    )
                ),
                showSelectDefaultPushDistributorCard = false
            ),
            onNavUp = {}
        )
    }
}

@Preview
@Composable
private fun PushSettingsScreenPreview_MultiplePushDistributorsNoDefault() {
    Theme {
        val context = LocalContext.current

        PushSettingsScreen(
            state = State(
                isPushEnabled = true,
                pushCollectionsMessage = PushCollectionsMessage.NonePushCapable,
                selectedPushDistributor = "sunup",
                defaultPushDistributor = null,
                pushDistributors = listOf(
                    PushDistributorInfo(
                        packageName = "ntfy",
                        appName = "ntfy",
                        appIcon = null
                    ),
                    PushDistributorInfo(
                        packageName = "sunup",
                        appName = "Sunup",
                        appIcon = null
                    ),
                    PushDistributorInfo(
                        packageName = context.packageName,
                        appName = "FCM (Google Play)",
                        appIcon = AppCompatResources.getDrawable(context, R.drawable.product_logomark_cloud_messaging_full_color)
                    )
                ),
                showSelectDefaultPushDistributorCard = true,
            ),
            onNavUp = {}
        )
    }
}

@Preview
@Composable
private fun PushSettingsScreenPreview_MultiplePushDistributorsWithDefault() {
    Theme {
        val context = LocalContext.current

        PushSettingsScreen(
            state = State(
                isPushEnabled = true,
                selectedPushDistributor = "sunup",
                defaultPushDistributor = "ntfy",
                pushDistributors = listOf(
                    PushDistributorInfo(
                        packageName = "ntfy",
                        appName = "ntfy",
                        appIcon = null
                    ),
                    PushDistributorInfo(
                        packageName = "sunup",
                        appName = "Sunup",
                        appIcon = null
                    ),
                    PushDistributorInfo(
                        packageName = context.packageName,
                        appName = "FCM (Google Play)",
                        appIcon = AppCompatResources.getDrawable(context, R.drawable.product_logomark_cloud_messaging_full_color)
                    )
                ),
                showSelectDefaultPushDistributorCard = false,
            ),
            onNavUp = {}
        )
    }
}

@Preview
@Composable
private fun PushSettingsScreenPreview_PushDisabled_OneUnifiedPushDistributor() {
    Theme {
        val context = LocalContext.current

        PushSettingsScreen(
            state = State(
                isPushEnabled = false,
                selectedPushDistributor = "sunup",
                pushDistributors = listOf(
                    PushDistributorInfo(
                        packageName = "sunup",
                        appName = "Sunup",
                        appIcon = null
                    ),
                    PushDistributorInfo(
                        packageName = context.packageName,
                        appName = "FCM (Google Play)",
                        appIcon = AppCompatResources.getDrawable(context, R.drawable.product_logomark_cloud_messaging_full_color)
                    )
                ),
                showSelectDefaultPushDistributorCard = false,
            ),
            onNavUp = {}
        )
    }
}


@Preview
@Composable
private fun PushSettingsScreenPreview_Sad() {
    Theme {
        PushSettingsScreen(
            state = State(
                isPushEnabled = true,
                pushCollectionsMessage = PushCollectionsMessage.NonePushCapable,
                pushDistributors = listOf(),
                showSelectDefaultPushDistributorCard = false,
            ),
            onNavUp = {}
        )
    }
}

@Composable
private fun Theme(content: @Composable () -> Unit) {
    AppTheme(content = content)
//    MaterialTheme(content = content)
}