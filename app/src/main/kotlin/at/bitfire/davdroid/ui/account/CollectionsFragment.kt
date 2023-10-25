/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui.account

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.app.Application
import android.content.*
import android.os.Bundle
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.view.*
import android.widget.PopupMenu
import androidx.annotation.CallSuper
import androidx.core.view.MenuProvider
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
import at.bitfire.davdroid.resource.TaskUtils
import at.bitfire.davdroid.servicedetection.RefreshCollectionsWorker
import at.bitfire.davdroid.syncadapter.SyncWorker
import at.bitfire.davdroid.ui.PermissionsActivity
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.AndroidEntryPoint
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

        model.hasWriteableCollections.observe(viewLifecycleOwner) {
            requireActivity().invalidateOptionsMenu()
        }
        model.collectionColors.observe(viewLifecycleOwner) { colors: List<Int?> ->
            val realColors = colors.filterNotNull()
            if (realColors.isNotEmpty())
                binding.swipeRefresh.setColorSchemeColors(*realColors.toIntArray())
        }
        binding.swipeRefresh.setOnRefreshListener(this)

        val updateProgress = Observer<Boolean> {
            binding.progress.apply {
                val isVisible = model.isSyncActive.value == true || model.isSyncPending.value == true

                if (model.isSyncActive.value == true) {
                    isIndeterminate = true
                } else if (model.isSyncPending.value == true) {
                    isIndeterminate = false
                    progress = 100
                }

                animate()
                    .alpha(if (isVisible) 1f else 0f)
                    // go to VISIBLE instantly, take 500 ms for INVISIBLE
                    .setDuration(if (isVisible) 0 else 500)
                    .setListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            super.onAnimationEnd(animation)
                            visibility = if (isVisible) View.VISIBLE else View.INVISIBLE
                        }
                    })
            }
        }
        model.isSyncPending.observe(viewLifecycleOwner, updateProgress)
        model.isSyncActive.observe(viewLifecycleOwner, updateProgress)

        val adapter = createAdapter()
        binding.list.layoutManager = LinearLayoutManager(requireActivity())
        binding.list.adapter = adapter
        model.collections.observe(viewLifecycleOwner) { data ->
            lifecycleScope.launch {
                adapter.submitData(data)
            }
        }
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

    override fun onRefresh() {
        // Disable swipe-down refresh spinner, as we use the progress bar instead
        binding.swipeRefresh.isRefreshing = false
        // Swipe-down gesture starts sync
        model.sync()
    }

    override fun onResume() {
        super.onResume()
        checkPermissions()
        (activity as? AccountActivity)?.updateRefreshCollectionsListAction(this)
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

    abstract inner class CollectionsMenuProvider : MenuProvider {
        abstract override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater)

        @CallSuper
        override fun onPrepareMenu(menu: Menu) {
            menu.findItem(R.id.showOnlyPersonal).let { showOnlyPersonal ->
                accountModel.showOnlyPersonal.value?.let { value ->
                    showOnlyPersonal.isChecked = value
                }
                accountModel.showOnlyPersonalWritable.value?.let { writable ->
                    showOnlyPersonal.isEnabled = writable
                }
            }
        }

        @CallSuper
        override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
            return when (menuItem.itemId) {
                R.id.refresh -> {
                    model.refresh()
                    true
                }
                R.id.showOnlyPersonal -> {
                    accountModel.toggleShowOnlyPersonal()
                    true
                }
                else ->
                    false
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
        application: Application,
        val db: AppDatabase,
        @Assisted val accountModel: AccountActivity.Model,
        @Assisted val serviceId: Long,
        @Assisted val collectionType: String
    ): AndroidViewModel(application) {

        @AssistedFactory
        interface Factory {
            fun create(accountModel: AccountActivity.Model, serviceId: Long, collectionType: String): Model
        }

        // cache task provider
        val taskProvider by lazy { TaskUtils.currentProvider(getApplication()) }

        val hasWriteableCollections = db.homeSetDao().hasBindableByServiceLive(serviceId)
        val collectionColors = db.collectionDao().colorsByServiceLive(serviceId)
        val collections: LiveData<PagingData<Collection>> =
            accountModel.showOnlyPersonal.switchMap { onlyPersonal ->
                val pager = Pager(
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
                )
                return@switchMap pager
                    .liveData
                    .cachedIn(viewModelScope)
            }

        // observe SyncWorker state
        private val authorities =
            if (collectionType == Collection.TYPE_ADDRESSBOOK)
                listOf(getApplication<Application>().getString(R.string.address_books_authority), ContactsContract.AUTHORITY)
            else
                listOf(CalendarContract.AUTHORITY, taskProvider?.authority).filterNotNull()
        val isSyncActive = SyncWorker.exists(getApplication(),
            listOf(WorkInfo.State.RUNNING),
            accountModel.account,
            authorities)
        val isSyncPending = SyncWorker.exists(getApplication(),
            listOf(WorkInfo.State.ENQUEUED),
            accountModel.account,
            authorities)

        // actions

        fun sync() = SyncWorker.enqueueAllAuthorities(getApplication(), accountModel.account)

        fun refresh() = RefreshCollectionsWorker.refreshCollections(getApplication(), serviceId)

    }

}