package at.bitfire.davdroid.ui

import android.accounts.Account
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.DataSaverOn
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.SignalCellularOff
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import at.bitfire.davdroid.R
import at.bitfire.davdroid.ui.account.progressAlpha
import at.bitfire.davdroid.ui.composable.ActionCard
import kotlinx.coroutines.launch

@Composable
fun AccountsScreen(
    accountsDrawerHandler: AccountsDrawerHandler,
    onAddAccount: () -> Unit = {},
    onShowAccount: (Account) -> Unit = {},
    onFinish: () -> Unit = {},
    model: AccountsModel = viewModel(),
    warnings: AppWarningsModel = viewModel()
) {
    val accounts by model.accountInfos.collectAsStateWithLifecycle(emptyList())
    val showSyncAll by model.showSyncAll.collectAsStateWithLifecycle(true)
    val showAddAccount by model.showAddAccount.collectAsStateWithLifecycle(AccountsModel.FABStyle.Standard)

    AccountsScreen(
        accountsDrawerHandler = accountsDrawerHandler,
        accounts = accounts,
        showSyncAll = showSyncAll,
        onSyncAll = { model.syncAllAccounts() },
        showAddAccount = showAddAccount,
        onAddAccount = onAddAccount,
        onShowAccount = onShowAccount,
        onFinish = onFinish
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(
    accountsDrawerHandler: AccountsDrawerHandler,
    accounts: List<AccountsModel.AccountInfo>,
    showSyncAll: Boolean = true,
    onSyncAll: () -> Unit = {},
    showAddAccount: AccountsModel.FABStyle = AccountsModel.FABStyle.Standard,
    onAddAccount: () -> Unit = {},
    onShowAccount: (Account) -> Unit = {},
    onFinish: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    BackHandler {
        scope.launch {
            if (drawerState.isOpen)
                drawerState.close()
            else
                onFinish()
        }
    }

    val refreshState = rememberPullToRefreshState(
        enabled = { showSyncAll }
    )
    LaunchedEffect(refreshState.isRefreshing) {
        if (refreshState.isRefreshing) {
            onSyncAll()
            refreshState.endRefresh()
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    AppTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconToggleButton(false, onCheckedChange = { openDrawer ->
                            scope.launch {
                                if (openDrawer)
                                    drawerState.open()
                                else
                                    drawerState.close()
                            }
                        }) {
                            Icon(
                                Icons.Filled.Menu,
                                stringResource(androidx.compose.ui.R.string.navigation_menu)
                            )
                        }
                    },
                    title = {
                        Text(stringResource(R.string.app_name))
                    },
                    actions = {
                        if (showSyncAll)
                            IconButton(onClick = onSyncAll) {
                                Icon(
                                    Icons.Default.Sync,
                                    contentDescription = stringResource(R.string.accounts_sync_all)
                                )
                            }
                    }
                )
            },
            floatingActionButton = {
                if (showAddAccount == AccountsModel.FABStyle.WithText)
                    ExtendedFloatingActionButton(
                        text = { Text(stringResource(R.string.login_create_account)) },
                        icon = { Icon(Icons.Filled.Add, stringResource(R.string.login_create_account)) },
                        onClick = onAddAccount
                    )
                else if (showAddAccount == AccountsModel.FABStyle.Standard)
                    FloatingActionButton(onClick = onAddAccount) {
                        Icon(Icons.Filled.Add, stringResource(R.string.login_create_account))
                    }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { padding ->
            Box(Modifier.padding(padding)) {
                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        ModalDrawerSheet {
                            accountsDrawerHandler.AccountsDrawer(
                                snackbarHostState = snackbarHostState,
                                onCloseDrawer = {
                                    scope.launch {
                                        drawerState.close()
                                    }
                                }
                            )
                        }
                    }
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .nestedScroll(refreshState.nestedScrollConnection)
                            .verticalScroll(rememberScrollState())
                    ) {
                        // background image
                        Image(
                            painterResource(R.drawable.accounts_background),
                            contentDescription = null,
                            modifier = Modifier
                                .matchParentSize()
                                .align(Alignment.Center)
                        )

                        Column {
                            /*val notificationsPermissionState =
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                                    rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS)
                                else
                                    null*/

                            // Warnings show as action cards
                            /*SyncWarnings(
                                notificationsWarning = notificationsPermissionState?.status?.isGranted == false,
                                onClickPermissions = {
                                    startActivity(Intent(this@AccountsActivity, PermissionsActivity::class.java))
                                },
                                internetWarning = warnings.networkAvailable.observeAsState().value == false,
                                onManageConnections = {
                                    val intent = Intent(Settings.ACTION_WIRELESS_SETTINGS)
                                    if (intent.resolveActivity(packageManager) != null)
                                        startActivity(intent)
                                },
                                dataSaverActive = warnings.dataSaverEnabled.collectAsStateWithLifecycle().value,
                                onManageDataSaver = {
                                    val intent = Intent(Settings.ACTION_IGNORE_BACKGROUND_DATA_RESTRICTIONS_SETTINGS, Uri.parse("package:$packageName"))
                                    if (intent.resolveActivity(packageManager) != null)
                                        startActivity(intent)
                                },
                                batterySaverActive = warnings.batterySaverActive.collectAsStateWithLifecycle().value,
                                onManageBatterySaver = {
                                    val intent = Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
                                    if (intent.resolveActivity(packageManager) != null)
                                        startActivity(intent)
                                },
                                lowStorageWarning = warnings.storageLow.collectAsStateWithLifecycle().value,
                                onManageStorage = {
                                    val intent = Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
                                    if (intent.resolveActivity(packageManager) != null)
                                        startActivity(intent)
                                }
                            )*/

                            // account list
                            AccountList(
                                accounts = accounts,
                                onClickAccount = { account ->
                                    onShowAccount(account)
                                },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp)
                            )
                        }

                        // indicate when the user pulls down
                        PullToRefreshContainer(
                            modifier = Modifier.align(Alignment.TopCenter),
                            state = refreshState
                        )
                    }
                }
            }
        }
    }
}

