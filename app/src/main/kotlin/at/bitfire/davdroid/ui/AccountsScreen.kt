/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui

import android.Manifest
import android.accounts.Account
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import at.bitfire.davdroid.BuildConfig
import at.bitfire.davdroid.R
import at.bitfire.davdroid.ui.account.AccountProgress
import at.bitfire.davdroid.ui.composable.ActionCard
import at.bitfire.davdroid.ui.composable.ProgressBar
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.delay
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

    // Remember shown state, so the intro does not restart on rotation or theme-change
    var shown by rememberSaveable { mutableStateOf(false) }
    val showAppIntro by model.showAppIntro.collectAsState(false)
    LaunchedEffect(showAppIntro) {
        if (showAppIntro && !shown) {
            shown = true
            onShowAppIntro()
        }
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
        storageLow = model.storageLow.collectAsStateWithLifecycle(false).value,
        calendarStorageDisabled = model.calendarStorageDisabled.collectAsStateWithLifecycle(false).value,
        contactsStorageDisabled = model.contactsStorageDisabled.collectAsStateWithLifecycle(false).value
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
    storageLow: Boolean = false,
    calendarStorageDisabled: Boolean = false,
    contactsStorageDisabled: Boolean = false
) {
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    var isRefreshing by remember { mutableStateOf(false) }
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            delay(300)
            isRefreshing = false
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    AppTheme {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(drawerState) {
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
                        if (showAddAccount == AccountsModel.FABStyle.WithText)
                            ExtendedFloatingActionButton(
                                text = { Text(stringResource(R.string.login_add_account)) },
                                icon = { Icon(Icons.Filled.Add, stringResource(R.string.login_add_account)) },
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                onClick = onAddAccount
                            )
                        else if (showAddAccount == AccountsModel.FABStyle.Standard)
                            FloatingActionButton(
                                onClick = onAddAccount,
                                containerColor = MaterialTheme.colorScheme.secondary,
                                contentColor = MaterialTheme.colorScheme.onSecondary
                            ) {
                                Icon(Icons.Filled.Add, stringResource(R.string.login_add_account))
                            }

                        if (showSyncAll)
                            FloatingActionButton(
                                onClick = onSyncAll,
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(top = 24.dp)
                            ) {
                                Icon(
                                    Icons.Default.Sync,
                                    contentDescription = stringResource(R.string.accounts_sync_all)
                                )
                            }
                    }
                },
                snackbarHost = { SnackbarHost(snackbarHostState) }
            ) { padding ->
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = { isRefreshing = true; onSyncAll() },
                    modifier = Modifier.padding(padding)
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
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
                                },
                                calendarStorageDisabled = calendarStorageDisabled,
                                contactsStorageDisabled = contactsStorageDisabled,
                                onManageApps = {
                                    val intent = Intent(Settings.ACTION_APPLICATION_SETTINGS)
                                    if (intent.resolveActivity(context.packageManager) != null)
                                        context.startActivity(intent)
                                },
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
        accounts = emptyList(),
        showAddAccount = AccountsModel.FABStyle.WithText,
        showSyncAll = false
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.account_list_welcome),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 32.dp)
                )
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
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
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
                                ProgressBar(
                                    modifier = Modifier
                                        .alpha(progressAlpha)
                                        .fillMaxWidth()
                                )
                            AccountProgress.Pending,
                            AccountProgress.Idle ->
                                ProgressBar(
                                    progress = { 1f },
                                    modifier = Modifier
                                        .alpha(progressAlpha)
                                        .fillMaxWidth()
                                )
                        }

                        Column(Modifier.padding(vertical = 12.dp)) {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = null,
                                modifier = Modifier
                                    .align(Alignment.CenterHorizontally)
                                    .size(48.dp)
                            )

                            Text(
                                text = account.name,
                                style = MaterialTheme.typography.titleLarge,
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
    AppTheme {
        AccountList(
            listOf(
                AccountsModel.AccountInfo(
                    Account("Account Name", "test"),
                    AccountProgress.Idle
                )
            )
        )
    }
}

@Composable
@Preview
fun AccountList_Preview_SyncPending() {
    AppTheme {
        AccountList(listOf(
            AccountsModel.AccountInfo(
                Account("Account Name", "test"),
                AccountProgress.Pending
            )
        ))
    }
}

@Composable
@Preview
fun AccountList_Preview_Syncing() {
    AppTheme {
        AccountList(listOf(
            AccountsModel.AccountInfo(
                Account("Account Name", "test"),
                AccountProgress.Active
            )
        ))
    }
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
    onManageStorage: () -> Unit = {},
    calendarStorageDisabled: Boolean = false,
    contactsStorageDisabled: Boolean = false,
    onManageApps: () -> Unit = {}
) {
    Column(Modifier.padding(horizontal = 8.dp)) {
        if (notificationsWarning)
            ActionCard(
                icon = Icons.Default.NotificationsOff,
                actionText = stringResource(R.string.account_manage_permissions),
                onAction = onManagePermissions,
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Text(stringResource(R.string.sync_warning_no_notification_permission))
            }

        if (internetWarning)
            ActionCard(
                icon = Icons.Default.SignalCellularOff,
                actionText = stringResource(R.string.sync_warning_manage_connections),
                onAction = onManageConnections,
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Text(stringResource(R.string.sync_warning_no_internet))
            }

        if (batterySaverActive)
            ActionCard(
                icon = Icons.Default.BatterySaver,
                actionText = stringResource(R.string.sync_warning_manage_battery_saver),
                onAction = onManageBatterySaver,
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Text(stringResource(R.string.sync_warning_battery_saver_enabled))
            }

        if (dataSaverActive)
            ActionCard(
                icon = Icons.Default.DataSaverOn,
                actionText = stringResource(R.string.sync_warning_manage_datasaver),
                onAction = onManageDataSaver,
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Text(stringResource(R.string.sync_warning_datasaver_enabled))
            }

        if (lowStorageWarning)
            ActionCard(
                icon = Icons.Default.Storage,
                actionText = stringResource(R.string.sync_warning_manage_storage),
                onAction = onManageStorage,
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Text(stringResource(R.string.sync_warning_low_storage))
            }

        if (calendarStorageDisabled)
            ActionCard(
                icon = ImageVector.vectorResource(R.drawable.ic_database_off),
                actionText = stringResource(R.string.sync_warning_manage_apps),
                onAction = onManageApps,
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.sync_warning_calendar_storage_disabled_title),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                    Text(stringResource(R.string.sync_warning_calendar_storage_disabled_description))
                }
            }

        if (contactsStorageDisabled)
            ActionCard(
                icon = ImageVector.vectorResource(R.drawable.ic_database_off),
                actionText = stringResource(R.string.sync_warning_manage_apps),
                onAction = onManageApps,
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.sync_warning_contacts_storage_disabled_title),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                    Text(stringResource(R.string.sync_warning_contacts_storage_disabled_description))
                }
            }
    }
}

@Composable
@Preview
fun SyncWarnings_Preview() {
    AppTheme {
        SyncWarnings(
            notificationsWarning = true,
            internetWarning = true,
            batterySaverActive = true,
            dataSaverActive = true,
            lowStorageWarning = true,
            calendarStorageDisabled = true,
            contactsStorageDisabled = true
        )
    }
}