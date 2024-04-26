
import android.Manifest
import android.accounts.Account
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.ui.AppTheme
import at.bitfire.davdroid.ui.PermissionsActivity
import at.bitfire.davdroid.ui.account.AccountProgress
import at.bitfire.davdroid.ui.account.CollectionsList
import at.bitfire.davdroid.ui.account.CreateAddressBookActivity
import at.bitfire.davdroid.ui.account.CreateCalendarActivity
import at.bitfire.davdroid.ui.account.RenameAccountDialog
import at.bitfire.davdroid.ui.composable.ActionCard
import at.bitfire.davdroid.util.TaskUtils
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AccountOverview(
    account: Account,
    showOnlyPersonal: AccountSettings.ShowOnlyPersonal,
    onSetShowOnlyPersonal: (showOnlyPersonal: Boolean) -> Unit = {},
    hasCardDav: Boolean,
    canCreateAddressBook: Boolean,
    cardDavProgress: AccountProgress,
    cardDavRefreshing: Boolean,
    addressBooks: LazyPagingItems<Collection>?,
    hasCalDav: Boolean,
    canCreateCalendar: Boolean,
    calDavProgress: AccountProgress,
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
    onNavUp: () -> Unit = {}
) {
    AppTheme {
        val context = LocalContext.current

        val pullRefreshState = rememberPullToRefreshState()

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
                        IconButton(onClick = onNavUp) {
                            Icon(Icons.AutoMirrored.Default.ArrowBack, stringResource(R.string.navigate_up))
                        }
                    },
                    title = {
                        Text(
                            text = account.name,
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
            //modifier = Modifier.pullRefresh(pullRefreshState)
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
                                                    val installIntent = Intent(
                                                        Intent.ACTION_VIEW,
                                                        Uri.parse("market://details?id=at.bitfire.icsdroid")
                                                    )
                                                    if (context.packageManager.resolveActivity(
                                                            installIntent,
                                                            0
                                                        ) != null
                                                    )
                                                        context.startActivity(installIntent)
                                                },
                                                modifier = Modifier.padding(top = 8.dp)
                                            ) {
                                                Text(stringResource(R.string.account_no_webcal_handler_found))
                                            }
                                        else
                                            Text(
                                                stringResource(R.string.account_webcal_external_app),
                                                style = MaterialTheme.typography.bodyMedium,
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

                            if (pullRefreshState.isRefreshing)
                                PullToRefreshContainer(
                                    state = pullRefreshState,
                                    modifier = Modifier.align(Alignment.TopCenter)
                                )
                        }
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
        hasCardDav = true,
        canCreateAddressBook = false,
        cardDavProgress = AccountProgress.Active,
        cardDavRefreshing = false,
        addressBooks = null,
        hasCalDav = true,
        canCreateCalendar = true,
        calDavProgress = AccountProgress.Pending,
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
            DropdownMenuItem(
                leadingIcon = {
                    Icon(
                        Icons.Default.CreateNewFolder,
                        contentDescription = stringResource(R.string.create_addressbook),
                        modifier = Modifier.padding(end = 8.dp)
                    )
                },
                text = {
                    Text(stringResource(R.string.create_addressbook))
                },
                onClick = {
                    val intent = Intent(context, CreateAddressBookActivity::class.java)
                    intent.putExtra(CreateAddressBookActivity.EXTRA_ACCOUNT, account)
                    context.startActivity(intent)

                    overflowOpen = false
                }
            )
        } else if (currentPage == idxCalDav && canCreateCalendar) {
            // create calendar
            DropdownMenuItem(
                leadingIcon = {
                    Icon(
                        Icons.Default.CreateNewFolder,
                        contentDescription = stringResource(R.string.create_calendar),
                        modifier = Modifier.padding(end = 8.dp)
                    )
                },
                text = {
                    Text(stringResource(R.string.create_calendar))
                },
                onClick = {
                    val intent = Intent(context, CreateCalendarActivity::class.java)
                    intent.putExtra(CreateCalendarActivity.EXTRA_ACCOUNT, account)
                    context.startActivity(intent)

                    overflowOpen = false
                }
            )
        }

        // GENERAL ACTIONS

        // show only personal
        DropdownMenuItem(
            leadingIcon = {
                Checkbox(
                    checked = showOnlyPersonal.onlyPersonal,
                    enabled = !showOnlyPersonal.locked,
                    onCheckedChange = {
                        onSetShowOnlyPersonal(it)
                        overflowOpen = false
                    }
                )
            },
            text = {
                Text(stringResource(R.string.account_only_personal))
            },
            onClick = {
                onSetShowOnlyPersonal(!showOnlyPersonal.onlyPersonal)
                overflowOpen = false
            },
            enabled = !showOnlyPersonal.locked
        )

        // rename account
        DropdownMenuItem(
            leadingIcon = {
                Icon(
                    Icons.Default.DriveFileRenameOutline,
                    contentDescription = stringResource(R.string.account_rename),
                    modifier = Modifier.padding(end = 8.dp)
                )
            },
            text = {
                Text(stringResource(R.string.account_rename))
            },
            onClick = {
                showRenameAccountDialog = true
                overflowOpen = false
            }
        )

        // delete account
        DropdownMenuItem(
            leadingIcon = {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.account_delete),
                    modifier = Modifier.padding(end = 8.dp)
                )
            },
            text = {
                Text(stringResource(R.string.account_delete))
            },
            onClick = {
                showDeleteAccountDialog = true
                overflowOpen = false
            }
        )
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
    progress: AccountProgress,
    collections: LazyPagingItems<Collection>?,
    onUpdateCollectionSync: (collectionId: Long, sync: Boolean) -> Unit = { _, _ -> },
    onChangeForceReadOnly: (collectionId: Long, forceReadOnly: Boolean) -> Unit = { _, _ -> },
    onSubscribe: (Collection) -> Unit = {},
) {
    val context = LocalContext.current

    Column {
        // progress indicator
        val progressAlpha = progress.rememberAlpha()
        when (progress) {
            AccountProgress.Active -> LinearProgressIndicator(
                modifier = Modifier
                    .alpha(progressAlpha)
                    .fillMaxWidth()
            )
            AccountProgress.Pending,
            AccountProgress.Idle -> LinearProgressIndicator(
                progress = { 1f },
                modifier = Modifier
                    .alpha(progressAlpha)
                    .fillMaxWidth()
            )
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