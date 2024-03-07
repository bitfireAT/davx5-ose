package at.bitfire.davdroid.ui.account

import android.accounts.Account
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.outlined.RuleFolder
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import at.bitfire.davdroid.servicedetection.RefreshCollectionsWorker
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.syncadapter.SyncWorker
import com.google.accompanist.themeadapter.material.MdcTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AccountActivity2 : AppCompatActivity() {

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


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MdcTheme {
                val cardDavSvc by model.cardDavSvc.observeAsState()
                val cardDavRefreshing by model.cardDavRefreshingActive.observeAsState(false)
                val cardDavSyncActive by model.cardDavSyncActive.observeAsState(false)
                val cardDavSyncPending by model.cardDavSyncPending.observeAsState(false)
                val cardDavProgress = when {
                    cardDavRefreshing || cardDavSyncActive -> ServiceProgressValue.ACTIVE
                    cardDavSyncPending -> ServiceProgressValue.PENDING
                    else -> ServiceProgressValue.IDLE
                }
                val addressBooks by model.addressBooksPager.observeAsState()

                val calDavSvc by model.calDavSvc.observeAsState()
                val calDavRefreshing by model.calDavRefreshingActive.observeAsState(false)
                val calDavSyncActive by model.calDavSyncActive.observeAsState(false)
                val calDavSyncPending by model.calDavSyncPending.observeAsState(false)
                val calDavProgress = when {
                    calDavRefreshing || calDavSyncActive -> ServiceProgressValue.ACTIVE
                    calDavSyncPending -> ServiceProgressValue.PENDING
                    else -> ServiceProgressValue.IDLE
                }
                val calendars by model.calendarsPager.observeAsState()
                val subscriptions by model.webcalPager.observeAsState()

                AccountOverview(
                    account = model.account,
                    showOnlyPersonal = model.showOnlyPersonal.observeAsState(AccountSettings.ShowOnlyPersonal(false, true)).value,
                    onSetShowOnlyPersonal = {
                        model.setShowOnlyPersonal(it)
                    },
                    hasCardDav = cardDavSvc != null,
                    cardDavRefreshing = cardDavProgress,
                    addressBooks = addressBooks?.flow?.collectAsLazyPagingItems(),
                    hasCalDav = calDavSvc != null,
                    calDavRefreshing = calDavProgress,
                    calendars = calendars?.flow?.collectAsLazyPagingItems(),
                    subscriptions = subscriptions?.flow?.collectAsLazyPagingItems(),
                    onUpdateCollectionSync = { id, sync ->
                        model.setCollectionSync(id, sync)
                    },
                    onChangeForceReadOnly = { id, forceReadOnly ->
                        model.setCollectionForceReadOnly(id, forceReadOnly)
                    },
                    onRefreshCollections = {
                        cardDavSvc?.let { svc ->
                            RefreshCollectionsWorker.enqueue(this@AccountActivity2, svc.id)
                        }
                        calDavSvc?.let { svc ->
                            RefreshCollectionsWorker.enqueue(this@AccountActivity2, svc.id)
                        }
                    },
                    onSync = {
                        SyncWorker.enqueueAllAuthorities(this, model.account)
                    },
                    onAccountSettings = {
                        val intent = Intent(this, SettingsActivity::class.java)
                        intent.putExtra(SettingsActivity.EXTRA_ACCOUNT, model.account)
                        startActivity(intent, null)
                    },
                    onDeleteAccount = {
                        model.deleteAccount()
                    },
                    onNavUp = ::onNavigateUp
                )
            }
        }

        model.invalid.observe(this) { invalid ->
            if (invalid)
                // account does not exist anymore
                finish()
        }
    }

}


