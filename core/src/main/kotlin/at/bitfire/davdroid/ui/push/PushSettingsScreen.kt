/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.push

import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.visible
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.DefaultAlpha
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import at.bitfire.davdroid.R
import at.bitfire.davdroid.ui.ExternalUris
import at.bitfire.davdroid.ui.UiUtils.toAnnotatedString
import at.bitfire.davdroid.ui.composable.AppTheme
import at.bitfire.davdroid.ui.push.PushSettingsContract.Event
import at.bitfire.davdroid.ui.push.PushSettingsContract.Event.PushDistributorSelected
import at.bitfire.davdroid.ui.push.PushSettingsContract.Event.PushEnabled
import at.bitfire.davdroid.ui.push.PushSettingsContract.PushDistributorInfo
import at.bitfire.davdroid.ui.push.PushSettingsContract.State
import at.bitfire.davdroid.ui.push.PushSettingsContract.State.Content
import at.bitfire.davdroid.ui.push.PushSettingsContract.State.Loading
import kotlinx.coroutines.time.delay
import java.time.Duration

private const val UNIFIED_PUSH_URL = "https://unifiedpush.org"

@Composable
fun PushSettingsScreen(onNavigateUp: () -> Unit) {
    val viewModel = hiltViewModel<PushSettingsModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    PushSettingsContent(
        state = state,
        onEvent = viewModel::onEvent,
        onNavUp = onNavigateUp
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PushSettingsContent(
    state: State,
    onEvent: (Event) -> Unit,
    onNavUp: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_settings_push_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavUp) {
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
            InfoHeader()

            when (state) {
                is Loading -> Loading()
                is Content -> Content(state, onEvent)
            }

            InfoFooter()
        }
    }
}

@Composable
private fun Loading() {
    var showProgressIndicator by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(Duration.ofMillis(500))
        showProgressIndicator = true
    }

    Box(modifier = Modifier
        .fillMaxWidth()
        .visible(showProgressIndicator)
    ) {
        CircularProgressIndicator(
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

@Composable
private fun Content(content: Content, onEvent: (Event) -> Unit) {
    PushEnabled(content.isPushEnabled, onEvent)
    PushServices(content, onEvent)
}

@Composable
private fun InfoHeader() {
    Text(
        text = stringResource(R.string.app_settings_push_description, stringResource(R.string.app_name)),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onBackground,
    )

    Spacer(modifier = Modifier.height(16.dp))
}

@Composable
private fun PushEnabled(isPushEnabled: Boolean, onEvent: (Event) -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }

    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .toggleable(
                value = isPushEnabled,
                onValueChange = { value -> onEvent(PushEnabled(value)) },
                interactionSource = interactionSource,
                indication = ripple(),
                role = Role.Switch
            )
    ) {
        Row(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.app_settings_push_use_push),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier
                    .weight(1f)
                    .align(Alignment.CenterVertically)
            )

            // The ripple effect is displayed on the Surface. Don't show a separate one on the Switch.
            CompositionLocalProvider(LocalRippleConfiguration provides null) {
                Switch(
                    checked = isPushEnabled,
                    onCheckedChange = null,
                    interactionSource = interactionSource,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
            }
        }
    }
}

@Composable
private fun PushServices(content: Content, onEvent: (Event) -> Unit) {
    if (content.pushDistributors.isEmpty()) {
        NoPushServices()
    } else {
        PushServicesList(content, onEvent)
    }
}

