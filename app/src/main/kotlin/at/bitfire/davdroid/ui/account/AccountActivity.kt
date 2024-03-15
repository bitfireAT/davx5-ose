package at.bitfire.davdroid.ui.account

import android.Manifest
import android.accounts.Account
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.AlertDialog
import androidx.compose.material.Checkbox
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ProgressIndicatorDefaults
import androidx.compose.material.Scaffold
import androidx.compose.material.SnackbarDuration
import androidx.compose.material.SnackbarHost
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material.icons.outlined.RuleFolder
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.resource.TaskUtils
import at.bitfire.davdroid.servicedetection.RefreshCollectionsWorker
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.syncadapter.OneTimeSyncWorker
import at.bitfire.davdroid.ui.AppTheme
import at.bitfire.davdroid.ui.PermissionsActivity
import at.bitfire.davdroid.ui.widget.ActionCard
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AccountActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ACCOUNT = "account"
    }

    @Inject
    lateinit var modelFactory: AccountModel.Factory
    val model by viewModels<AccountModel> {
        val account = intent.getParcelableExtra(EXTRA_ACCOUNT) as? Account
            ?: throw IllegalArgumentException("AccountActivity requires EXTRA_ACCOUNT")
        object: ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T: ViewModel> create(modelClass: Class<T>) =
                modelFactory.create(account) as T
        }
    }

    /** Tri-state enum to represent active / pending / idle status */
    enum class Progress {
        Active,     // syncing or refreshing
        Pending,    // sync pending
        Idle
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        model.invalid.observe(this) { invalid ->
            if (invalid)
                // account does not exist anymore
                finish()
        }
        model.renameAccountError.observe(this) { error ->
            if (error != null) {
                Toast.makeText(this, error, Toast.LENGTH_LONG).show()
                model.renameAccountError.value = null
            }
        }

        setContent {
            AppTheme {
                val cardDavSvc by model.cardDavSvc.observeAsState()
                val canCreateAddressBook by model.canCreateAddressBook.observeAsState(false)
                val cardDavRefreshing by model.cardDavRefreshing.observeAsState(false)
                val cardDavSyncPending by model.cardDavSyncPending.observeAsState(false)
                val cardDavSyncing by model.cardDavSyncing.observeAsState(false)
                val cardDavProgress: Progress = when {
                    cardDavRefreshing || cardDavSyncing -> Progress.Active
                    cardDavSyncPending -> Progress.Pending
                    else -> Progress.Idle
                }
                val addressBooks by model.addressBooksPager.observeAsState()

                val calDavSvc by model.calDavSvc.observeAsState()
                val canCreateCalendar by model.canCreateCalendar.observeAsState(false)
                val calDavRefreshing by model.calDavRefreshing.observeAsState(false)
                val calDavSyncPending by model.calDavSyncPending.observeAsState(false)
                val calDavSyncing by model.calDavSyncing.observeAsState(false)
                val calDavProgress: Progress = when {
                    calDavRefreshing || calDavSyncing -> Progress.Active
                    calDavSyncPending -> Progress.Pending
                    else -> Progress.Idle
                }
                val calendars by model.calendarsPager.observeAsState()
                val subscriptions by model.webcalPager.observeAsState()

                var installIcsx5 by remember { mutableStateOf(false) }

                AccountOverview(
                    account = model.account,
                    showOnlyPersonal =
                        model.showOnlyPersonal.observeAsState(
                            AccountSettings.ShowOnlyPersonal(onlyPersonal = false, locked = true)
                        ).value,
                    onSetShowOnlyPersonal = {
                        model.setShowOnlyPersonal(it)
                    },
                    hasCardDav = cardDavSvc != null,
                    canCreateAddressBook = canCreateAddressBook,
                    cardDavProgress = cardDavProgress,
                    cardDavRefreshing = cardDavRefreshing,
                    addressBooks = addressBooks?.flow?.collectAsLazyPagingItems(),
                    hasCalDav = calDavSvc != null,
                    canCreateCalendar = canCreateCalendar,
                    calDavProgress = calDavProgress,
                    calDavRefreshing = calDavRefreshing,
                    calendars = calendars?.flow?.collectAsLazyPagingItems(),
                    subscriptions = subscriptions?.flow?.collectAsLazyPagingItems(),
                    onUpdateCollectionSync = { collectionId, sync ->
                        model.setCollectionSync(collectionId, sync)
                    },
                    onChangeForceReadOnly = { id, forceReadOnly ->
                        model.setCollectionForceReadOnly(id, forceReadOnly)
                    },
                    onSubscribe = { item ->
                        installIcsx5 = !subscribeWebcal(item)
                    },
                    installIcsx5 = installIcsx5,
                    onRefreshCollections = {
                        cardDavSvc?.let { svc ->
                            RefreshCollectionsWorker.enqueue(this@AccountActivity, svc.id)
                        }
                        calDavSvc?.let { svc ->
                            RefreshCollectionsWorker.enqueue(this@AccountActivity, svc.id)
                        }
                    },
                    onSync = {
                        OneTimeSyncWorker.enqueueAllAuthorities(this, model.account, manual = true)
                    },
                    onAccountSettings = {
                        val intent = Intent(this, AccountSettingsActivity::class.java)
                        intent.putExtra(AccountSettingsActivity.EXTRA_ACCOUNT, model.account)
                        startActivity(intent, null)
                    },
                    onRenameAccount = { newName ->
                        model.renameAccount(newName)
                    },
                    onDeleteAccount = {
                        model.deleteAccount()
                    },
                    onNavigateUp = ::onSupportNavigateUp
                )
            }
        }
    }

    /**
     * Subscribes to a Webcal using a compatible app like ICSx5.
     *
     * @return true if a compatible Webcal app is installed, false otherwise
     */
    private fun subscribeWebcal(item: Collection): Boolean {
        // subscribe
        var uri = Uri.parse(item.source.toString())
        when {
            uri.scheme.equals("http", true) -> uri = uri.buildUpon().scheme("webcal").build()
            uri.scheme.equals("https", true) -> uri = uri.buildUpon().scheme("webcals").build()
        }

        val intent = Intent(Intent.ACTION_VIEW, uri)
        item.displayName?.let { intent.putExtra("title", it) }
        item.color?.let { intent.putExtra("color", it) }

        if (packageManager.resolveActivity(intent, 0) != null) {
            startActivity(intent)
            return true
        }

        return false
    }

}


