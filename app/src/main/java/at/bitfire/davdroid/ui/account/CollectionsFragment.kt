package at.bitfire.davdroid.ui.account

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.*
import android.widget.PopupMenu
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.*
import androidx.paging.PagedList
import androidx.paging.PagedListAdapter
import androidx.paging.toLiveData
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import at.bitfire.davdroid.DavService
import at.bitfire.davdroid.R
import at.bitfire.davdroid.model.AppDatabase
import at.bitfire.davdroid.model.Collection
import at.bitfire.davdroid.ui.CollectionInfoFragment
import at.bitfire.davdroid.ui.DeleteCollectionFragment
import kotlinx.android.synthetic.main.account_addressbooks.view.*

abstract class CollectionsFragment: Fragment(), SwipeRefreshLayout.OnRefreshListener {

    companion object {
        const val EXTRA_SERVICE_ID = "serviceId"
        const val EXTRA_COLLECTION_TYPE = "collectionType"
    }

    lateinit var accountModel: AccountActivity.Model
    lateinit var model: Model

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        accountModel = ViewModelProviders.of(requireActivity()).get(AccountActivity.Model::class.java)
        model = ViewModelProviders.of(this).get(Model::class.java)
        model.initialize(
                arguments?.getLong(EXTRA_SERVICE_ID) ?: throw IllegalArgumentException("EXTRA_SERVICE_ID required"),
                arguments?.getString(EXTRA_COLLECTION_TYPE) ?: throw IllegalArgumentException("EXTRA_COLLECTION_TYPE required")
        )

        setHasOptionsMenu(true)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) =
            inflater.inflate(R.layout.account_addressbooks, container, false)!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        model.isRefreshing.observe(viewLifecycleOwner, Observer { nowRefreshing ->
            view.swipe_refresh.isRefreshing = nowRefreshing
        })
        view.swipe_refresh.setOnRefreshListener(this)

        val adapter = createAdapter()
        view.list.layoutManager = LinearLayoutManager(requireActivity())
        view.list.adapter = adapter
        model.collections.observe(viewLifecycleOwner, Observer { data ->
            adapter.submitList(data)
        })
    }

    protected abstract fun createAdapter(): CollectionAdapter

    override fun onOptionsItemSelected(item: MenuItem) =
            when (item.itemId) {
                R.id.refresh -> {
                    onRefresh()
                    true
                }
                else ->
                    false
            }

    override fun onRefresh() {
        model.refresh()
    }



    abstract class CollectionViewHolder(
            parent: ViewGroup,
            itemLayout: Int,
            protected val accountModel: AccountActivity.Model
    ): RecyclerView.ViewHolder(
            LayoutInflater.from(parent.context).inflate(itemLayout, parent, false)
    ) {
        abstract fun bindTo(item: Collection)
    }

    abstract class CollectionAdapter(
            protected val accountModel: AccountActivity.Model
    ): PagedListAdapter<Collection, CollectionViewHolder>(DIFF_CALLBACK) {

        companion object {
            private val DIFF_CALLBACK = object: DiffUtil.ItemCallback<Collection>() {
                override fun areItemsTheSame(oldItem: Collection, newItem: Collection) =
                        oldItem.id == newItem.id

                override fun areContentsTheSame(oldItem: Collection, newItem: Collection) =
                        oldItem == newItem
            }
        }

        override fun onBindViewHolder(holder: CollectionViewHolder, position: Int) {
            getItem(position)?.let { item ->
                holder.bindTo(item)
            }
        }

    }

    class CollectionPopupListener(
            private val accountModel: AccountActivity.Model,
            private val item: Collection
    ): View.OnClickListener {

        override fun onClick(anchor: View) {
            val fragmentManager = (anchor.context as AppCompatActivity).supportFragmentManager
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


    class Model(application: Application): AndroidViewModel(application), DavService.RefreshingStatusListener {

        private val db = AppDatabase.getInstance(application)

        val serviceId = MutableLiveData<Long>()
        lateinit var collectionType: String

        val collections: LiveData<PagedList<Collection>> =
                Transformations.switchMap(serviceId) { service ->
                    db.collectionDao().pageByServiceAndType(service, collectionType).toLiveData(25)
                }

        // observe DavService refresh status
        @Volatile
        private var davService: DavService.InfoBinder? = null
        private var davServiceConn: ServiceConnection? = null
        val svcConn = object: ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                val svc = service as DavService.InfoBinder
                davService = svc
                svc.addRefreshingStatusListener(this@Model, true)
            }
            override fun onServiceDisconnected(name: ComponentName?) {
                davService = null
            }
        }
        val isRefreshing = MutableLiveData<Boolean>()

        fun initialize(id: Long, collectionType: String) {
            this.collectionType = collectionType
            if (serviceId.value == null)
                serviceId.value = id

            val context = getApplication<Application>()
            if (context.bindService(Intent(context, DavService::class.java), svcConn, Context.BIND_AUTO_CREATE))
                davServiceConn = svcConn
        }

        override fun onCleared() {
            davService?.removeRefreshingStatusListener(this)
            davServiceConn?.let {
                getApplication<Application>().unbindService(it)
                davServiceConn = null
            }
        }

        fun refresh() {
            val context = getApplication<Application>()
            val intent = Intent(context, DavService::class.java)
            intent.action = DavService.ACTION_REFRESH_COLLECTIONS
            intent.putExtra(DavService.EXTRA_DAV_SERVICE_ID, serviceId.value)
            context.startService(intent)
        }

        override fun onDavRefreshStatusChanged(id: Long, refreshing: Boolean) {
            if (id == serviceId.value)
                isRefreshing.postValue(refreshing)
        }

    }

}