@Composable
private fun NoPushServices() {
    Spacer(modifier = Modifier.height(24.dp))

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp)) {
            Icon(
                Icons.Outlined.Info,
                contentDescription = null,
                modifier = Modifier.padding(end = 8.dp)
            )

            val infoText = HtmlCompat.fromHtml(
                stringResource(
                    R.string.app_settings_push_no_push_services_found,
                    UNIFIED_PUSH_URL,
                    stringResource(R.string.app_settings_push_unified_push_url_text)
                ),
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

@Composable
private fun PushServicesList(content: Content, onEvent: (Event) -> Unit) {
    Spacer(modifier = Modifier.height(32.dp))

    Column(modifier = Modifier.selectableGroup()) {
        Text(
            text = stringResource(R.string.app_settings_push_push_services),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold
        )

        content.pushDistributors.forEach { pushDistributor ->
            val isSelected = pushDistributor.packageName == content.selectedPushDistributor
            val isEnabled = content.isPushEnabled

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(shape = RoundedCornerShape(16.dp))
                    .selectable(
                        selected = isSelected,
                        enabled = isEnabled,
                        onClick = { onEvent(PushDistributorSelected(pushDistributor.packageName)) },
                        role = Role.RadioButton
                    )
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .padding(horizontal = 16.dp)
            ) {
                RadioButton(
                    selected = isSelected,
                    enabled = isEnabled,
                    onClick = null // null recommended for accessibility with screen readers
                )

                val title = if (content.defaultPushDistributor == pushDistributor.packageName) {
                    buildAnnotatedString {
                        append(pushDistributor.appName)
                        append(" ")
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(stringResource(R.string.app_settings_push_default_distributor_text))
                        }
                    }
                } else {
                    AnnotatedString(pushDistributor.appName)
                }

                Text(
                    text = title,
                    color = if (isEnabled) {
                        Color.Unspecified
                    } else {
                        if (isSelected) {
                            RadioButtonDefaults.colors().disabledSelectedColor
                        } else {
                            RadioButtonDefaults.colors().disabledUnselectedColor
                        }
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .padding(start = 16.dp, end = 8.dp)
                        .weight(1f)
                )

                pushDistributor.appIcon?.let { icon ->
                    val appIconBitmap = remember(pushDistributor.appIcon) { icon.toBitmap().asImageBitmap() }
                    Image(
                        bitmap = appIconBitmap,
                        contentDescription = null,
                        alpha = if (isEnabled) DefaultAlpha else 0.5f,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoFooter() {
    Spacer(modifier = Modifier.height(32.dp))

    Icon(imageVector = Icons.Outlined.Info, contentDescription = null)
    Spacer(modifier = Modifier.height(8.dp))

    val url = remember {
        ExternalUris.Manual.baseUrl.buildUpon()
            .appendPath(ExternalUris.Manual.PATH_WEBDAV_PUSH)
            .build()
            .toString()
    }

    Text(
        text = HtmlCompat.fromHtml(
            stringResource(R.string.app_settings_push_info_text, url),
            HtmlCompat.FROM_HTML_MODE_COMPACT
        ).toAnnotatedString(),
        style = MaterialTheme.typography.bodyMedium,
    )
}


@Preview
@Composable
private fun PushSettingsScreenPreview_Loading() {
    AppTheme {
        PushSettingsContent(
            state = Loading,
            onEvent = {},
            onNavUp = {}
        )
    }
}

@Preview
@Composable
private fun PushSettingsScreenPreview_NoExternalPushDistributors() {
    AppTheme {
        val context = LocalContext.current

        PushSettingsContent(
            state = Content(
                selectedPushDistributor = context.packageName,
                pushDistributors = listOf(
                    PushDistributorInfo(
                        packageName = context.packageName,
                        appName = "FCM (Google Play)",
                        appIcon = AppCompatResources.getDrawable(context, R.drawable.product_logomark_cloud_messaging_full_color)
                    )
                ),
            ),
            onEvent = {},
            onNavUp = {}
        )
    }
}

@Preview
@Composable
private fun PushSettingsScreenPreview_AbsolutelyNoPushDistributors() {
    AppTheme {
        PushSettingsContent(
            state = Content(
                pushDistributors = listOf(),
            ),
            onEvent = {},
            onNavUp = {}
        )
    }
}

@Preview
@Composable
private fun PushSettingsScreenPreview_MultiplePushDistributors() {
    AppTheme {
        val context = LocalContext.current

        PushSettingsContent(
            state = Content(
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
            ),
            onEvent = {},
            onNavUp = {}
        )
    }
}
