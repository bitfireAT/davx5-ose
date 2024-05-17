package at.bitfire.davdroid.ui

import android.Manifest
import android.accounts.Account
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import at.bitfire.davdroid.BuildConfig
import at.bitfire.davdroid.R
import at.bitfire.davdroid.ui.account.AccountProgress
import at.bitfire.davdroid.ui.composable.ActionCard
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.launch

@Composable
fun AccountsScreen(
    initialSyncAccounts: Boolean,
    onShowAppIntro: () -> Unit,
    accountsDrawerHandler: AccountsDrawerHandler,
    onAddAccount: () -> Unit,
    onShowAccount: (Account) -> Unit,
    onManagePermissions: () -> Unit,
    model: AccountsModel = hiltViewModel(
        creationCallback = { factory: AccountsModel.Factory ->
            factory.create(initialSyncAccounts)
        }
    )
) {
    val accounts by model.accountInfos.collectAsStateWithLifecycle(emptyList())
    val showSyncAll by model.showSyncAll.collectAsStateWithLifecycle(true)
    val showAddAccount by model.showAddAccount.collectAsStateWithLifecycle(AccountsModel.FABStyle.Standard)

    val showAppIntro by model.showAppIntro.collectAsState(false)
    LaunchedEffect(showAppIntro) {
        if (showAppIntro)
            onShowAppIntro()
    }

    AccountsScreen(
        accountsDrawerHandler = accountsDrawerHandler,
        accounts = accounts,
        showSyncAll = showSyncAll,
        onSyncAll = { model.syncAllAccounts() },
        showAddAccount = showAddAccount,
        onAddAccount = onAddAccount,
        onShowAccount = onShowAccount,
        onManagePermissions = onManagePermissions,
        internetUnavailable = !model.networkAvailable.collectAsStateWithLifecycle(false).value,
        batterySaverActive = model.batterySaverActive.collectAsStateWithLifecycle(false).value,
        dataSaverActive = model.dataSaverEnabled.collectAsStateWithLifecycle(false).value,
        storageLow = model.storageLow.collectAsStateWithLifecycle(false).value
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun AccountsScreen(
    accountsDrawerHandler: AccountsDrawerHandler,
    accounts: List<AccountsModel.AccountInfo>,
    showSyncAll: Boolean = true,
    onSyncAll: () -> Unit = {},
    showAddAccount: AccountsModel.FABStyle = AccountsModel.FABStyle.Standard,
    onAddAccount: () -> Unit = {},
    onShowAccount: (Account) -> Unit = {},
    onManagePermissions: () -> Unit = {},
    internetUnavailable: Boolean = false,
    batterySaverActive: Boolean = false,
    dataSaverActive: Boolean = false,
    storageLow: Boolean = false
) {
    val scope = rememberCoroutineScope()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    BackHandler(drawerState.isOpen) {
        scope.launch {
            drawerState.close()
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
                        }
                    )
                },
                floatingActionButton = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (showSyncAll)
                            FloatingActionButton(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                onClick = onSyncAll
                            ) {
                                Icon(
                                    Icons.Default.Sync,
                                    contentDescription = stringResource(R.string.accounts_sync_all)
                                )
                            }

                        if (showAddAccount == AccountsModel.FABStyle.WithText)
                            ExtendedFloatingActionButton(
                                text = { Text(stringResource(R.string.login_create_account)) },
                                icon = { Icon(Icons.Filled.Add, stringResource(R.string.login_create_account)) },
                                onClick = onAddAccount
                            )
                        else if (showAddAccount == AccountsModel.FABStyle.Standard)
                            FloatingActionButton(
                                onClick = onAddAccount,
                                modifier = Modifier.padding(top = 24.dp)
                            ) {
                                Icon(Icons.Filled.Add, stringResource(R.string.login_create_account))
                            }
                    }
                },
                snackbarHost = { SnackbarHost(snackbarHostState) }
            ) { padding ->
                Box(Modifier.padding(padding)) {
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
                            val notificationsPermissionState =
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !LocalInspectionMode.current)
                                    rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS)
                                else
                                    null

                            // Warnings show as action cards
                            val context = LocalContext.current
                            SyncWarnings(
                                notificationsWarning = notificationsPermissionState?.status?.isGranted == false,
                                onManagePermissions = onManagePermissions,
                                internetWarning = internetUnavailable,
                                onManageConnections = {
                                    val intent = Intent(Settings.ACTION_WIRELESS_SETTINGS)
                                    if (intent.resolveActivity(context.packageManager) != null)
                                        context.startActivity(intent)
                                },
                                batterySaverActive = batterySaverActive,
                                onManageBatterySaver = {
                                    val intent = Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
                                    if (intent.resolveActivity(context.packageManager) != null)
                                        context.startActivity(intent)
                                },
                                dataSaverActive = dataSaverActive,
                                onManageDataSaver = {
                                    val intent = Intent(
                                        /* action = */ Settings.ACTION_IGNORE_BACKGROUND_DATA_RESTRICTIONS_SETTINGS,
                                        /* uri = */ Uri.parse("package:${BuildConfig.APPLICATION_ID}")
                                    )
                                    if (intent.resolveActivity(context.packageManager) != null)
                                        context.startActivity(intent)
                                },
                                lowStorageWarning = storageLow,
                                onManageStorage = {
                                    val intent = Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
                                    if (intent.resolveActivity(context.packageManager) != null)
                                        context.startActivity(intent)
                                }
                            )

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
                AccountProgress.Idle
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
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary
                    ),
                    elevation = CardDefaults.cardElevation(1.dp),
                    modifier = Modifier
                        .clickable { onClickAccount(account) }
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Column {
                        val progressAlpha = progress.rememberAlpha()
                        when (progress) {
                            AccountProgress.Active ->
                                LinearProgressIndicator(
                                    modifier = Modifier
                                        .alpha(progressAlpha)
                                        .fillMaxWidth()
                                )
                            AccountProgress.Pending,
                            AccountProgress.Idle ->
                                LinearProgressIndicator(
                                    progress = 1f,
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
                                    .size(32.dp)
                            )

                            Text(
                                text = account.name,
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .padding(top = 4.dp)
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
                AccountProgress.Idle
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
            AccountProgress.Pending
        )
    ))
}

