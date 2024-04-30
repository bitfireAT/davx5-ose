/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.account

import android.app.Activity
import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import at.bitfire.davdroid.R
import at.bitfire.davdroid.repository.DavSyncStatsRepository
import at.bitfire.davdroid.ui.AppTheme
import at.bitfire.davdroid.ui.composable.ExceptionInfoDialog
import dagger.hilt.android.EntryPointAccessors
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
    val context = LocalContext.current as Activity
    val entryPoint = EntryPointAccessors.fromActivity(context, CollectionActivity.CollectionEntryPoint::class.java)
    val model = viewModel<CollectionScreenModel>(
        factory = CollectionScreenModel.factoryFromCollection(entryPoint.collectionModelAssistedFactory(), collectionId)
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
        privWriteContent = collection.privWriteContent,
        forceReadOnly = collection.forceReadOnly,
        onSetForceReadOnly = model::setForceReadOnly,
        title = collection.title(),
        displayName = collection.displayName,
        description = collection.description,
        owner = model.owner.collectAsStateWithLifecycle(null).value,
        lastSynced = model.lastSynced.collectAsStateWithLifecycle(emptyList()).value,
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
    privWriteContent: Boolean,
    forceReadOnly: Boolean,
    onSetForceReadOnly: (Boolean) -> Unit = {},
    title: String,
    displayName: String? = null,
    description: String? = null,
    owner: String? = null,
    lastSynced: List<DavSyncStatsRepository.LastSynced> = emptyList(),
    supportsWebPush: Boolean = false,
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
                            Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_up))
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
                            Icon(Icons.Default.DeleteForever, contentDescription = stringResource(R.string.delete_collection))
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
                    LinearProgressIndicator(
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
                    // TODO strings i18n

                    CollectionScreen_Entry(
                        icon = Icons.Default.Sync,
                        title = "Synchronization",
                        text = if (sync) "Synchronization enabled" else "Synchronization disabled",
                        control = {
                            Switch(
                                checked = sync,
                                onCheckedChange = onSetSync
                            )
                        }
                    )

                    CollectionScreen_Entry(
                        icon = Icons.Default.DoNotDisturbOn,
                        title = "Read-only",
                        text = when {
                            !privWriteContent -> "Read-only on server"
                            forceReadOnly -> "Forced read-only (client only)"
                            else -> "Read-write"
                        },
                        control = {
                            Switch(
                                checked = forceReadOnly || !privWriteContent,
                                enabled = privWriteContent,
                                onCheckedChange = onSetForceReadOnly
                            )
                        }
                    )

                    if (displayName != null)
                        CollectionScreen_Entry(
                            title = "Title",
                            text = title
                        )

                    if (description != null)
                        CollectionScreen_Entry(
                            title = "Description",
                            text = description
                        )

                    if (owner != null)
                        CollectionScreen_Entry(
                            icon = Icons.Default.AccountBox,
                            title = "Owner",
                            text = owner
                        )

                    if (supportsWebPush)
                        CollectionScreen_Entry(
                            icon = Icons.Default.CloudSync,
                            title = "Web Push",
                            text = "Server advertises Push support"
                        )

                    Column(Modifier.padding(start = 44.dp)) {
                        if (sync && lastSynced.isNotEmpty()) {
                            val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)

                            for (lastSync in lastSynced) {
                                Text(
                                    text = "Last synchronized (${lastSync.appName})",
                                    style = MaterialTheme.typography.titleMedium
                                )

                                val time = ZonedDateTime.ofInstant(Instant.ofEpochMilli(lastSync.lastSynced), ZoneId.systemDefault())
                                Text(
                                    text = formatter.format(time),
                                    style = MaterialTheme.typography.bodyLarge
                                )

                                Spacer(Modifier.height(16.dp))
                            }
                        }

                        Text(
                            text = "URL",
                            style = MaterialTheme.typography.titleMedium
                        )
                        SelectionContainer {
                            Text(
                                text = url,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                modifier = Modifier
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
    control: @Composable (() -> Unit)? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 16.dp)
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
        }

        if (control != null)
            control()
    }
}

@Composable
@Preview
fun CollectionScreen_Preview() {
    CollectionScreen(
        inProgress = true,
        color = 0xff14c0c4.toInt(),
        sync = true,
        privWriteContent = true,
        forceReadOnly = false,
        url = "https://example.com/calendar",
        title = "Some Calendar, with some additional text to make it wrap around and stuff.",
        displayName = "Some Calendar, with some additional text to make it wrap around and stuff.",
        description = "This is some description of the calendar. It can be long and wrap around.",
        owner = "Some One",
        lastSynced = listOf(
            DavSyncStatsRepository.LastSynced(
                appName = "Some Content Provider",
                lastSynced = 1234567890
            )
        ),
        supportsWebPush = true
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
            Text(stringResource(R.string.delete_collection))
        },
        text = {
            Text(stringResource(R.string.delete_collection_confirm_warning, displayName))
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Delete")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
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