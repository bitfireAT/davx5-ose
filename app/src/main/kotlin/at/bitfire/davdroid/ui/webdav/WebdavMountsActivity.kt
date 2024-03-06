/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui.webdav

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.AlertDialog
import androidx.compose.material.Card
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Help
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ShareCompat
import androidx.core.text.HtmlCompat
import androidx.core.view.MenuProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.viewModelScope
import at.bitfire.davdroid.App
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.WebDavDocument
import at.bitfire.davdroid.db.WebDavMount
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.ui.UiUtils
import at.bitfire.davdroid.ui.UiUtils.toAnnotatedString
import at.bitfire.davdroid.ui.widget.SafeAndroidUriHandler
import at.bitfire.davdroid.util.DavUtils
import at.bitfire.davdroid.webdav.CredentialsStore
import at.bitfire.davdroid.webdav.DavDocumentsProvider
import com.google.accompanist.themeadapter.material.MdcTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import org.apache.commons.io.FileUtils
import java.util.logging.Level
import javax.inject.Inject

@AndroidEntryPoint
class WebdavMountsActivity: AppCompatActivity() {

    private val model by viewModels<Model>()

    private val browser = registerForActivityResult(StartActivityForResult()) { result ->
        result.data?.data?.let { uri ->
            ShareCompat.IntentBuilder(this)
                .setType(DavUtils.MIME_TYPE_ACCEPT_ALL)
                .addStream(uri)
                .startChooser()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MdcTheme {
                CompositionLocalProvider(
                    LocalUriHandler provides SafeAndroidUriHandler(this)
                ) {
                    val mountInfos by model.mountInfos.observeAsState(emptyList())

                    WebdavMountsContent(mountInfos)
                }
            }
        }
    }

    private fun helpUrl() =
        App.homepageUrl(this).buildUpon()
            .appendEncodedPath("manual/webdav_mounts.html")
            .build()


    @Composable
    fun WebdavMountsContent(mountInfos: List<MountInfo>) {
        val uriHandler = LocalUriHandler.current

        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(
                            onClick = ::finish
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = null
                            )
                        }
                    },
                    title = { Text(stringResource(R.string.webdav_mounts_title)) },
                    actions = {
                        IconButton(
                            onClick = { uriHandler.openUri(helpUrl().toString()) }
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
                    onClick = { startActivity(Intent(this, AddWebdavMountActivity::class.java)) }
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
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                if (mountInfos.isEmpty()) {
                    HintText()
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .padding(paddingValues)
                    ) {
                        items(mountInfos, key = { it.mount.id }, contentType = { "mount" }) {
                            WebdavMountsItem(it)
                        }
                    }
                }
            }
        }
    }

    @Composable
    @OptIn(ExperimentalTextApi::class)
    fun HintText() {
        val uriHandler = LocalUriHandler.current

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = stringResource(R.string.webdav_mounts_empty),
                style = MaterialTheme.typography.h6,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            )

            val text = HtmlCompat.fromHtml(
                stringResource(
                    R.string.webdav_add_mount_empty_more_info,
                    // helpUrl doesn't work in previews
                    if (LocalInspectionMode.current)
                        "https://example.com"
                    else
                        helpUrl().toString()
                ),
                0
            ).toAnnotatedString()
            ClickableText(
                text = text,
                style = MaterialTheme.typography.body1,
                modifier = Modifier.fillMaxWidth(),
                onClick = { position ->
                    text.getUrlAnnotations(position, position + 1)
                        .firstOrNull()
                        ?.let { uriHandler.openUri(it.item.url) }
                }
            )
        }
    }

    @Composable
    fun WebdavMountsItem(info: MountInfo) {
        var showingDialog by remember { mutableStateOf(false) }
        if (showingDialog) {
            AlertDialog(
                onDismissRequest = { showingDialog = false },
                title = { Text(stringResource(R.string.webdav_remove_mount_title)) },
                text = { Text(stringResource(R.string.webdav_remove_mount_text)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            Logger.log.log(Level.INFO, "User removes mount point", info.mount)
                            model.remove(info.mount)
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            backgroundColor = MaterialTheme.colors.onSecondary
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
                    style = MaterialTheme.typography.body1
                )
                Text(
                    text = info.mount.url.toString(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    style = MaterialTheme.typography.caption
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
                            .padding(top = 12.dp)
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
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            val authority = getString(R.string.webdav_authority)
                            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                                addCategory(Intent.CATEGORY_OPENABLE)
                                type = "*/*"
                            }
                            val uri = DocumentsContract.buildRootUri(authority, info.mount.id.toString())
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, uri)
                            }
                            browser.launch(intent)
                        }
                    ) {
                        Text(
                            text = stringResource(R.string.webdav_mounts_share_content).uppercase()
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
    fun WebdavMountsContent_Preview() {
        MdcTheme {
            WebdavMountsContent(emptyList())
        }
    }

    @Preview(showBackground = true)
    @Composable
    fun WebdavMountsItem_Preview() {
        MdcTheme {
            WebdavMountsItem(
                info = MountInfo(
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
    }


    data class MountInfo(
        val mount: WebDavMount,
        val rootDocument: WebDavDocument?
    )


    @HiltViewModel
    class Model @Inject constructor(
        application: Application,
        val db: AppDatabase
    ): AndroidViewModel(application) {

        val context: Context get() = getApplication()

        val authority = context.getString(R.string.webdav_authority)

        val mountInfos = object: MediatorLiveData<List<MountInfo>>() {
            var mounts: List<WebDavMount>? = null
            var roots: List<WebDavDocument>? = null
            init {
                addSource(db.webDavMountDao().getAllLive()) { newMounts ->
                    mounts = newMounts

                    viewModelScope.launch(Dispatchers.IO) {
                        // query children of root document for every mount to show quota
                        for (mount in newMounts)
                            queryChildrenOfRoot(mount)

                        merge()
                    }
                }
                addSource(db.webDavDocumentDao().getRootsLive()) { newRoots ->
                    roots = newRoots
                    merge()
                }
            }
            @Synchronized
            fun merge() {
                val result = mutableListOf<MountInfo>()
                mounts?.forEach { mount ->
                    result += MountInfo(
                        mount = mount,
                        rootDocument = roots?.firstOrNull { it.mountId == mount.id }
                    )
                }
                postValue(result)
            }
        }

        /**
         * Removes the mountpoint (deleting connection information)
         */
        fun remove(mount: WebDavMount) {
            viewModelScope.launch(Dispatchers.IO) {
                // remove mount from database
                db.webDavMountDao().delete(mount)

                // remove credentials, too
                CredentialsStore(context).setCredentials(mount.id, null)

                // notify content URI listeners
                DavDocumentsProvider.notifyMountsChanged(context)
            }
        }


        private fun queryChildrenOfRoot(mount: WebDavMount) {
            val resolver = context.contentResolver
            db.webDavDocumentDao().getOrCreateRoot(mount).let { root ->
                resolver.query(DocumentsContract.buildChildDocumentsUri(authority, root.id.toString()), null, null, null, null)?.close()
            }
        }

    }

}