@Composable
@Preview
fun AccountsScreen_Preview_Empty() {
    AccountsScreen(
        accountsDrawerHandler = object: AccountsDrawerHandler() {
            @Composable
            override fun MenuEntries(snackbarHostState: SnackbarHostState) {
                Text("Menu entries")
            }
        },
        accounts = emptyList()
    )
}

@Composable
@Preview
fun AccountsScreen_Preview_OneAccount() {
    AccountsScreen(
        accountsDrawerHandler = object: AccountsDrawerHandler() {
            @Composable
            override fun MenuEntries(snackbarHostState: SnackbarHostState) {
                Text("Menu entries")
            }
        },
        accounts = listOf(
            AccountsModel.AccountInfo(
                Account("Account Name", "test"),
                AccountsModel.Progress.Idle
            )
        )
    )
}

@Composable
fun AccountList(
    accounts: List<AccountsModel.AccountInfo>,
    modifier: Modifier = Modifier,
    onClickAccount: (Account) -> Unit = {}
) {
    Column(modifier) {
        if (accounts.isEmpty())
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.account_list_empty),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        else
            for ((account, progress) in accounts)
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    ),
                    elevation = CardDefaults.cardElevation(1.dp),
                    modifier = Modifier
                        .clickable { onClickAccount(account) }
                        .fillMaxWidth()
                        .padding(8.dp)
                ) {
                    Column {
                        val progressAlpha = progressAlpha(progress)
                        when (progress) {
                            AccountsModel.Progress.Active ->
                                LinearProgressIndicator(
                                    //color = MaterialTheme.colors.onSecondary,
                                    modifier = Modifier
                                        .alpha(progressAlpha)
                                        .fillMaxWidth()
                                )
                            AccountsModel.Progress.Pending,
                            AccountsModel.Progress.Idle ->
                                LinearProgressIndicator(
                                    progress = 1f,
                                    //color = MaterialTheme.colors.onSecondary,
                                    modifier = Modifier
                                        .alpha(progressAlpha)
                                        .fillMaxWidth()
                                )
                        }

                        Column(Modifier.padding(8.dp)) {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = null,
                                modifier = Modifier
                                    .align(Alignment.CenterHorizontally)
                                    .size(48.dp)
                            )

                            Text(
                                text = account.name,
                                style = MaterialTheme.typography.headlineMedium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .padding(top = 8.dp)
                                    .fillMaxWidth()
                            )
                        }
                    }
                }
    }
}

@Composable
@Preview
fun AccountList_Preview_Idle() {
    AccountList(
        listOf(
            AccountsModel.AccountInfo(
                Account("Account Name", "test"),
                AccountsModel.Progress.Idle
            )
        )
    )
}

@Composable
@Preview
fun AccountList_Preview_SyncPending() {
    AccountList(listOf(
        AccountsModel.AccountInfo(
            Account("Account Name", "test"),
            AccountsModel.Progress.Pending
        )
    ))
}

@Composable
@Preview
fun AccountList_Preview_Syncing() {
    AccountList(listOf(
        AccountsModel.AccountInfo(
            Account("Account Name", "test"),
            AccountsModel.Progress.Active
        )
    ))
}


@Composable
fun SyncWarnings(
    notificationsWarning: Boolean,
    onClickPermissions: () -> Unit = {},
    internetWarning: Boolean,
    onManageConnections: () -> Unit = {},
    batterySaverActive: Boolean,
    onManageBatterySaver: () -> Unit = {},
    dataSaverActive: Boolean,
    onManageDataSaver: () -> Unit = {},
    lowStorageWarning: Boolean,
    onManageStorage: () -> Unit = {}
) {
    Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
        if (notificationsWarning)
            ActionCard(
                icon = Icons.Default.NotificationsOff,
                actionText = stringResource(R.string.account_manage_permissions),
                onAction = onClickPermissions
            ) {
                Text(stringResource(R.string.account_list_no_notification_permission))
            }

        if (internetWarning)
            ActionCard(
                icon = Icons.Default.SignalCellularOff,
                actionText = stringResource(R.string.account_list_manage_connections),
                onAction = onManageConnections
            ) {
                Text(stringResource(R.string.account_list_no_internet))
            }

        if (batterySaverActive)
            ActionCard(
                icon = Icons.Default.BatterySaver,
                actionText = stringResource(R.string.account_list_manage_battery_saver),
                onAction = onManageBatterySaver
            ) {
                Text(stringResource(R.string.account_list_battery_saver_enabled))
            }

        if (dataSaverActive)
            ActionCard(
                icon = Icons.Default.DataSaverOn,
                actionText = stringResource(R.string.account_list_manage_datasaver),
                onAction = onManageDataSaver
            ) {
                Text(stringResource(R.string.account_list_datasaver_enabled))
            }

        if (lowStorageWarning)
            ActionCard(
                icon = Icons.Default.Storage,
                actionText = stringResource(R.string.account_list_manage_storage),
                onAction = onManageStorage
            ) {
                Text(stringResource(R.string.account_list_low_storage))
            }
    }
}

@Composable
@Preview
fun SyncWarnings_Preview() {
    SyncWarnings(
        notificationsWarning = true,
        internetWarning = true,
        batterySaverActive = true,
        dataSaverActive = true,
        lowStorageWarning = true
    )
}