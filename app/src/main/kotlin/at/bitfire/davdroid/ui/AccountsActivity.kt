/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui

import android.Manifest
import android.accounts.Account
import android.accounts.AccountManager
import android.accounts.OnAccountsUpdateListener
import android.app.Application
import android.content.Intent
import android.content.pm.ShortcutManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Card
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.IconToggleButton
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ProgressIndicatorDefaults
import androidx.compose.material.Scaffold
import androidx.compose.material.ScaffoldState
import androidx.compose.material.SnackbarHost
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.getSystemService
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.servicedetection.RefreshCollectionsWorker
import at.bitfire.davdroid.syncadapter.SyncUtils
import at.bitfire.davdroid.syncadapter.SyncWorker
import at.bitfire.davdroid.ui.account.AccountActivity
import at.bitfire.davdroid.ui.intro.IntroActivity
import at.bitfire.davdroid.ui.setup.LoginActivity
import at.bitfire.davdroid.ui.widget.ActionCard
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.themeadapter.material.MdcTheme
import com.google.android.material.navigation.NavigationView
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.Collator
import javax.inject.Inject


@AndroidEntryPoint
class AccountsActivity: AppCompatActivity() {

    @Inject lateinit var accountsDrawerHandler: AccountsDrawerHandler

    private val model by viewModels<Model>()

    private val introActivityLauncher = registerForActivityResult(IntroActivity.Contract) { cancelled ->
        if (cancelled)
            finish()
    }


    @OptIn(ExperimentalMaterialApi::class, ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            // use a separate thread to check whether IntroActivity should be shown
            CoroutineScope(Dispatchers.Default).launch {
                if (IntroActivity.shouldShowIntroActivity(this@AccountsActivity))
                    introActivityLauncher.launch(null)
            }
        }

