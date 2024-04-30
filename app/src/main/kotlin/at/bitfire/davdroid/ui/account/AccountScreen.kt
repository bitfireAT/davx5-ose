
import android.Manifest
import android.accounts.Account
import android.app.Activity
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
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.ui.AppTheme
import at.bitfire.davdroid.ui.PermissionsActivity
import at.bitfire.davdroid.ui.account.AccountActivity
import at.bitfire.davdroid.ui.account.AccountProgress
import at.bitfire.davdroid.ui.account.AccountScreenModel
import at.bitfire.davdroid.ui.account.CollectionsList
import at.bitfire.davdroid.ui.account.RenameAccountDialog
import at.bitfire.davdroid.ui.composable.ActionCard
import at.bitfire.davdroid.util.TaskUtils
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.launch

@Composable
fun AccountScreen(
    account: Account,
    onAccountSettings: () -> Unit,
    onCreateAddressBook: () -> Unit,
    onCreateCalendar: () -> Unit,
    onCollectionDetails: (Collection) -> Unit,
    onNavUp: () -> Unit,
    onFinish: () -> Unit
) {
    val context = LocalContext.current as Activity
    val entryPoint = EntryPointAccessors.fromActivity(context, AccountActivity.AccountScreenEntryPoint::class.java)
    val model = viewModel<AccountScreenModel>(
        factory = AccountScreenModel.factoryFromAccount(entryPoint.accountModelAssistedFactory(), account)
    )

    val addressBooksPager by model.addressBooksPager.collectAsState(null)
    val calendarsPager by model.calendarsPager.collectAsState(null)
    val subscriptionsPager by model.webcalPager.collectAsState(null)

    AccountScreen(
        accountName = account.name,
        error = model.error,
        onResetError = model::resetError,
        invalidAccount = model.invalidAccount.collectAsStateWithLifecycle(false).value,
        showOnlyPersonal = model.showOnlyPersonal.collectAsStateWithLifecycle(
            initialValue = AccountSettings.ShowOnlyPersonal(onlyPersonal = false, locked = false)
        ).value,
        onSetShowOnlyPersonal = model::setShowOnlyPersonal,
        hasCardDav = model.hasCardDav.collectAsStateWithLifecycle(false).value,
        canCreateAddressBook = model.canCreateAddressBook.collectAsStateWithLifecycle(false).value,
        cardDavProgress = model.cardDavProgress.collectAsStateWithLifecycle(AccountProgress.Idle).value,
        addressBooks = addressBooksPager?.flow?.collectAsLazyPagingItems(),
        hasCalDav = model.hasCalDav.collectAsStateWithLifecycle(initialValue = false).value,
        canCreateCalendar = model.canCreateCalendar.collectAsStateWithLifecycle(false).value,
        calDavProgress = model.calDavProgress.collectAsStateWithLifecycle(AccountProgress.Idle).value,
        calendars = calendarsPager?.flow?.collectAsLazyPagingItems(),
        hasWebcal = model.hasWebcal.collectAsStateWithLifecycle(false).value,
        subscriptions =  subscriptionsPager?.flow?.collectAsLazyPagingItems(),
        onUpdateCollectionSync = model::setCollectionSync,
        onSubscribe = { collection ->
            // subscribe
            var uri = Uri.parse(collection.source.toString())
            when {
                uri.scheme.equals("http", true) -> uri = uri.buildUpon().scheme("webcal").build()
                uri.scheme.equals("https", true) -> uri = uri.buildUpon().scheme("webcals").build()
            }

            val intent = Intent(Intent.ACTION_VIEW, uri)
            collection.displayName?.let { intent.putExtra("title", it) }
            collection.color?.let { intent.putExtra("color", it) }

            if (context.packageManager.resolveActivity(intent, 0) != null)
                context.startActivity(intent)
            else
                model.noWebcalApp()
        },
        onCollectionDetails = onCollectionDetails,
        showNoWebcalApp = model.showNoWebcalApp,
        resetShowNoWebcalApp = model::resetShowNoWebcalApp,
        onRefreshCollections = model::refreshCollections,
        onSync = model::sync,
        onAccountSettings = onAccountSettings,
        onCreateAddressBook = onCreateAddressBook,
        onCreateCalendar = onCreateCalendar,
        onRenameAccount = model::renameAccount,
        onDeleteAccount = model::deleteAccount,
        onNavUp = onNavUp,
        onFinish = onFinish
    )
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    accountName: String,
    error: String? = null,
    onResetError: () -> Unit = {},
    invalidAccount: Boolean = false,
    showOnlyPersonal: AccountSettings.ShowOnlyPersonal,
    onSetShowOnlyPersonal: (showOnlyPersonal: Boolean) -> Unit = {},
    hasCardDav: Boolean,
    canCreateAddressBook: Boolean,
    cardDavProgress: AccountProgress,
    addressBooks: LazyPagingItems<Collection>?,
    hasCalDav: Boolean,
    canCreateCalendar: Boolean,
    calDavProgress: AccountProgress,
    calendars: LazyPagingItems<Collection>?,
    hasWebcal: Boolean,
    subscriptions: LazyPagingItems<Collection>?,
    onUpdateCollectionSync: (collectionId: Long, sync: Boolean) -> Unit = { _, _ -> },
    onSubscribe: (Collection) -> Unit = {},
    onCollectionDetails: (Collection) -> Unit = {},
    showNoWebcalApp: Boolean = false,
    resetShowNoWebcalApp: () -> Unit = {},
    onRefreshCollections: () -> Unit = {},
    onSync: () -> Unit = {},
    onAccountSettings: () -> Unit = {},
    onCreateAddressBook: () -> Unit = {},
    onCreateCalendar: () -> Unit = {},
    onRenameAccount: (newName: String) -> Unit = {},
    onDeleteAccount: () -> Unit = {},
    onNavUp: () -> Unit = {},
    onFinish: () -> Unit = {}
) {
    AppTheme {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()

        if (invalidAccount)
            onFinish()

        val pullRefreshState = rememberPullToRefreshState()
        LaunchedEffect(pullRefreshState.isRefreshing) {
            if (pullRefreshState.isRefreshing) {
                onSync()
                pullRefreshState.endRefresh()
            }
        }

        val snackbarHostState = remember { SnackbarHostState() }
        LaunchedEffect(error) {
            if (error != null)
                scope.launch {
                    snackbarHostState.showSnackbar(error)
                    onResetError()
                }
        }

        // tabs calculation
        var nextIdx = -1

        @Suppress("KotlinConstantConditions")
        val idxCardDav: Int? = if (hasCardDav) ++nextIdx else null
        val idxCalDav: Int? = if (hasCalDav) ++nextIdx else null
        val idxWebcal: Int? = if (hasWebcal) ++nextIdx else null
        val nrPages =
            (if (idxCardDav != null) 1 else 0) +
                    (if (idxCalDav != null) 1 else 0) +
                    (if (idxWebcal != null) 1 else 0)
        val pagerState = rememberPagerState(pageCount = { nrPages })

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
                            text = accountName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    actions = {
                        AccountScreen_Actions(
                            accountName = accountName,
                            canCreateAddressBook = canCreateAddressBook,
                            onCreateAddressBook = onCreateAddressBook,
                            canCreateCalendar = canCreateCalendar,
                            onCreateCalendar = onCreateCalendar,
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
                Column(horizontalAlignment = Alignment.End) {
                    ExtendedFloatingActionButton(
                        text = {
                            Text(stringResource(R.string.account_refresh_collections))
                        },
                        icon = {
                            Icon(Icons.Outlined.RuleFolder, stringResource(R.string.account_refresh_collections))
                        },
                        onClick = onRefreshCollections,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    if (pagerState.currentPage == idxCardDav || pagerState.currentPage == idxCalDav)
                        ExtendedFloatingActionButton(
                            text = {
                                Text(stringResource(R.string.account_synchronize_now))
                            },
                            icon = {
                                Icon(Icons.Default.Sync, stringResource(R.string.account_synchronize_now))
                            },
                            onClick = onSync
                        )
                }
            },
            snackbarHost = {
                SnackbarHost(snackbarHostState)
            }
        ) { padding ->
            Box(
                Modifier
                    .padding(padding)
                    .nestedScroll(pullRefreshState.nestedScrollConnection)
            ) {
                Column {
                    if (nrPages > 0) {
                        TabRow(selectedTabIndex = pagerState.currentPage) {
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
                                        stringResource(R.string.account_carddav),
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
                                        stringResource(R.string.account_caldav),
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
                                        stringResource(R.string.account_webcal),
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
                            when (index) {
                                idxCardDav ->
                                    AccountScreen_ServiceTab(
                                        requiredPermissions = listOf(Manifest.permission.WRITE_CONTACTS),
                                        progress = cardDavProgress,
                                        collections = addressBooks,
                                        onUpdateCollectionSync = onUpdateCollectionSync,
                                        onCollectionDetails = onCollectionDetails
                                    )

                                idxCalDav -> {
                                    val permissions = mutableListOf(Manifest.permission.WRITE_CALENDAR)
                                    TaskUtils.currentProvider(context)?.let { tasksProvider ->
                                        permissions += tasksProvider.permissions
                                    }
                                    AccountScreen_ServiceTab(
                                        requiredPermissions = permissions,
                                        progress = calDavProgress,
                                        collections = calendars,
                                        onUpdateCollectionSync = onUpdateCollectionSync,
                                        onCollectionDetails = onCollectionDetails
                                    )
                                }

                                idxWebcal -> {
                                    LaunchedEffect(showNoWebcalApp) {
                                        if (showNoWebcalApp) {
                                            if (snackbarHostState.showSnackbar(
                                                    message = context.getString(R.string.account_no_webcal_handler_found),
                                                    actionLabel = context.getString(R.string.account_install_icsx5),
                                                    duration = SnackbarDuration.Long
                                                ) == SnackbarResult.ActionPerformed
                                            ) {
                                                val installIntent = Intent(
                                                    Intent.ACTION_VIEW,
                                                    Uri.parse("market://details?id=at.bitfire.icsdroid")
                                                )
                                                if (context.packageManager.resolveActivity(installIntent, 0) != null)
                                                    context.startActivity(installIntent)
                                            }
                                            resetShowNoWebcalApp()
                                        }
                                    }

                                    Column {
                                        Text(
                                            stringResource(R.string.account_webcal_external_app),
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.padding(top = 8.dp, start = 8.dp, end = 8.dp)
                                        )

                                        AccountScreen_ServiceTab(
                                            requiredPermissions = listOf(Manifest.permission.WRITE_CALENDAR),
                                            progress = calDavProgress,
                                            collections = subscriptions,
                                            onSubscribe = onSubscribe
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                PullToRefreshContainer(
                    state = pullRefreshState,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
        }
    }
}

@Composable
fun AccountScreen_Actions(
    accountName: String,
    canCreateAddressBook: Boolean,
    onCreateAddressBook: () -> Unit,
    canCreateCalendar: Boolean,
    onCreateCalendar: () -> Unit,
    showOnlyPersonal: AccountSettings.ShowOnlyPersonal,
    onSetShowOnlyPersonal: (showOnlyPersonal: Boolean) -> Unit,
    currentPage: Int,
    idxCardDav: Int?,
    idxCalDav: Int?,
    onRenameAccount: (newName: String) -> Unit,
    onDeleteAccount: () -> Unit,
    onAccountSettings: () -> Unit
) {
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
                    onCreateAddressBook()
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
                    onCreateCalendar()
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
            oldName = accountName,
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

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun AccountScreen_ServiceTab(
    requiredPermissions: List<String>,
    progress: AccountProgress,
    collections: LazyPagingItems<Collection>?,
    onUpdateCollectionSync: (collectionId: Long, sync: Boolean) -> Unit = { _, _ -> },
    onSubscribe: (Collection) -> Unit = {},
    onCollectionDetails: ((Collection) -> Unit)? = null
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
        if (!LocalInspectionMode.current) {
            val permissionsState = rememberMultiplePermissionsState(requiredPermissions)
            if (!permissionsState.allPermissionsGranted)
                ActionCard(
                    icon = Icons.Default.SyncProblem,
                    actionText = stringResource(R.string.account_manage_permissions),
                    onAction = {
                        val intent = Intent(context, PermissionsActivity::class.java)
                        context.startActivity(intent)
                    },
                    modifier = Modifier.padding(8.dp)
                ) {
                    Text(stringResource(R.string.account_missing_permissions))
                }

            // collection list
            if (collections != null)
                CollectionsList(
                    collections,
                    onChangeSync = onUpdateCollectionSync,
                    onSubscribe = onSubscribe,
                    onCollectionDetails = onCollectionDetails,
                    modifier = Modifier.weight(1f)
                )
        }
    }
}

@Preview
@Composable
fun AccountScreen_Preview() {
    AccountScreen(
        accountName = "test@example.com",
        showOnlyPersonal = AccountSettings.ShowOnlyPersonal(onlyPersonal = false, locked = true),
        hasCardDav = true,
        canCreateAddressBook = false,
        cardDavProgress = AccountProgress.Active,
        addressBooks = null,
        hasCalDav = true,
        canCreateCalendar = true,
        calDavProgress = AccountProgress.Pending,
        calendars = null,
        hasWebcal = true,
        subscriptions = null
    )
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
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}