@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterialApi::class)
@Composable
fun AccountOverview(
    account: Account,
    showOnlyPersonal: AccountSettings.ShowOnlyPersonal,
    onSetShowOnlyPersonal: (showOnlyPersonal: Boolean) -> Unit,
    hasCardDav: Boolean,
    canCreateAddressBook: Boolean,
    cardDavProgress: AccountActivity.Progress,
    cardDavRefreshing: Boolean,
    addressBooks: LazyPagingItems<Collection>?,
    hasCalDav: Boolean,
    canCreateCalendar: Boolean,
    calDavProgress: AccountActivity.Progress,
    calDavRefreshing: Boolean,
    calendars: LazyPagingItems<Collection>?,
    subscriptions: LazyPagingItems<Collection>?,
    onUpdateCollectionSync: (collectionId: Long, sync: Boolean) -> Unit = { _, _ -> },
    onChangeForceReadOnly: (collectionId: Long, forceReadOnly: Boolean) -> Unit = { _, _ -> },
    onSubscribe: (Collection) -> Unit = {},
    installIcsx5: Boolean = false,
    onRefreshCollections: () -> Unit = {},
    onSync: () -> Unit = {},
    onAccountSettings: () -> Unit = {},
    onRenameAccount: (newName: String) -> Unit = {},
    onDeleteAccount: () -> Unit = {},
    onNavigateUp: () -> Unit = {}
) {
    val context = LocalContext.current

    val pullRefreshing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullRefreshState(
        pullRefreshing,
        onRefresh = onRefreshCollections
    )

    // tabs calculation
    var nextIdx = -1
    @Suppress("KotlinConstantConditions")
    val idxCardDav: Int? = if (hasCardDav) ++nextIdx else null
    val idxCalDav: Int? = if (hasCalDav) ++nextIdx else null
    val idxWebcal: Int? = if ((subscriptions?.itemCount ?: 0) > 0) ++nextIdx else null
    val nrPages =
        (if (idxCardDav != null) 1 else 0) +
                (if (idxCalDav != null) 1 else 0) +
                (if (idxWebcal != null) 1 else 0)
    val pagerState = rememberPagerState(pageCount = { nrPages })

    // snackbar
    val snackbarHostState = remember { SnackbarHostState() }
    AccountOverview_SnackbarContent(
        snackbarHostState = snackbarHostState,
        currentPageIsCardDav = pagerState.currentPage == idxCardDav,
        cardDavRefreshing = cardDavRefreshing,
        calDavRefreshing = calDavRefreshing
    )

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, stringResource(R.string.navigate_up))
                    }
                },
                title = {
                    Text(
                        account.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                actions = {
                    AccountOverview_Actions(
                        account = account,
                        canCreateAddressBook = canCreateAddressBook,
                        canCreateCalendar = canCreateCalendar,
                        showOnlyPersonal = showOnlyPersonal,
                        onSetShowOnlyPersonal = onSetShowOnlyPersonal,
                        currentPage = pagerState.currentPage,
                        idxCardDav = idxCardDav,
                        idxCalDav = idxCalDav,
                        onRenameAccount = onRenameAccount,
                        onDeleteAccount = onDeleteAccount,
                        onAccountSettings = onAccountSettings
                    )
                }
            )
        },
        floatingActionButton = {
            Column {
                FloatingActionButton(
                    onClick = onRefreshCollections,
                    backgroundColor = MaterialTheme.colors.background,
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    // Material 3: add Tooltip
                    Icon(Icons.Outlined.RuleFolder, stringResource(R.string.account_refresh_collections))
                }

                if (pagerState.currentPage == idxCardDav || pagerState.currentPage == idxCalDav)
                    FloatingActionButton(onClick = onSync) {
                        // Material 3: add Tooltip
                        Icon(Icons.Default.Sync, stringResource(R.string.account_synchronize_now))
                    }
            }
        },
        snackbarHost = {
            SnackbarHost(snackbarHostState)
        },
        modifier = Modifier.pullRefresh(pullRefreshState)
    ) { padding ->
        Column {
            if (nrPages > 0) {
                val scope = rememberCoroutineScope()

                TabRow(
                    selectedTabIndex = pagerState.currentPage,
                    modifier = Modifier.padding(padding)
                ) {
                    if (idxCardDav != null)
                        Tab(
                            selected = pagerState.currentPage == idxCardDav,
                            onClick = {
                                scope.launch {
                                    pagerState.scrollToPage(idxCardDav)
                                }
                            }
                        ) {
                            Text(
                                stringResource(R.string.account_carddav).uppercase(),
                                modifier = Modifier.padding(8.dp)
                            )
                        }

                    if (idxCalDav != null) {
                        Tab(
                            selected = pagerState.currentPage == idxCalDav,
                            onClick = {
                                scope.launch {
                                    pagerState.scrollToPage(idxCalDav)
                                }
                            }
                        ) {
                            Text(
                                stringResource(R.string.account_caldav).uppercase(),
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }

                    if (idxWebcal != null) {
                        Tab(
                            selected = pagerState.currentPage == idxWebcal,
                            onClick = {
                                scope.launch {
                                    pagerState.scrollToPage(idxWebcal)
                                }
                            }
                        ) {
                            Text(
                                stringResource(R.string.account_webcal).uppercase(),
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }

                HorizontalPager(
                    pagerState,
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) { index ->
                    Box {
                        when (index) {
                            idxCardDav ->
                                ServiceTab(
                                    requiredPermissions = listOf(Manifest.permission.WRITE_CONTACTS),
                                    progress = cardDavProgress,
                                    collections = addressBooks,
                                    onUpdateCollectionSync = onUpdateCollectionSync,
                                    onChangeForceReadOnly = onChangeForceReadOnly
                                )

                            idxCalDav -> {
                                val permissions = mutableListOf(Manifest.permission.WRITE_CALENDAR)
                                TaskUtils.currentProvider(context)?.let { tasksProvider ->
                                    permissions += tasksProvider.permissions
                                }
                                ServiceTab(
                                    requiredPermissions = permissions,
                                    progress = calDavProgress,
                                    collections = calendars,
                                    onUpdateCollectionSync = onUpdateCollectionSync,
                                    onChangeForceReadOnly = onChangeForceReadOnly
                                )
                            }

                            idxWebcal -> {
                                Column {
                                    if (installIcsx5)
                                        ActionCard(
                                            icon = Icons.Default.Event,
                                            actionText = stringResource(R.string.account_install_icsx5),
                                            onAction = {
                                                val installIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=at.bitfire.icsdroid"))
                                                if (context.packageManager.resolveActivity(installIntent, 0) != null)
                                                    context.startActivity(installIntent)
                                            },
                                            modifier = Modifier.padding(top = 8.dp)
                                        ) {
                                            Text(stringResource(R.string.account_no_webcal_handler_found))
                                        }
                                    else
                                        Text(
                                            stringResource(R.string.account_webcal_external_app),
                                            style = MaterialTheme.typography.body2,
                                            modifier = Modifier.padding(top = 8.dp, start = 8.dp, end = 8.dp)
                                        )

                                    ServiceTab(
                                        requiredPermissions = listOf(Manifest.permission.WRITE_CALENDAR),
                                        progress = calDavProgress,
                                        collections = subscriptions,
                                        onSubscribe = onSubscribe
                                    )
                                }
                            }
                        }

                        PullRefreshIndicator(
                            refreshing = pullRefreshing,
                            state = pullRefreshState,
                            modifier = Modifier.align(Alignment.TopCenter)
                        )
                    }
                }
            }
        }
    }
}

@Preview
@Composable
fun AccountOverview_CardDAV_CalDAV() {
    AccountOverview(
        account = Account("test@example.com", "test"),
        showOnlyPersonal = AccountSettings.ShowOnlyPersonal(false, true),
        onSetShowOnlyPersonal = {},
        hasCardDav = true,
        canCreateAddressBook = false,
        cardDavProgress = AccountActivity.Progress.Active,
        cardDavRefreshing = false,
        addressBooks = null,
        hasCalDav = true,
        canCreateCalendar = true,
        calDavProgress = AccountActivity.Progress.Pending,
        calDavRefreshing = false,
        calendars = null,
        subscriptions = null
    )
}

@Composable
fun AccountOverview_Actions(
    account: Account,
    canCreateAddressBook: Boolean,
    canCreateCalendar: Boolean,
    showOnlyPersonal: AccountSettings.ShowOnlyPersonal,
    onSetShowOnlyPersonal: (showOnlyPersonal: Boolean) -> Unit,
    currentPage: Int,
    idxCardDav: Int?,
    idxCalDav: Int?,
    onRenameAccount: (newName: String) -> Unit,
    onDeleteAccount: () -> Unit,
    onAccountSettings: () -> Unit
) {
    val context = LocalContext.current

    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var showRenameAccountDialog by remember { mutableStateOf(false) }

    var overflowOpen by remember { mutableStateOf(false) }
    IconButton(onClick = onAccountSettings) {
        Icon(Icons.Default.Settings, stringResource(R.string.account_settings))
    }
    IconButton(onClick = { overflowOpen = !overflowOpen }) {
        Icon(Icons.Default.MoreVert, stringResource(R.string.options_menu))
    }
    DropdownMenu(
        expanded = overflowOpen,
        onDismissRequest = { overflowOpen = false }
    ) {
        // TAB-SPECIFIC ACTIONS

        // create collection
        if (currentPage == idxCardDav && canCreateAddressBook) {
            // create address book
            DropdownMenuItem(onClick = {
                val intent = Intent(context, CreateAddressBookActivity::class.java)
                intent.putExtra(CreateAddressBookActivity.EXTRA_ACCOUNT, account)
                context.startActivity(intent)

                overflowOpen = false
            }) {
                Icon(
                    Icons.Default.CreateNewFolder,
                    contentDescription = stringResource(R.string.create_addressbook),
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(stringResource(R.string.create_addressbook))
            }
        } else if (currentPage == idxCalDav && canCreateCalendar) {
            // create calendar
            DropdownMenuItem(onClick = {
                val intent = Intent(context, CreateCalendarActivity::class.java)
                intent.putExtra(CreateCalendarActivity.EXTRA_ACCOUNT, account)
                context.startActivity(intent)

                overflowOpen = false
            }) {
                Icon(
                    Icons.Default.CreateNewFolder,
                    contentDescription = stringResource(R.string.create_calendar),
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(stringResource(R.string.create_calendar))
            }
        }

        // GENERAL ACTIONS

        // show only personal
        DropdownMenuItem(
            onClick = {
                onSetShowOnlyPersonal(!showOnlyPersonal.onlyPersonal)
                overflowOpen = false
            },
            enabled = !showOnlyPersonal.locked
        ) {
            Text(stringResource(R.string.account_only_personal))
            Checkbox(
                checked = showOnlyPersonal.onlyPersonal,
                enabled = !showOnlyPersonal.locked,
                onCheckedChange = {
                    onSetShowOnlyPersonal(it)
                    overflowOpen = false
                }
            )
        }

        // rename account
        DropdownMenuItem(onClick = {
            showRenameAccountDialog = true
            overflowOpen = false
        }) {
            Icon(
                Icons.Default.DriveFileRenameOutline,
                contentDescription = stringResource(R.string.account_rename),
                modifier = Modifier.padding(end = 8.dp)
            )
            Text(stringResource(R.string.account_rename))
        }

        // delete account
        DropdownMenuItem(onClick = {
            showDeleteAccountDialog = true
            overflowOpen = false
        }) {
            Icon(
                Icons.Default.Delete,
                contentDescription = stringResource(R.string.account_delete),
                modifier = Modifier.padding(end = 8.dp)
            )
            Text(stringResource(R.string.account_delete))
        }
    }

    // modal dialogs
    if (showRenameAccountDialog)
        RenameAccountDialog(
            oldName = account.name,
            onRenameAccount = { newName ->
                onRenameAccount(newName)
                showRenameAccountDialog = false
            },
            onDismiss = { showRenameAccountDialog = false }
        )
    if (showDeleteAccountDialog)
        DeleteAccountDialog(
            onConfirm = onDeleteAccount,
            onDismiss = { showDeleteAccountDialog = false }
        )
}

@Composable
fun AccountOverview_SnackbarContent(
    snackbarHostState: SnackbarHostState,
    currentPageIsCardDav: Boolean,
    cardDavRefreshing: Boolean,
    calDavRefreshing: Boolean
) {
    val context = LocalContext.current

    // show snackbar when refreshing collection list
    val currentTabRefreshing =
        if (currentPageIsCardDav)
            cardDavRefreshing
        else
            calDavRefreshing
    LaunchedEffect(currentTabRefreshing) {
        if (currentTabRefreshing)
            snackbarHostState.showSnackbar(
                context.getString(R.string.account_refreshing_collections),
                duration = SnackbarDuration.Indefinite
            )
    }
}

@Composable
@Preview
fun DeleteAccountDialog(
    onConfirm: () -> Unit = {},
    onDismiss: () -> Unit = {}
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.account_delete_confirmation_title)) },
        text = { Text(stringResource(R.string.account_delete_confirmation_text)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(android.R.string.ok).uppercase())
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel).uppercase())
            }
        }
    )
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ServiceTab(
    requiredPermissions: List<String>,
    progress: AccountActivity.Progress,
    collections: LazyPagingItems<Collection>?,
    onUpdateCollectionSync: (collectionId: Long, sync: Boolean) -> Unit = { _, _ -> },
    onChangeForceReadOnly: (collectionId: Long, forceReadOnly: Boolean) -> Unit = { _, _ -> },
    onSubscribe: (Collection) -> Unit = {},
) {
    val context = LocalContext.current

    Column {
        // progress indicator
        val progressAlpha by animateFloatAsState(
            when (progress) {
                AccountActivity.Progress.Active -> 1f
                AccountActivity.Progress.Pending -> 0.5f
                AccountActivity.Progress.Idle -> 0f
            },
            label = "progressAlpha"
        )
        when (progress) {
            AccountActivity.Progress.Active -> LinearProgressIndicator(
                color = MaterialTheme.colors.secondary,
                modifier = Modifier
                    .graphicsLayer(alpha = progressAlpha)
                    .fillMaxWidth()
            )
            AccountActivity.Progress.Pending -> LinearProgressIndicator(
                color = MaterialTheme.colors.secondary,
                progress = 1f,
                modifier = Modifier
                    .graphicsLayer(alpha = progressAlpha)
                    .fillMaxWidth()
            )
            AccountActivity.Progress.Idle -> Spacer(Modifier.height(ProgressIndicatorDefaults.StrokeWidth))
        }

        // permissions warning
        val permissionsState = rememberMultiplePermissionsState(requiredPermissions)
        if (!permissionsState.allPermissionsGranted)
            ActionCard(
                icon = Icons.Default.SyncProblem,
                actionText = stringResource(R.string.account_manage_permissions),
                onAction = {
                    val intent = Intent(context, PermissionsActivity::class.java)
                    context.startActivity(intent)
                }
            ) {
                Text(stringResource(R.string.account_missing_permissions))
            }

        // collection list
        if (collections != null)
            CollectionsList(
                collections,
                onChangeSync = onUpdateCollectionSync,
                onChangeForceReadOnly = onChangeForceReadOnly,
                onSubscribe = onSubscribe,
                modifier = Modifier.weight(1f)
            )
    }
}