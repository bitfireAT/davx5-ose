package at.bitfire.davdroid.ui.account

import android.accounts.Account
import android.accounts.AccountManager
import android.accounts.OnAccountsUpdateListener
import android.app.Application
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.compose.collectAsLazyPagingItems
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.settings.AccountSettings
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
                AccountOverview(
                    account = model.account,
                    hasCardDav = model.cardDavSvc.observeAsState().value != null,
                    addressBooks = model.addressBooks.observeAsState().value,
                    hasCalDav = model.calDavSvc.observeAsState().value != null,
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

    // menu actions

    fun openAccountSettings() {
        val intent = Intent(this, SettingsActivity::class.java)
        intent.putExtra(SettingsActivity.EXTRA_ACCOUNT, model.account)
        startActivity(intent, null)
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

        val invalid = MutableLiveData<Boolean>()

        val showOnlyPersonal = MutableLiveData<Boolean>()
        val showOnlyPersonalWritable = MutableLiveData<Boolean>()

        val context = getApplication<Application>()
        val accountManager: AccountManager = AccountManager.get(context)

        val cardDavSvc = db.serviceDao().getLiveByAccountAndType(account.name, Service.TYPE_CARDDAV)
        val addressBooks = cardDavSvc.map { svc ->
            svc?.id?.let { svcId ->
                db.collectionDao().pageByServiceAndType(svcId, Collection.TYPE_ADDRESSBOOK)
            }
        }

        val calDavSvc = db.serviceDao().getLiveByAccountAndType(account.name, Service.TYPE_CALDAV)
        val calendars = calDavSvc.map { svc ->
            svc?.id?.let { svcId ->
                db.collectionDao().pageByServiceAndType(svcId, Collection.TYPE_CALENDAR)
            }
        }
        val subscriptions = calDavSvc.map { svc ->
            svc?.id?.let { svcId ->
                db.collectionDao().pageByServiceAndType(svcId, Collection.TYPE_CALENDAR)
            }
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

    }

}


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AccountOverview(
    account: Account,
    hasCardDav: Boolean,
    addressBooks: PagingSource<Int, Collection>?,
    hasCalDav: Boolean,
    onNavUp: () -> Unit = {}
) {
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
                    IconButton(onClick = { /* TODO account settings */ }) {
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

                FloatingActionButton(onClick = { /* sync */ }) {
                    Icon(Icons.Default.Sync, stringResource(R.string.account_synchronize_now))
                }
            }
        }
    ) { padding ->
        val cardDavPageCount = if (hasCardDav) 1 else 0
        val calDavPageCount = if (hasCalDav) /* CalDAV, Webcal */ 2 else 0

        var currentIdx = -1
        val idxCardDav: Int? = if (hasCardDav) currentIdx.inc() else null
        val idxCalDav: Int? = if (hasCalDav) ++currentIdx else null
        val idxWebcal: Int? = if (hasCalDav) ++currentIdx else null

        val scope = rememberCoroutineScope()
        val state = rememberPagerState(pageCount = { cardDavPageCount + calDavPageCount })

        Column {
            TabRow(
                selectedTabIndex = state.currentPage,
                modifier = Modifier.padding(padding)
            ) {
                Tab(
                    selected = state.currentPage == idxCardDav,
                    onClick = {
                        scope.launch {
                            state.scrollToPage(0)
                        }
                    }
                ) {
                    Text(
                        stringResource(R.string.account_carddav).uppercase(),
                        modifier = Modifier.padding(8.dp)
                    )
                }

                Tab(
                    selected = state.currentPage == idxCalDav,
                    onClick = {
                        scope.launch {
                            state.scrollToPage(1)
                        }
                    }
                ) {
                    Text(
                        stringResource(R.string.account_caldav).uppercase(),
                        modifier = Modifier.padding(8.dp)
                    )
                }

                Tab(
                    selected = state.currentPage == idxWebcal,
                    onClick = {
                        scope.launch {
                            state.scrollToPage(2)
                        }
                    }
                ) {
                    Text(
                        stringResource(R.string.account_webcal).uppercase(),
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            HorizontalPager(
                state,
                verticalAlignment = Alignment.Top,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { index ->
                when (index) {
                    0 -> {
                        if (addressBooks != null) {
                            val pager = Pager(
                                config = PagingConfig(20),
                                pagingSourceFactory = {
                                    addressBooks
                                }
                            )
                            val pagedItems = pager.flow.collectAsLazyPagingItems()
                            AddressBooksList(pagedItems)
                        }
                    }

                    1 -> {
                        Text("CalDAV")
                    }

                    2 -> {
                        Text("Webcal")
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
        addressBooks = null,
        hasCalDav = true
    )
}