@Composable
@Preview
fun AccountList_Preview_Syncing() {
    AccountList(listOf(
        AccountsModel.AccountInfo(
            Account("Account Name", "test"),
            AccountProgress.Active
        )
    ))
}


@Composable
fun SyncWarnings(
    notificationsWarning: Boolean = true,
    onManagePermissions: () -> Unit = {},
    internetWarning: Boolean = true,
    onManageConnections: () -> Unit = {},
    batterySaverActive: Boolean = true,
    onManageBatterySaver: () -> Unit = {},
    dataSaverActive: Boolean = true,
    onManageDataSaver: () -> Unit = {},
    lowStorageWarning: Boolean = true,
    onManageStorage: () -> Unit = {}
) {
    Column(Modifier.padding(horizontal = 8.dp)) {
        if (notificationsWarning)
            ActionCard(
                icon = Icons.Default.NotificationsOff,
                actionText = stringResource(R.string.account_manage_permissions),
                onAction = onManagePermissions,
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Text(stringResource(R.string.account_list_no_notification_permission))
            }

        if (internetWarning)
            ActionCard(
                icon = Icons.Default.SignalCellularOff,
                actionText = stringResource(R.string.account_list_manage_connections),
                onAction = onManageConnections,
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Text(stringResource(R.string.account_list_no_internet))
            }

        if (batterySaverActive)
            ActionCard(
                icon = Icons.Default.BatterySaver,
                actionText = stringResource(R.string.account_list_manage_battery_saver),
                onAction = onManageBatterySaver,
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Text(stringResource(R.string.account_list_battery_saver_enabled))
            }

        if (dataSaverActive)
            ActionCard(
                icon = Icons.Default.DataSaverOn,
                actionText = stringResource(R.string.account_list_manage_datasaver),
                onAction = onManageDataSaver,
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Text(stringResource(R.string.account_list_datasaver_enabled))
            }

        if (lowStorageWarning)
            ActionCard(
                icon = Icons.Default.Storage,
                actionText = stringResource(R.string.account_list_manage_storage),
                onAction = onManageStorage,
                modifier = Modifier.padding(vertical = 4.dp)
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