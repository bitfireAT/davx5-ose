/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.repository.DavSyncStatsRepository
import at.bitfire.davdroid.sync.SyncDataType
import at.bitfire.davdroid.ui.composable.AppTheme
import at.bitfire.davdroid.ui.composable.ExceptionInfoDialog
import at.bitfire.davdroid.ui.composable.ProgressBar
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun CollectionScreen(
    collectionId: Long,
    onFinish: () -> Unit,
    onNavUp: () -> Unit
) {
    val model: CollectionScreenModel = hiltViewModel(
        creationCallback = { factory: CollectionScreenModel.Factory ->
            factory.create(collectionId)
        }
    )

    val collectionOrNull by model.collection.collectAsStateWithLifecycle(null)
    if (model.invalid) {
        onFinish()
        return
    }

    val collection = collectionOrNull ?: return
    CollectionScreen(
        inProgress = model.inProgress,
        error = model.error,
        onResetError = model::resetError,
        color = collection.color,
        sync = collection.sync,
        onSetSync = model::setSync,
        readOnly = model.readOnly.collectAsStateWithLifecycle(CollectionScreenModel.ReadOnlyState.READ_WRITE).value,
        onSetForceReadOnly = model::setForceReadOnly,
        title = collection.title(),
        displayName = collection.displayName,
        localDisplayName = collection.localDisplayName,
        onSetLocalDisplayName = model::setLocalDisplayName,
        supportsLocalRename = collection.type == Collection.TYPE_CALENDAR || collection.type == Collection.TYPE_WEBCAL,
        description = collection.description,
        owner = model.owner.collectAsStateWithLifecycle(null).value,
        localItemCounts = model.localItemCounts.collectAsStateWithLifecycle(initialValue = emptyList()).value,
        pastEventTimeLimit = model.pastEventTimeLimit.collectAsStateWithLifecycle(null).value,
        lastSynced = model.lastSynced.collectAsStateWithLifecycle(emptyList()).value,
        supportsWebPush = collection.supportsWebPush,
        pushSubscriptionCreated = collection.pushSubscriptionCreated,
        pushSubscriptionExpires = collection.pushSubscriptionExpires,
        url = collection.url.toString(),
        onDelete = model::delete,
        onNavUp = onNavUp
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionScreen(
    inProgress: Boolean,
    error: Exception? = null,
    onResetError: () -> Unit = {},
    color: Int?,
    sync: Boolean,
    onSetSync: (Boolean) -> Unit = {},
    readOnly: CollectionScreenModel.ReadOnlyState,
    onSetForceReadOnly: (Boolean) -> Unit = {},
    title: String,
    displayName: String? = null,
    localDisplayName: String? = null,
    onSetLocalDisplayName: (String?) -> Unit = {},
    supportsLocalRename: Boolean = false,
    description: String? = null,
    owner: String? = null,
    lastSynced: List<DavSyncStatsRepository.LastSynced> = emptyList(),
    localItemCounts: List<CollectionScreenModel.LocalItemsCount>,
    pastEventTimeLimit: Int?,
    supportsWebPush: Boolean = false,
    pushSubscriptionCreated: Long? = null,
    pushSubscriptionExpires: Long? = null,
    url: String,
    onDelete: () -> Unit = {},
    onNavUp: () -> Unit = {}
) {
    AppTheme {
        if (error != null)
            ExceptionInfoDialog(
                exception = error,
                onDismiss = onResetError
            )

        Scaffold(
            topBar = {
                MediumTopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onNavUp) {
                            Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = stringResource(R.string.navigate_up))
                        }
                    },
                    title = {
                        Text(
                            text = title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    actions = {
                        var showDeleteDialog by remember { mutableStateOf(false) }
                        IconButton(
                            onClick = { showDeleteDialog = true },
                            enabled = !inProgress
                        ) {
                            Icon(Icons.Default.DeleteForever, contentDescription = stringResource(R.string.collection_delete))
                        }

                        if (showDeleteDialog)
                            DeleteCollectionDialog(
                                displayName = title,
                                onDismiss = { showDeleteDialog = false },
                                onConfirm = {
                                    onDelete()
                                    showDeleteDialog = false
                                }
                            )
                    }
                )
            }
        ) { padding ->
            Column(
                Modifier
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
            ) {
                if (inProgress)
                    ProgressBar(
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp))

                if (color != null) {
                    Box(
                        Modifier
                            .background(Color(color))
                            .fillMaxWidth()
                            .height(16.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                }

                Column(Modifier.padding(8.dp)) {
                    CollectionScreen_Entry(
                        icon = Icons.Default.Sync,
                        title = stringResource(R.string.collection_synchronization),
                        text =
                            if (sync)
                                stringResource(R.string.collection_synchronization_on)
                            else
                                stringResource(R.string.collection_synchronization_off),
                        control = {
                            Switch(
                                checked = sync,
                                onCheckedChange = onSetSync
                            )
                        }
                    )

                    CollectionScreen_Entry(
                        icon = Icons.Default.DoNotDisturbOn,
                        title = stringResource(R.string.collection_read_only),
                        text = when (readOnly) {
                            CollectionScreenModel.ReadOnlyState.READ_ONLY_BY_SERVER ->
                                stringResource(R.string.collection_read_only_by_server)
                            CollectionScreenModel.ReadOnlyState.READ_ONLY_BY_SETTING ->
                                stringResource(R.string.collection_read_only_by_setting)
                            CollectionScreenModel.ReadOnlyState.READ_ONLY_BY_USER ->
                                stringResource(R.string.collection_read_only_forced)
                            else -> stringResource(R.string.collection_read_write)
                        },
                        control = {
                            Switch(
                                checked = readOnly.isReadOnly(),
                                enabled = readOnly.canUserChange(),
                                onCheckedChange = onSetForceReadOnly
                            )
                        }
                    )

                    if (supportsLocalRename) {
                        var showRenameDialog by remember { mutableStateOf(false) }
                        CollectionScreen_Entry(
                            icon = Icons.Default.DriveFileRenameOutline,
                            title = stringResource(R.string.collection_local_rename),
                            text = localDisplayName ?: stringResource(R.string.collection_local_rename_off),
                            onClick = { showRenameDialog = true },
                            control = {
                                // Reflect "pending on" while the dialog is open so the switch state
                                // matches the user's tap immediately (flips back if they cancel).
                                Switch(
                                    checked = localDisplayName != null || showRenameDialog,
                                    onCheckedChange = { enabled ->
                                        if (enabled) showRenameDialog = true
                                        else onSetLocalDisplayName(null)
                                    }
                                )
                            }
                        )
                        if (showRenameDialog)
                            LocalRenameDialog(
                                initialName = localDisplayName ?: displayName ?: title,
                                onDismiss = { showRenameDialog = false },
                                onConfirm = { newName ->
                                    onSetLocalDisplayName(newName)
                                    showRenameDialog = false
                                }
                            )
                    }

                    if (displayName != null)
                        CollectionScreen_Entry(
                            title = stringResource(R.string.collection_title),
                            text = displayName
                        )

                    if (description != null)
                        CollectionScreen_Entry(
                            title = stringResource(R.string.collection_description),
                            text = description
                        )

                    if (owner != null)
                        CollectionScreen_Entry(
                            icon = Icons.Default.AccountBox,
                            title = stringResource(R.string.collection_owner),
                            text = owner
                        )

                    if (localItemCounts.isNotEmpty())
                        CollectionScreen_Entry(
                            icon = Icons.Default.BarChart,
                            title = stringResource(R.string.collection_synced_items_title)
                        ) {
                            for (count in localItemCounts) {
                                Text(
                                    text = pluralStringResource(R.plurals.collection_synced_items_total, count.total, count.total, count.contentProviderName),
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                                Text(
                                    text = pluralStringResource( R.plurals.collection_synced_items_modified, count.modified, count.modified),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = pluralStringResource( R.plurals.collection_synced_items_deleted, count.deleted, count.deleted),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }

                            if (pastEventTimeLimit != null)
                                Text(
                                    text = pluralStringResource( R.plurals.collection_synced_items_past_event_time_limit, pastEventTimeLimit, pastEventTimeLimit),
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                        }

                    if (sync && lastSynced.isNotEmpty())
                        CollectionScreen_Entry {
                            val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)

                            for ((idx, lastSync) in lastSynced.withIndex()) {
                                if (idx != 0)
                                    Spacer(Modifier.height(8.dp))

                                val dataType = when (lastSync.dataType) {
                                    SyncDataType.EVENTS.name -> stringResource(R.string.collection_datatype_events)
                                    SyncDataType.TASKS.name -> stringResource(R.string.collection_datatype_tasks)
                                    SyncDataType.CONTACTS.name -> stringResource(R.string.collection_datatype_contacts)
                                    else -> lastSync.dataType
                                }
                                Text(
                                    text = stringResource(R.string.collection_last_sync, dataType),
                                    style = MaterialTheme.typography.titleMedium
                                )

                                val time = ZonedDateTime.ofInstant(Instant.ofEpochMilli(lastSync.lastSynced), ZoneId.systemDefault())
                                Text(
                                    text = formatter.format(time),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }

                    if (supportsWebPush) {
                        val text =
                            if (pushSubscriptionCreated != null && pushSubscriptionExpires != null) {
                                val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withZone(ZoneId.systemDefault())
                                stringResource(
                                    R.string.collection_push_subscribed_at,
                                    formatter.format(Instant.ofEpochSecond(pushSubscriptionCreated)),
                                    formatter.format(Instant.ofEpochSecond(pushSubscriptionExpires))
                                )
                            } else
                                stringResource(R.string.collection_push_web_push)
                        CollectionScreen_Entry(
                            icon = Icons.Default.CloudSync,
                            title = stringResource(R.string.collection_push_support),
                            text = text
                        )
                    }

                    CollectionScreen_Entry(
                        title = stringResource(R.string.collection_url),
                        isLast = true
                    ) {
                        SelectionContainer {
                            Text(
                                text = url,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CollectionScreen_Entry(
    icon: ImageVector? = null,
    title: String? = null,
    text: String? = null,
    isLast: Boolean = false,
    onClick: (() -> Unit)? = null,
    control: @Composable (() -> Unit)? = null,
    content: @Composable (() -> Unit)? = null
) {
    Row(
        verticalAlignment = if (content != null) Alignment.Top else Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(role = Role.Button, onClick = onClick) else it }
    ) {
        if (icon != null)
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier
                    .padding(end = 12.dp)
                    .size(32.dp)
            )
        else
            Spacer(Modifier.width(44.dp))

        Column(Modifier.weight(1f)) {
            if (title != null)
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )

            if (text != null)
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge
                )

            content?.invoke()
        }

        if (control != null)
            control()
    }

    Spacer(Modifier.height(12.dp))
    if (!isLast) {
        HorizontalDivider(modifier = Modifier.padding(start = 44.dp))
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
@Preview
fun CollectionScreen_Preview() {
    CollectionScreen(
        inProgress = true,
        color = 0xff14c0c4.toInt(),
        sync = true,
        readOnly = CollectionScreenModel.ReadOnlyState.READ_ONLY_BY_USER,
        url = "https://example.com/calendar",
        title = "Some Calendar, with some additional text to make it wrap around and stuff.",
        displayName = "Some Calendar, with some additional text to make it wrap around and stuff.",
        localDisplayName = "My Local Name",
        supportsLocalRename = true,
        description = "This is some description of the calendar. It can be long and wrap around.",
        owner = "Some One",
        lastSynced = listOf(
            DavSyncStatsRepository.LastSynced(
                dataType = "Some Sync Data Type",
                lastSynced = 1234567890
            )
        ),
        pastEventTimeLimit = 90,
        localItemCounts = listOf(
            CollectionScreenModel.LocalItemsCount(
                contentProviderName = "Calender Storage",
                total = 150,
                modified = 2,
                deleted = 1
            ),
            CollectionScreenModel.LocalItemsCount(
                contentProviderName = "Some Tasks App",
                total = 10,
                modified = 0,
                deleted = 1
            )
        ),
        supportsWebPush = true,
        pushSubscriptionCreated = 1731846565,
        pushSubscriptionExpires = 1731847565
    )
}


@Composable
fun DeleteCollectionDialog(
    displayName: String,
    onDismiss: () -> Unit = {},
    onConfirm: () -> Unit = {}
) {
    AlertDialog(
        icon = {
            Icon(Icons.Default.DeleteForever, contentDescription = null)
        },
        title = {
            Text(stringResource(R.string.collection_delete))
        },
        text = {
            Text(stringResource(R.string.collection_delete_warning, displayName))
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(stringResource(R.string.dialog_delete))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
        onDismissRequest = onDismiss
    )
}

@Composable
@Preview
fun DeleteCollectionDialog_Preview() {
    DeleteCollectionDialog(
        displayName = "Some Calendar"
    )
}


@Composable
fun LocalRenameDialog(
    initialName: String,
    onDismiss: () -> Unit = {},
    onConfirm: (newName: String) -> Unit = {}
) {
    var name by remember {
        mutableStateOf(TextFieldValue(initialName, selection = TextRange(initialName.length)))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null) },
        title = { Text(stringResource(R.string.collection_local_rename)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.collection_local_rename_description),
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                val focusRequester = remember { FocusRequester() }
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.collection_local_rename_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = { if (name.text.isNotBlank()) onConfirm(name.text) }
                    ),
                    modifier = Modifier.focusRequester(focusRequester)
                )
                LaunchedEffect(Unit) {
                    focusRequester.requestFocus()
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name.text) },
                enabled = name.text.isNotBlank()
            ) {
                Text(stringResource(R.string.dialog_save))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}

@Composable
@Preview
fun LocalRenameDialog_Preview() {
    LocalRenameDialog(initialName = "My Calendar")
}