enum class ServiceProgressValue {
    IDLE,
    PENDING,
    ACTIVE
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterialApi::class)
@Composable
fun AccountOverview(
    account: Account,
    showOnlyPersonal: AccountSettings.ShowOnlyPersonal,
    onSetShowOnlyPersonal: (showOnlyPersonal: Boolean) -> Unit,
    hasCardDav: Boolean,
    cardDavRefreshing: ServiceProgressValue,
    addressBooks: LazyPagingItems<Collection>?,
    hasCalDav: Boolean,
    calDavRefreshing: ServiceProgressValue,
    calendars: LazyPagingItems<Collection>?,
    subscriptions: LazyPagingItems<Collection>?,
    onUpdateCollectionSync: (collectionId: Long, sync: Boolean) -> Unit = { _, _ -> },
    onChangeForceReadOnly: (collectionId: Long, forceReadOnly: Boolean) -> Unit = { _, _ -> },
    onRefreshCollections: () -> Unit = {},
    onSync: () -> Unit = {},
    onAccountSettings: () -> Unit = {},
    onDeleteAccount: () -> Unit = {},
    onNavUp: () -> Unit = {}
) {
    val context = LocalContext.current

    val refreshing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullRefreshState(
        refreshing,
        onRefresh = onRefreshCollections
    )

    var showDeleteAccountDialog by remember { mutableStateOf(false) }

    // tabs calculation
    var currentIdx = -1
    @Suppress("KotlinConstantConditions")
    val idxCardDav: Int? = if (hasCardDav) ++currentIdx else null
    val idxCalDav: Int? = if (hasCalDav) ++currentIdx else null
    val idxWebcal: Int? = if ((subscriptions?.itemCount ?: 0) > 0) ++currentIdx else null
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
                        Icon(
                            Icons.AutoMirrored.Default.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_up)
                        )
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
                    var overflowOpen by remember { mutableStateOf(false) }
                    IconButton(onClick = onAccountSettings) {
                        Icon(Icons.Default.Settings, stringResource(R.string.account_settings))
                    }
                    IconButton(onClick = { overflowOpen = !overflowOpen }) {
                        Icon(Icons.Default.MoreVert, null)
                    }
                    DropdownMenu(
                        expanded = overflowOpen,
                        onDismissRequest = { overflowOpen = false }
                    ) {
                        // TAB-SPECIFIC ACTIONS

                        // create address book
                        if (pagerState.currentPage == idxCardDav) {
                            // create address book
                            DropdownMenuItem(onClick = {
                                val intent = Intent(context, CreateAddressBookActivity::class.java)
                                intent.putExtra(CreateAddressBookActivity.EXTRA_ACCOUNT, account)
                                context.startActivity(intent)

                                overflowOpen = false
                            }) {
                                Icon(
                                    Icons.Default.CreateNewFolder, stringResource(R.string.create_addressbook),
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Text(stringResource(R.string.create_addressbook))
                            }
                        } else if (pagerState.currentPage == idxCalDav) {
                            // create calendar
                            DropdownMenuItem(onClick = {
                                val intent = Intent(context, CreateCalendarActivity::class.java)
                                intent.putExtra(CreateCalendarActivity.EXTRA_ACCOUNT, account)
                                context.startActivity(intent)

                                overflowOpen = false
                            }) {
                                Icon(
                                    Icons.Default.CreateNewFolder, stringResource(R.string.create_calendar),
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
                            /* rename account */
                        }) {
                            Icon(
                                Icons.Default.DriveFileRenameOutline, stringResource(R.string.account_rename),
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
                                Icons.Default.Delete, stringResource(R.string.account_delete),
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(stringResource(R.string.account_delete))
                        }
                    }

                    if (showDeleteAccountDialog)
                        DeleteAccountDialog(
                            onConfirm = onDeleteAccount,
                            onDismiss = { showDeleteAccountDialog = false }
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
                    Icon(Icons.Outlined.RuleFolder, stringResource(R.string.account_refresh_collections))
                }

                FloatingActionButton(onClick = onSync) {
                    Icon(Icons.Default.Sync, stringResource(R.string.account_synchronize_now))
                }
            }
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

                        AnimatedVisibility(idxCalDav != null) {
                            Tab(
                                selected = pagerState.currentPage == idxCalDav,
                                onClick = {
                                    if (idxCalDav != null)
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

                        AnimatedVisibility(idxWebcal != null) {
                            Tab(
                                selected = pagerState.currentPage == idxWebcal,
                                onClick = {
                                    if (idxWebcal != null)
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
                                    refreshing = cardDavRefreshing,
                                    collections = addressBooks,
                                    onUpdateCollectionSync = onUpdateCollectionSync,
                                    onChangeForceReadOnly = onChangeForceReadOnly
                                )

                            idxCalDav ->
                                ServiceTab(
                                    refreshing = calDavRefreshing,
                                    collections = calendars,
                                    onUpdateCollectionSync = onUpdateCollectionSync,
                                    onChangeForceReadOnly = onChangeForceReadOnly
                                )

                            idxWebcal ->
                                ServiceTab(
                                    refreshing = calDavRefreshing,
                                    collections = subscriptions,
                                    onUpdateCollectionSync = onUpdateCollectionSync,
                                    onChangeForceReadOnly = onChangeForceReadOnly
                                )
                        }

                        PullRefreshIndicator(
                            refreshing = refreshing,
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
        cardDavRefreshing = ServiceProgressValue.ACTIVE,
        addressBooks = null,
        hasCalDav = true,
        calDavRefreshing = ServiceProgressValue.PENDING,
        calendars = null,
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
                Text(stringResource(android.R.string.yes).uppercase())
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.no).uppercase())
            }
        }
    )
}

@Composable
fun ServiceTab(
    refreshing: ServiceProgressValue,
    collections: LazyPagingItems<Collection>?,
    onUpdateCollectionSync: (collectionId: Long, sync: Boolean) -> Unit,
    onChangeForceReadOnly: (collectionId: Long, forceReadOnly: Boolean) -> Unit
) {
    Column {
        // progress indicator
        val progressAlpha by animateFloatAsState(
            when (refreshing) {
                ServiceProgressValue.ACTIVE -> 1f
                ServiceProgressValue.PENDING -> .5f
                else -> 0f
            },
            label = "cardDavProgress"
        )
        when (refreshing) {
            ServiceProgressValue.ACTIVE ->
                // indeterminate
                LinearProgressIndicator(
                    color = MaterialTheme.colors.secondary,
                    modifier = Modifier
                        .graphicsLayer(alpha = progressAlpha)
                        .fillMaxWidth()
                )
            ServiceProgressValue.PENDING ->
                // determinate 100%, but semi-transparent (see progressAlpha)
                LinearProgressIndicator(
                    color = MaterialTheme.colors.secondary,
                    progress = 1f,
                    modifier = Modifier
                        .alpha(progressAlpha)
                        .fillMaxWidth()
                )
            else ->
                Spacer(Modifier.height(ProgressIndicatorDefaults.StrokeWidth))
        }

        //  collection list
        if (collections != null)
            CollectionsList(
                collections,
                onChangeSync = onUpdateCollectionSync,
                onChangeForceReadOnly = onChangeForceReadOnly,
                modifier = Modifier.weight(1f)
            )
    }
}