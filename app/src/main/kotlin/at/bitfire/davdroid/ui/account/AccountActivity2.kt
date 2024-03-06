package at.bitfire.davdroid.ui.account

import android.accounts.Account
import android.accounts.AccountManager
import android.accounts.OnAccountsUpdateListener
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.provider.CalendarContract
import android.provider.ContactsContract
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
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.map
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.work.WorkInfo
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.servicedetection.RefreshCollectionsWorker
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.syncadapter.SyncWorker
import com.google.accompanist.themeadapter.material.MdcTheme
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AccountActivity2 : AppCompatActivity() {

    companion object {
        const val EXTRA_ACCOUNT = "account"
    }

    @Inject
    lateinit var modelFactory: Model.Factory
    val model by viewModels<Model> {
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


    class Model @AssistedInject constructor(
        application: Application,
        val db: AppDatabase,
        @Assisted val account: Account
    ): AndroidViewModel(application), OnAccountsUpdateListener {

        @AssistedFactory
        interface Factory {
            fun create(account: Account): Model
        }

        companion object {
            const val PAGER_SIZE = 20
        }

        val invalid = MutableLiveData<Boolean>()

        val showOnlyPersonal = MutableLiveData<Boolean>()
        val showOnlyPersonalWritable = MutableLiveData<Boolean>()

        val context = getApplication<Application>()
        val accountManager: AccountManager = AccountManager.get(context)

        val cardDavSvc = db.serviceDao().getLiveByAccountAndType(account.name, Service.TYPE_CARDDAV)
        val cardDavRefreshingActive = cardDavSvc.switchMap { svc ->
            if (svc == null)
                return@switchMap null
            RefreshCollectionsWorker.exists(application, RefreshCollectionsWorker.workerName(svc.id))
        }
        val cardDavSyncPending = SyncWorker.exists(
            getApplication(),
            listOf(WorkInfo.State.ENQUEUED),
            account,
            listOf(context.getString(R.string.address_books_authority), ContactsContract.AUTHORITY)
        )
        val cardDavSyncActive = SyncWorker.exists(
            getApplication(),
            listOf(WorkInfo.State.RUNNING),
            account,
            listOf(context.getString(R.string.address_books_authority), ContactsContract.AUTHORITY)
        )
        val addressBooksPager: LiveData<Pager<Int, Collection>?> = cardDavSvc.map { svc ->
            if (svc == null)
                return@map null
            Pager(
                config = PagingConfig(PAGER_SIZE),
                pagingSourceFactory = {
                    db.collectionDao().pageByServiceAndType(svc.id, Collection.TYPE_ADDRESSBOOK)
                }
            )
        }

        val calDavSvc = db.serviceDao().getLiveByAccountAndType(account.name, Service.TYPE_CALDAV)
        val calDavRefreshingActive = calDavSvc.switchMap { svc ->
            if (svc == null)
                return@switchMap null
            RefreshCollectionsWorker.exists(application, RefreshCollectionsWorker.workerName(svc.id))
        }
        val calDavSyncPending = SyncWorker.exists(
            getApplication(),
            listOf(WorkInfo.State.ENQUEUED),
            account,
            listOf(CalendarContract.AUTHORITY)
        )
        val calDavSyncActive = SyncWorker.exists(
            getApplication(),
            listOf(WorkInfo.State.RUNNING),
            account,
            listOf(CalendarContract.AUTHORITY)
        )
        val calendarsPager: LiveData<Pager<Int, Collection>?> = calDavSvc.map { svc ->
            if (svc == null)
                return@map null
            Pager(
                config = PagingConfig(PAGER_SIZE),
                pagingSourceFactory = {
                    db.collectionDao().pageByServiceAndType(svc.id, Collection.TYPE_CALENDAR)
                }
            )
        }
        val webcalPager: LiveData<Pager<Int, Collection>?> = calDavSvc.map { svc ->
            if (svc == null)
                return@map null
            Pager(
                config = PagingConfig(PAGER_SIZE),
                pagingSourceFactory = {
                    db.collectionDao().pageByServiceAndType(svc.id, Collection.TYPE_WEBCAL)
                }
            )
        }

        init {
            accountManager.addOnAccountsUpdatedListener(this, null, true)

            viewModelScope.launch(Dispatchers.IO) {
                val accountSettings = AccountSettings(context, account)
                accountSettings.getShowOnlyPersonal().let { (value, locked) ->
                    showOnlyPersonal.postValue(value)
                    showOnlyPersonalWritable.postValue(locked)
                }
            }
        }

        override fun onCleared() {
            super.onCleared()
            accountManager.removeOnAccountsUpdatedListener(this)
        }

        override fun onAccountsUpdated(accounts: Array<out Account>) {
            if (!accounts.contains(account))
                invalid.postValue(true)
        }

        fun setCollectionSync(id: Long, sync: Boolean) = viewModelScope.launch(Dispatchers.IO) {
            db.collectionDao().updateSync(id, sync)
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
    hasCardDav: Boolean,
    cardDavRefreshing: ServiceProgressValue,
    addressBooks: LazyPagingItems<Collection>?,
    hasCalDav: Boolean,
    calDavRefreshing: ServiceProgressValue,
    calendars: LazyPagingItems<Collection>?,
    subscriptions: LazyPagingItems<Collection>?,
    onUpdateCollectionSync: (collectionId: Long, sync: Boolean) -> Unit = { _, _ -> },
    onRefreshCollections: () -> Unit = {},
    onSync: () -> Unit = {},
    onAccountSettings: () -> Unit = {},
    onNavUp: () -> Unit = {}
) {
    val refreshing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullRefreshState(
        refreshing,
        onRefresh = onRefreshCollections
    )

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
                        DropdownMenuItem(onClick = { /* rename */ }) {
                            Icon(
                                Icons.Default.DriveFileRenameOutline, stringResource(R.string.account_rename),
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(stringResource(R.string.account_rename))
                        }
                        DropdownMenuItem(onClick = { /* delete */ }) {
                            Icon(
                                Icons.Default.Delete, stringResource(R.string.delete_collection),
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(stringResource(R.string.delete_collection))
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            Column {
                FloatingActionButton(
                    onClick = { /* refresh list */ },
                    backgroundColor = MaterialTheme.colors.background,
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    // TODO refresh calendar list vs address book list
                    Icon(Icons.Default.Sync, stringResource(R.string.account_refresh_calendar_list))
                }

                FloatingActionButton(onClick = onSync) {
                    Icon(Icons.Default.Sync, stringResource(R.string.account_synchronize_now))
                }
            }
        },
        modifier = Modifier.pullRefresh(pullRefreshState)
    ) { padding ->
        var currentIdx = -1
        val idxCardDav: Int? = if (hasCardDav) ++currentIdx else null
        val idxCalDav: Int? = if (hasCalDav) ++currentIdx else null
        val idxWebcal: Int? = if ((subscriptions?.itemCount ?: 0) > 0) ++currentIdx else null
        val nrPages =
            (if (idxCardDav != null) 1 else 0) +
            (if (idxCalDav != null) 1 else 0) +
            (if (idxWebcal != null) 1 else 0)
        Logger.log.info("Pages: $nrPages")

        Column {
            if (nrPages > 0) {
                val scope = rememberCoroutineScope()
                val state = rememberPagerState(pageCount = { nrPages })

                TabRow(
                    selectedTabIndex = state.currentPage,
                    modifier = Modifier.padding(padding)
                ) {
                    if (idxCardDav != null)
                        Tab(
                            selected = state.currentPage == idxCardDav,
                            onClick = {
                                scope.launch {
                                    state.scrollToPage(idxCardDav)
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
                                selected = state.currentPage == idxCalDav,
                                onClick = {
                                    if (idxCalDav != null)
                                        scope.launch {
                                            state.scrollToPage(idxCalDav)
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
                                selected = state.currentPage == idxWebcal,
                                onClick = {
                                    if (idxWebcal != null)
                                        scope.launch {
                                            state.scrollToPage(idxWebcal)
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
                    state,
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) { index ->
                    Box {
                        when (index) {
                            idxCardDav ->
                                ServiceTab(cardDavRefreshing, addressBooks, onUpdateCollectionSync)

                            idxCalDav ->
                                ServiceTab(calDavRefreshing, calendars, onUpdateCollectionSync)

                            idxWebcal ->
                                ServiceTab(calDavRefreshing, subscriptions, onUpdateCollectionSync)
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
fun ServiceTab(
    cardDavRefreshing: ServiceProgressValue,
    addressBooks: LazyPagingItems<Collection>?,
    onUpdateCollectionSync: (collectionId: Long, sync: Boolean) -> Unit
) {
    Column {
        // progress indicator
        val progressAlpha by animateFloatAsState(
            when (cardDavRefreshing) {
                ServiceProgressValue.ACTIVE -> 1f
                ServiceProgressValue.PENDING -> .5f
                else -> 0f
            },
            label = "cardDavProgress"
        )
        when (cardDavRefreshing) {
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
                        .graphicsLayer(alpha = progressAlpha)
                        .fillMaxWidth()
                )
            else ->
                Spacer(Modifier.height(ProgressIndicatorDefaults.StrokeWidth))
        }

        //  collection list
        if (addressBooks != null)
            CollectionsList(
                addressBooks,
                onChangeSync = onUpdateCollectionSync,
                modifier = Modifier.weight(1f)
            )
    }
}