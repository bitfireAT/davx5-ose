/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.webdav

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ShareCompat
import androidx.core.text.HtmlCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import at.bitfire.davdroid.Constants
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.WebDavDocument
import at.bitfire.davdroid.db.WebDavMount
import at.bitfire.davdroid.db.WebDavMountWithRootDocument
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.ui.UiUtils.toAnnotatedString
import at.bitfire.davdroid.ui.widget.ClickableTextWithLink
import at.bitfire.davdroid.util.DavUtils
import okhttp3.HttpUrl
import org.apache.commons.io.FileUtils

@Composable
fun WebdavMountsScreen(
    onAddWebdavMount: () -> Unit,
    onFinish: () -> Unit,
    model: WebdavMountsModel = viewModel()
) {
    val mountInfos by model.mountInfos.collectAsStateWithLifecycle(emptyList())

    WebdavMountsList(
        mountInfos = mountInfos,
        refreshingQuota = model.refreshingQuota,
        onRefreshQuota = {
            model.refreshQuota()
        },
        onAddMount = onAddWebdavMount,
        onRemoveMount = { mount ->
            model.remove(mount)
        },
        onFinish = onFinish
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
fun WebdavMountsList(
    mountInfos: List<WebDavMountWithRootDocument>,
    refreshingQuota: Boolean = false,
    onRefreshQuota: () -> Unit = {},
    onAddMount: () -> Unit = {},
    onRemoveMount: (WebDavMount) -> Unit = {},
    onFinish: () -> Unit = {}
) {
    val uriHandler = LocalUriHandler.current

    Logger.log.info("refreshingQuota: $refreshingQuota")

    val refreshState = rememberPullRefreshState(
        refreshing = refreshingQuota,
        onRefresh = {
            onRefreshQuota()
        }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = onFinish
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null
                        )
                    }
                },
                title = { Text(stringResource(R.string.webdav_mounts_title)) },
                actions = {
                    IconButton(
                        onClick = {
                            uriHandler.openUri(webdavMountsHelpUrl().toString())
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Help,
                            contentDescription = stringResource(R.string.help)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddMount,
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.webdav_add_mount_add)
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .pullRefresh(refreshState),
            contentAlignment = Alignment.Center
        ) {
            if (mountInfos.isEmpty())
                HintText()
            else
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                ) {
                    items(mountInfos, key = { it.mount.id }, contentType = { "mount" }) {
                        WebdavMountsItem(
                            info = it,
                            onRemoveMount = onRemoveMount
                        )
                    }
                }

            PullRefreshIndicator(
                refreshing = refreshingQuota,
                state = refreshState,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}

@Composable
fun HintText() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = stringResource(R.string.webdav_mounts_empty),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        )

        val text = HtmlCompat.fromHtml(
            stringResource(
                R.string.webdav_add_mount_empty_more_info,
                webdavMountsHelpUrl().toString()
            ),
            0
        ).toAnnotatedString()
        ClickableTextWithLink(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun WebdavMountsItem(
    info: WebDavMountWithRootDocument,
    onRemoveMount: (WebDavMount) -> Unit = {},
) {
    var showingDialog by remember { mutableStateOf(false) }
    if (showingDialog) {
        AlertDialog(
            onDismissRequest = { showingDialog = false },
            title = { Text(stringResource(R.string.webdav_remove_mount_title)) },
            text = { Text(stringResource(R.string.webdav_remove_mount_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRemoveMount(info.mount)
                    }
                ) {
                    Text(stringResource(R.string.dialog_remove))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showingDialog = false }
                ) {
                    Text(stringResource(R.string.dialog_deny))
                }
            }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = info.mount.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = info.mount.url.toString(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
            )

            val quotaUsed = info.rootDocument?.quotaUsed
            val quotaAvailable = info.rootDocument?.quotaAvailable
            if (quotaUsed != null && quotaAvailable != null) {
                val quotaTotal = quotaUsed + quotaAvailable
                val progress = quotaUsed.toFloat() / quotaTotal
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                )
                Text(
                    text = stringResource(
                        R.string.webdav_mounts_quota_used_available,
                        FileUtils.byteCountToDisplaySize(quotaUsed),
                        FileUtils.byteCountToDisplaySize(quotaAvailable)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val context = LocalContext.current

                val browser = rememberLauncherForActivityResult(contract = ActivityResultContracts.StartActivityForResult()) { result ->
                    result.data?.data?.let { uri ->
                        ShareCompat.IntentBuilder(context)
                            .setType(DavUtils.MIME_TYPE_ACCEPT_ALL)
                            .addStream(uri)
                            .startChooser()
                    }
                }

                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "*/*"
                        }
                        val uri = DocumentsContract.buildRootUri(context.getString(R.string.webdav_authority), info.mount.id.toString())
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, uri)
                        }
                        browser.launch(intent)
                    }
                ) {
                    Text(
                        text = stringResource(R.string.webdav_mounts_share_content)
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = { showingDialog = true }
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.webdav_mounts_unmount)
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun WebdavMountsList_Preview_Empty() {
    WebdavMountsList(
        mountInfos = emptyList(),
        refreshingQuota = true
    )
}

@Preview(showBackground = true)
@Composable
fun WebdavMountsItem_Preview() {
    WebdavMountsItem(
        info = WebDavMountWithRootDocument(
            mount = WebDavMount(
                id = 0,
                name = "Preview Webdav Mount",
                url = HttpUrl.Builder()
                    .scheme("https")
                    .host("example.com")
                    .build()
            ),
            rootDocument = WebDavDocument(
                mountId = 0,
                parentId = null,
                name = "Root",
                quotaAvailable = 1024 * 1024 * 1024,
                quotaUsed = 512 * 1024 * 1024
            )
        )
    )
}


private fun webdavMountsHelpUrl(): Uri = Constants.MANUAL_URL.buildUpon()
    .appendPath(Constants.MANUAL_PATH_WEBDAV_MOUNTS)
    .build()