        setContent {
            val scope = rememberCoroutineScope()
            val scaffoldState = rememberScaffoldState()
            val snackbarHostState = remember { SnackbarHostState() }

            val refreshing by remember { mutableStateOf(false) }
            val pullRefreshState = rememberPullRefreshState(refreshing, onRefresh = {
                model.syncAllAccounts()
            })

            MdcTheme {
                Scaffold(
                    scaffoldState = scaffoldState,
                    drawerContent = drawerContent(scope, scaffoldState),
                    topBar = topBar(scope, scaffoldState),
                    floatingActionButton = floatingActionButton(),
                    snackbarHost = snackbarHost(snackbarHostState, scope)
                ) { padding ->
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .pullRefresh(pullRefreshState)
                            .verticalScroll(rememberScrollState())
                    ) {

                        // background image
                        Image(
                            painterResource(R.drawable.accounts_background),
                            contentDescription = null,
                            modifier = Modifier.fillMaxWidth().align(Alignment.Center)
                        )

                        Column {
                            val warnings = model.warnings

                            val notificationsPermissionState = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                rememberPermissionState(
                                    permission = Manifest.permission.POST_NOTIFICATIONS
                                )
                            } else {
                                null
                            }

                            // Warnings show as action cards
                            SyncWarnings(
                                notificationsWarning = notificationsPermissionState?.status?.isGranted == false,
                                onClickPermissions = {
                                    startActivity(Intent(this@AccountsActivity, PermissionsActivity::class.java))
                                },
                                internetWarning = warnings.networkAvailable.observeAsState().value == false,
                                onManageConnections = {
                                    val intent = Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS)
                                    if (intent.resolveActivity(packageManager) != null)
                                        startActivity(intent)
                                },
                                lowStorageWarning = warnings.storageLow.observeAsState().value == true,
                                onManageStorage = {
                                    val intent = Intent(android.provider.Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
                                    if (intent.resolveActivity(packageManager) != null)
                                        startActivity(intent)
                                },
                                dataSaverActive = warnings.dataSaverEnabled.observeAsState().value == true,
                                onManageDataSaver = {
                                    val intent = Intent(android.provider.Settings.ACTION_IGNORE_BACKGROUND_DATA_RESTRICTIONS_SETTINGS, Uri.parse("package:" + packageName))
                                    if (intent.resolveActivity(packageManager) != null)
                                        startActivity(intent)
                                }
                            )

                            // account list
                            val accounts = model.accountInfos.observeAsState()
                            AccountList(
                                accounts = accounts.value ?: emptyList(),
                                onClickAccount = { account ->
                                    val activity = this@AccountsActivity
                                    val intent = Intent(activity, AccountActivity::class.java)
                                    intent.putExtra(AccountActivity.EXTRA_ACCOUNT, account)
                                    activity.startActivity(intent)
                                },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp)
                            )
                        }

                        // indicate when the user pulls down
                        PullRefreshIndicator(refreshing, pullRefreshState,
                            modifier = Modifier.align(Alignment.TopCenter))
                    }
                }
            }

            BackHandler {
                scope.launch {
                    if (scaffoldState.drawerState.isOpen)
                        scaffoldState.drawerState.close()
                    else
                        finish()
                }
            }
        }

        // handle "Sync all" intent from launcher shortcut
        if (savedInstanceState == null && intent.action == Intent.ACTION_SYNC)
            model.syncAllAccounts()
    }

    @Composable
    private fun snackbarHost(
        snackbarHostState: SnackbarHostState,
        scope: CoroutineScope
    ): @Composable (SnackbarHostState) -> Unit = {
        SnackbarHost(snackbarHostState)
        model.feedback.observeAsState().value?.let { msg ->
            scope.launch {
                snackbarHostState.showSnackbar(msg)
            }
            // reset feedback
            model.feedback.value = null
        }
    }

    @Composable
    private fun floatingActionButton(): @Composable (() -> Unit) = {
        val show by model.showAddAccount.observeAsState()
        if (show == true)
            FloatingActionButton(onClick = {
                startActivity(Intent(this@AccountsActivity, LoginActivity::class.java))
            }) {
                Icon(
                    Icons.Filled.Add,
                    stringResource(R.string.login_create_account)
                )
            }
    }

    @Composable
    private fun topBar(
        scope: CoroutineScope,
        scaffoldState: ScaffoldState
    ): @Composable (() -> Unit) = {
        TopAppBar(
            navigationIcon = {
                IconToggleButton(false, onCheckedChange = { openDrawer ->
                    scope.launch {
                        if (openDrawer)
                            scaffoldState.drawerState.open()
                        else
                            scaffoldState.drawerState.close()
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
                IconButton(onClick = { model.syncAllAccounts() }) {
                    Icon(
                        painterResource(R.drawable.ic_sync),
                        contentDescription = stringResource(R.string.accounts_sync_all)
                    )
                }
            }
        )
    }

    @Composable
    private fun drawerContent(
        scope: CoroutineScope,
        scaffoldState: ScaffoldState
    ): @Composable (ColumnScope.() -> Unit) =
        {
            AndroidView(factory = { context ->
                // use legacy NavigationView for now
                NavigationView(context).apply {
                    inflateHeaderView(R.layout.nav_header_accounts)

                    inflateMenu(R.menu.activity_accounts_drawer)
                    accountsDrawerHandler.initMenu(this@AccountsActivity, menu)

                    setNavigationItemSelectedListener { item ->
                        scope.launch {
                            accountsDrawerHandler.onNavigationItemSelected(
                                this@AccountsActivity,
                                item
                            )
                            scaffoldState.drawerState.close()
                        }
                        true
                    }
                }
            }, modifier = Modifier.fillMaxWidth())
        }


    data class AccountInfo(
        val account: Account,
        val isRefreshing: Boolean,
        val isSyncing: Boolean
    )

    @HiltViewModel
    class Model @Inject constructor(
        application: Application,
        val db: AppDatabase,
        val warnings: AppWarningsManager
    ): AndroidViewModel(application), OnAccountsUpdateListener {

        val feedback = MutableLiveData<String>()

        val accountManager = AccountManager.get(application)
        private val accountType = application.getString(R.string.account_type)
        val showAddAccount = MutableLiveData<Boolean>(true)

        val workManager = WorkManager.getInstance(application)
        val runningWorkers = workManager.getWorkInfosLiveData(WorkQuery.fromStates(WorkInfo.State.RUNNING))

        val accounts = MutableLiveData<Set<Account>>()
        val accountInfos = object: MediatorLiveData<List<AccountInfo>>() {
            var myAccounts: Set<Account> = emptySet()
            var workInfos: List<WorkInfo> = emptyList()
            init {
                addSource(accounts) { newAccounts ->
                    myAccounts = newAccounts
                    update()
                }
                addSource(runningWorkers) { newWorkInfos ->
                    workInfos = newWorkInfos
                    update()
                }
            }
            fun update() = viewModelScope.launch(Dispatchers.Default) {
                val authorities = SyncUtils.syncAuthorities(application, withContacts = true)
                val collator = Collator.getInstance()
                postValue(myAccounts
                    .toList()
                    .sortedWith { a, b -> collator.compare(a.name, b.name) }
                    .map { account ->
                        val services = db.serviceDao().getIdsByAccount(account.name)
                        AccountInfo(
                            account = account,
                            isRefreshing = workInfos.any { info ->
                                services.any { serviceId ->
                                    info.tags.contains(RefreshCollectionsWorker.workerName(serviceId))
                                }
                            },
                            isSyncing = workInfos.any { info ->
                                authorities.any { authority ->
                                    info.tags.contains(SyncWorker.workerName(account, authority))
                                }
                            }
                        )
                    })
            }
        }

        val networkAvailable = warnings.networkAvailable

        init {
            accountManager.addOnAccountsUpdatedListener(this, null, true)
        }


        // callbacks

        override fun onAccountsUpdated(newAccounts: Array<out Account>) {
            accounts.postValue(newAccounts.filter { it.type == accountType }.toSet())
        }


        // actions

        fun syncAllAccounts() {
            val context = getApplication<Application>()
            if (Build.VERSION.SDK_INT >= 25)
                context.getSystemService<ShortcutManager>()?.reportShortcutUsed(UiUtils.SHORTCUT_SYNC_ALL)

            feedback.value = context.getString(
                if (networkAvailable.value == false)
                    R.string.no_internet_sync_scheduled
                else
                    R.string.sync_started
            )

            // Enqueue sync worker for all accounts and authorities. Will sync once internet is available
            for (account in allAccounts())
                SyncWorker.enqueueAllAuthorities(context, account)
        }


        // helpers

        private fun allAccounts() =
            AccountManager.get(getApplication()).getAccountsByType(accountType)

    }

}


@Composable
fun AccountList(
    accounts: List<AccountsActivity.AccountInfo>,
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
                style = MaterialTheme.typography.h6,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    else
        for (account in accounts)
            Card(
                backgroundColor = MaterialTheme.colors.secondaryVariant,
                contentColor = MaterialTheme.colors.onSecondary,
                modifier = Modifier
                    .clickable { onClickAccount(account.account) }
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Column {
                    if (account.isRefreshing || account.isSyncing)
                        LinearProgressIndicator(
                            color = MaterialTheme.colors.onSecondary,
                            modifier = Modifier.fillMaxWidth()
                        )
                    else
                        Spacer(Modifier.height(ProgressIndicatorDefaults.StrokeWidth))

                    Column(Modifier.padding(8.dp)) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = null,
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .size(48.dp)
                        )

                        Text(
                            text = account.account.name,
                            style = MaterialTheme.typography.h5,
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
    AccountList(listOf(
        AccountsActivity.AccountInfo(
            Account("Account Name", "test"),
            isRefreshing = false,
            isSyncing = false
        )
    ))
}

@Composable
@Preview
fun AccountList_Preview_IsSyncing() {
    AccountList(listOf(
        AccountsActivity.AccountInfo(
            Account("Account Name", "test"),
            isRefreshing = false,
            isSyncing = true
        )
    ))
}

@Composable
@Preview
fun AccountList_Preview_Empty() {
    AccountList(listOf())
}


@Composable
fun SyncWarnings(
    notificationsWarning: Boolean,
    onClickPermissions: () -> Unit = {},
    internetWarning: Boolean,
    onManageConnections: () -> Unit = {},
    lowStorageWarning: Boolean,
    onManageStorage: () -> Unit = {},
    dataSaverActive: Boolean,
    onManageDataSaver: () -> Unit = {}
) {
    Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
        if (notificationsWarning)
            ActionCard(
                icon = painterResource(R.drawable.ic_notifications_off),
                actionText = stringResource(R.string.account_permissions_action),
                onAction = onClickPermissions
            ) {
                Text(stringResource(R.string.account_list_no_notification_permission))
            }

        if (internetWarning)
            ActionCard(
                icon = painterResource(R.drawable.ic_signal_cellular_off),
                actionText = stringResource(R.string.account_list_manage_connections),
                onAction = onManageConnections
            ) {
                Text(stringResource(R.string.account_list_no_internet))
            }

        if (lowStorageWarning)
            ActionCard(
                icon = painterResource(R.drawable.ic_storage),
                actionText = stringResource(R.string.account_list_manage_storage),
                onAction = onManageStorage
            ) {
                Text(stringResource(R.string.account_list_low_storage))
            }

        if (dataSaverActive)
            ActionCard(
                icon = painterResource(R.drawable.ic_datasaver_on),
                actionText = stringResource(R.string.account_list_manage_datasaver),
                onAction = onManageDataSaver
            ) {
                Text(stringResource(R.string.account_list_datasaver_enabled))
            }
    }
}

@Composable
@Preview
fun SyncWarnings_Preview() {
    SyncWarnings(
        notificationsWarning = true,
        internetWarning = true,
        lowStorageWarning = true,
        dataSaverActive = true
    )
}
