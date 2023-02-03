/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui.account

import android.content.*
import android.os.Bundle
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.view.*
import android.widget.PopupMenu
import androidx.annotation.AnyThread
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.*
import androidx.paging.*
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.viewbinding.ViewBinding
import androidx.work.WorkInfo
import at.bitfire.davdroid.R
import at.bitfire.davdroid.databinding.AccountCollectionsBinding
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.resource.LocalAddressBook
import at.bitfire.davdroid.resource.TaskUtils
import at.bitfire.davdroid.servicedetection.RefreshCollectionsWorker
import at.bitfire.davdroid.syncadapter.SyncWorker
import at.bitfire.davdroid.ui.PermissionsActivity
import at.bitfire.davdroid.util.LiveDataUtils
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
abstract class CollectionsFragment: Fragment(), SwipeRefreshLayout.OnRefreshListener {

    companion object {
        const val EXTRA_SERVICE_ID = "serviceId"
        const val EXTRA_COLLECTION_TYPE = "collectionType"
    }

    private var _binding: AccountCollectionsBinding? = null
    protected val binding get() = _binding!!

    val accountModel by activityViewModels<AccountActivity.Model>()
    @Inject lateinit var modelFactory: Model.Factory
    val model by viewModels<Model> {
        object: ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T: ViewModel> create(modelClass: Class<T>): T =
                modelFactory.create(
                    accountModel,
                    requireArguments().getLong(EXTRA_SERVICE_ID),
                    requireArguments().getString(EXTRA_COLLECTION_TYPE) ?: throw IllegalArgumentException("EXTRA_COLLECTION_TYPE required")
                ) as T
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    abstract val noCollectionsStringId: Int

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = AccountCollectionsBinding.inflate(inflater, container, false)

        binding.permissionsBtn.setOnClickListener {
            startActivity(Intent(requireActivity(), PermissionsActivity::class.java))
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        model.isRefreshing.observe(viewLifecycleOwner, Observer { nowRefreshing ->
            binding.swipeRefresh.isRefreshing = nowRefreshing
        })
        model.hasWriteableCollections.observe(viewLifecycleOwner, Observer {
            requireActivity().invalidateOptionsMenu()
        })
        model.collectionColors.observe(viewLifecycleOwner, Observer { colors: List<Int?> ->
            val realColors = colors.filterNotNull()
            if (realColors.isNotEmpty())
                binding.swipeRefresh.setColorSchemeColors(*realColors.toIntArray())
        })
        binding.swipeRefresh.setOnRefreshListener(this)

        val updateProgress = Observer<Boolean> {
            if (model.isSyncActive.value == true) {
                binding.progress.isIndeterminate = true
                binding.progress.alpha = 1.0f
                binding.progress.visibility = View.VISIBLE
            } else {
                if (model.isSyncPending.value == true) {
                    binding.progress.visibility = View.VISIBLE
                    binding.progress.alpha = 0.2f
                    binding.progress.isIndeterminate = false
                    binding.progress.progress = 100
                } else
                    binding.progress.visibility = View.INVISIBLE
            }
        }
        model.isSyncPending.observe(viewLifecycleOwner, updateProgress)
        model.isSyncActive.observe(viewLifecycleOwner, updateProgress)

        val adapter = createAdapter()
        binding.list.layoutManager = LinearLayoutManager(requireActivity())
        binding.list.adapter = adapter
        model.collections.observe(viewLifecycleOwner, Observer { data ->
            lifecycleScope.launch {
                adapter.submitData(data)
            }
        })
        adapter.addLoadStateListener { loadStates ->
            if (loadStates.refresh is LoadState.NotLoading) {
                if (adapter.itemCount > 0) {
                    binding.list.visibility = View.VISIBLE
                    binding.empty.visibility = View.GONE
                } else {
                    binding.list.visibility = View.GONE
                    binding.empty.visibility = View.VISIBLE
                }
            }
        }

        binding.noCollections.setText(noCollectionsStringId)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        menu.findItem(R.id.showOnlyPersonal).let { showOnlyPersonal ->
            accountModel.showOnlyPersonal.value?.let { value ->
                showOnlyPersonal.isChecked = value
            }
            accountModel.showOnlyPersonal_writable.value?.let { writable ->
                showOnlyPersonal.isEnabled = writable
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem) =
            when (item.itemId) {
                R.id.refresh -> {
                    onRefresh()
                    true
                }
                R.id.showOnlyPersonal -> {
                    accountModel.toggleShowOnlyPersonal()
                    true
                }
                else ->
                    false
            }

    override fun onRefresh() {
        model.refresh()
    }

    override fun onResume() {
        super.onResume()
        checkPermissions()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }


    protected abstract fun checkPermissions()
    protected abstract fun createAdapter(): CollectionAdapter


    abstract class CollectionViewHolder<T: ViewBinding>(
        parent: ViewGroup,
        val binding: T,
        protected val accountModel: AccountActivity.Model
    ): RecyclerView.ViewHolder(binding.root) {
        abstract fun bindTo(item: Collection)
    }

    abstract class CollectionAdapter(
        protected val accountModel: AccountActivity.Model
    ): PagingDataAdapter<Collection, CollectionViewHolder<*>>(DIFF_CALLBACK) {

        companion object {
            private val DIFF_CALLBACK = object: DiffUtil.ItemCallback<Collection>() {
                override fun areItemsTheSame(oldItem: Collection, newItem: Collection) =
                        oldItem.id == newItem.id

                override fun areContentsTheSame(oldItem: Collection, newItem: Collection) =
                        oldItem == newItem
            }
        }

        override fun onBindViewHolder(holder: CollectionViewHolder<*>, position: Int) {
            getItem(position)?.let { item ->
                holder.bindTo(item)
            }
        }

    }

    class CollectionPopupListener(
        private val accountModel: AccountActivity.Model,
        private val item: Collection,
        private val fragmentManager: FragmentManager,
        private val forceReadOnly: Boolean = false
    ): View.OnClickListener {

        override fun onClick(anchor: View) {
            val popup = PopupMenu(anchor.context, anchor, Gravity.RIGHT)
            popup.inflate(R.menu.account_collection_operations)

            with(popup.menu.findItem(R.id.force_read_only)) {
                if (item.type == Collection.TYPE_WEBCAL)
                    // Webcal collections are always read-only
                    isVisible = false
                else {
                    // non-Webcal collection
                    if (item.privWriteContent)
                        isChecked = item.forceReadOnly
                    else
                        isVisible = false
                }

                if (item.type == Collection.TYPE_ADDRESSBOOK && forceReadOnly) {
                    // managed restriction "force read-only address books" is active
                    isChecked = true
                    isEnabled = false
                }
            }
            popup.menu.findItem(R.id.delete_collection).isVisible = item.privUnbind

            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.force_read_only -> {
                        accountModel.toggleReadOnly(item)
                    }
                    R.id.properties ->
                        CollectionInfoFragment.newInstance(item.id).show(fragmentManager, null)
                    R.id.delete_collection ->
                        DeleteCollectionFragment.newInstance(accountModel.account, item.id).show(fragmentManager, null)
                }
                true
            }
            popup.show()
        }

    }


    class Model @AssistedInject constructor(
        @ApplicationContext val context: Context,
        val db: AppDatabase,
        @Assisted val accountModel: AccountActivity.Model,
        @Assisted val serviceId: Long,
        @Assisted val collectionType: String
    ): ViewModel(), SyncStatusObserver {

        @AssistedFactory
        interface Factory {
            fun create(accountModel: AccountActivity.Model, serviceId: Long, collectionType: String): Model
        }

        // cache task provider
        val taskProvider by lazy { TaskUtils.currentProvider(context) }

        val hasWriteableCollections = db.homeSetDao().hasBindableByServiceLive(serviceId)
        val collectionColors = db.collectionDao().colorsByServiceLive(serviceId)
        val collections: LiveData<PagingData<Collection>> =
            Transformations.switchMap(accountModel.showOnlyPersonal) { onlyPersonal ->
                Pager(
                    PagingConfig(pageSize = 25),
                    pagingSourceFactory = {
                        Logger.log.info("Creating new pager onlyPersonal=$onlyPersonal")
                        if (onlyPersonal)
                            // show only personal collections
                            db.collectionDao().pagePersonalByServiceAndType(serviceId, collectionType)
                        else
                            // show all collections
                            db.collectionDao().pageByServiceAndType(serviceId, collectionType)
                    }
                ).liveData
            }

        // observe RefreshCollectionsWorker status
        val isRefreshing = RefreshCollectionsWorker.isWorkerInState(context, RefreshCollectionsWorker.workerName(serviceId), WorkInfo.State.RUNNING)

        // observe whether sync framework is active
        private var syncFrameworkStatusHandle: Any? = null
        private val isSyncFrameworkActive = MutableLiveData<Boolean>()
        private val isSyncFrameworkPending = MutableLiveData<Boolean>()

        // observe SyncWorker state
        private val authorities =
            if (collectionType == Collection.TYPE_ADDRESSBOOK)
                listOf(context.getString(R.string.address_books_authority), ContactsContract.AUTHORITY)
            else
                listOf(CalendarContract.AUTHORITY, taskProvider?.authority).filterNotNull()
        private val isSyncWorkerRunning = SyncWorker.isSomeWorkerInState(context,
            WorkInfo.State.RUNNING,
            accountModel.account,
            authorities)
        private val isSyncWorkerEnqueued = SyncWorker.isSomeWorkerInState(context,
            WorkInfo.State.ENQUEUED,
            accountModel.account,
            authorities)

        // observe and combine states of sync framework and SyncWorker
        val isSyncActive = LiveDataUtils.liveDataLogicOr(listOf(isSyncFrameworkActive, isSyncWorkerRunning))
        val isSyncPending = LiveDataUtils.liveDataLogicOr(listOf(isSyncFrameworkPending, isSyncWorkerEnqueued))

        init {
            viewModelScope.launch(Dispatchers.Default) {
                syncFrameworkStatusHandle = ContentResolver.addStatusChangeListener(ContentResolver.SYNC_OBSERVER_TYPE_PENDING +
                        ContentResolver.SYNC_OBSERVER_TYPE_ACTIVE, this@Model)
                checkSyncFrameworkStatus()
            }
        }

        override fun onCleared() {
            syncFrameworkStatusHandle?.let {
                ContentResolver.removeStatusChangeListener(it)
            }
        }

        @AnyThread
        override fun onStatusChanged(which: Int) {
            checkSyncFrameworkStatus()
        }

        @AnyThread
        @Synchronized
        private fun checkSyncFrameworkStatus() {
            // SyncFramework only, isSyncFrameworkActive/Pending gets combined in logic OR with SyncWorker state
            if (collectionType == Collection.TYPE_ADDRESSBOOK) {
                // CardDAV tab
                val mainAuthority = context.getString(R.string.address_books_authority)
                val mainSyncActive = ContentResolver.isSyncActive(accountModel.account, mainAuthority)
                val mainSyncPending = ContentResolver.isSyncPending(accountModel.account, mainAuthority)

                val addrBookAccounts = LocalAddressBook.findAll(context, null, accountModel.account).map { it.account }
                val syncActive = addrBookAccounts.any { ContentResolver.isSyncActive(it, ContactsContract.AUTHORITY) }
                val syncPending = addrBookAccounts.any { ContentResolver.isSyncPending(it, ContactsContract.AUTHORITY) }

                isSyncFrameworkActive.postValue(mainSyncActive || syncActive)
                isSyncFrameworkPending.postValue(mainSyncPending || syncPending)

            } else {
                // CalDAV tab
                val authorities = mutableListOf(CalendarContract.AUTHORITY)
                taskProvider?.let {
                    authorities += it.authority
                }
                isSyncFrameworkActive.postValue(authorities.any {
                    ContentResolver.isSyncActive(accountModel.account, it)
                })
                isSyncFrameworkPending.postValue(authorities.any {
                    ContentResolver.isSyncPending(accountModel.account, it)
                })
            }
        }


        // actions

        fun refresh() {
            RefreshCollectionsWorker.refreshCollections(context, serviceId)
        }

    }

}