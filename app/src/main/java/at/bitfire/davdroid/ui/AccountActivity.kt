/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.ui

import android.Manifest
import android.accounts.Account
import android.accounts.AccountManager
import android.app.Application
import android.content.*
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.*
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.Settings
import android.view.*
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat.checkSelfPermission
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.*
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import at.bitfire.davdroid.*
import at.bitfire.davdroid.R
import at.bitfire.davdroid.databinding.ActivityAccountBinding
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.model.AppDatabase
import at.bitfire.davdroid.model.Collection
import at.bitfire.davdroid.model.Service
import at.bitfire.davdroid.resource.LocalAddressBook
import at.bitfire.davdroid.resource.LocalTaskList
import at.bitfire.ical4android.TaskProvider
import com.google.android.material.snackbar.Snackbar
import java.util.concurrent.Executors
import java.util.logging.Level
import kotlin.concurrent.thread

class AccountActivity: AppCompatActivity(), Toolbar.OnMenuItemClickListener, PopupMenu.OnMenuItemClickListener {

    companion object {
        const val EXTRA_ACCOUNT = "account"

        const val REQUEST_CODE_PERMISSIONS_UPDATED = 0
    }

    private lateinit var model: Model
    private lateinit var binding: ActivityAccountBinding


    private val openAppSettings = { _: View ->
        val appSettings = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", BuildConfig.APPLICATION_ID, null))
        if (appSettings.resolveActivity(packageManager) != null)
            startActivityForResult(appSettings, REQUEST_CODE_PERMISSIONS_UPDATED)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        model = ViewModelProviders.of(this).get(Model::class.java)

        binding = DataBindingUtil.setContentView<ActivityAccountBinding>(this, R.layout.activity_account)
        binding.lifecycleOwner = this
        binding.model = model

        // account may be a DAVx5 address book account -> use main account in this case
        val account = LocalAddressBook.mainAccount(this,
                requireNotNull(intent.getParcelableExtra(EXTRA_ACCOUNT)))
        model.initialize(account)
        title = model.account.name

        val icMenu = AppCompatResources.getDrawable(this, R.drawable.ic_menu_light)

        // permissions
        model.askForContactsPermissions.observe(this, Observer { needsContactPermissions ->
            if (needsContactPermissions)
                ActivityCompat.requestPermissions(this, ContactsPermissionsCalculator.permissions, 0)
        })
        binding.contactPermissions.setOnClickListener(openAppSettings)

        model.askForCalendarPermissions.observe(this, Observer { permissions ->
            if (permissions.isNotEmpty())
                ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 0)
        })
        binding.calendarPermissions.setOnClickListener(openAppSettings)

        // CardDAV
        binding.carddavMenu.apply {
            overflowIcon = icMenu
            inflateMenu(R.menu.carddav_actions)
            setOnMenuItemClickListener(this@AccountActivity)

            model.hasAddressBookHomeSets.observe(this@AccountActivity, Observer { hasHomeSets ->
                menu.findItem(R.id.create_address_book).isEnabled = hasHomeSets
            })
        }
        binding.addressBooks.apply {
            layoutManager = LinearLayoutManager(this@AccountActivity)
            adapter = AddressBookAdapter(this@AccountActivity, model)
        }

        // CalDAV
        binding.caldavMenu.apply {
            overflowIcon = icMenu
            inflateMenu(R.menu.caldav_actions)
            setOnMenuItemClickListener(this@AccountActivity)

            model.hasCalendarHomeSets.observe(this@AccountActivity, Observer { hasHomeSets ->
                menu.findItem(R.id.create_calendar).isEnabled = hasHomeSets
            })
        }
        binding.calendars.apply {
            layoutManager = LinearLayoutManager(this@AccountActivity)
            adapter = CalendarAdapter(this@AccountActivity, model)
        }

        // Webcal
        binding.webcalMenu.apply {
            overflowIcon = icMenu
            inflateMenu(R.menu.webcal_actions)
            setOnMenuItemClickListener(this@AccountActivity)
        }
        binding.webcals.apply {
            layoutManager = LinearLayoutManager(this@AccountActivity)
            adapter = WebcalAdapter(this@AccountActivity, model)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) =
            model.onPermissionsUpdated()

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_PERMISSIONS_UPDATED)
            model.onPermissionsUpdated()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.activity_account, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val itemRename = menu.findItem(R.id.rename_account)
        // renameAccount is available for API level 21+
        itemRename.isVisible = Build.VERSION.SDK_INT >= 21
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.sync_now ->
                requestSync()
            R.id.settings -> {
                val intent = Intent(this, AccountSettingsActivity::class.java)
                intent.putExtra(AccountSettingsActivity.EXTRA_ACCOUNT, model.account)
                startActivity(intent)
            }
            R.id.rename_account ->
                RenameAccountFragment.newInstance(model.account).show(supportFragmentManager, null)
            R.id.delete_account -> {
                AlertDialog.Builder(this)
                        .setIcon(R.drawable.ic_error_dark)
                        .setTitle(R.string.account_delete_confirmation_title)
                        .setMessage(R.string.account_delete_confirmation_text)
                        .setNegativeButton(android.R.string.no, null)
                        .setPositiveButton(android.R.string.yes) { _, _ ->
                            deleteAccount()
                        }
                        .show()
            }
            else ->
                return super.onOptionsItemSelected(item)
        }
        return true
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.refresh_address_books ->
                model.cardDavServiceId.value?.let { id ->
                    val intent = Intent(this, DavService::class.java)
                    intent.action = DavService.ACTION_REFRESH_COLLECTIONS
                    intent.putExtra(DavService.EXTRA_DAV_SERVICE_ID, id)
                    startService(intent)
                }
            R.id.create_address_book -> {
                val intent = Intent(this, CreateAddressBookActivity::class.java)
                intent.putExtra(CreateAddressBookActivity.EXTRA_ACCOUNT, model.account)
                startActivity(intent)
            }
            R.id.refresh_calendars ->
                model.calDavServiceId.value?.let { id ->
                    val intent = Intent(this, DavService::class.java)
                    intent.action = DavService.ACTION_REFRESH_COLLECTIONS
                    intent.putExtra(DavService.EXTRA_DAV_SERVICE_ID, id)
                    startService(intent)
                }
            R.id.create_calendar -> {
                val intent = Intent(this, CreateCalendarActivity::class.java)
                intent.putExtra(CreateCalendarActivity.EXTRA_ACCOUNT, model.account)
                startActivity(intent)
            }
        }
        return false
    }


    private val onActionOverflowListener = { anchor: View, info: Collection, adapter: RecyclerView.Adapter<*>, position: Int ->
        val popup = PopupMenu(this, anchor, Gravity.RIGHT)
        popup.inflate(R.menu.account_collection_operations)

        with(popup.menu.findItem(R.id.force_read_only)) {
            if (info.privWriteContent)
                isChecked = info.forceReadOnly
            else
                isVisible = false
        }

        popup.menu.findItem(R.id.delete_collection).isVisible = info.privUnbind

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.force_read_only -> {
                    //info.uiEnabled = false
                    adapter.notifyItemChanged(position)

                    model.updateCollectionReadOnly(info, !info.forceReadOnly)
                }
                R.id.delete_collection ->
                    DeleteCollectionFragment.newInstance(model.account, info.id).show(supportFragmentManager, null)
                R.id.properties ->
                    CollectionInfoFragment.newInstance(info.id).show(supportFragmentManager, null)
            }
            true
        }
        popup.show()

        // click was handled
        true
    }


    /* LIST ADAPTERS */

    class AddressBookAdapter(
            val activity: AccountActivity,
            val model: Model
    ): RecyclerView.Adapter<AddressBookAdapter.AddressBookViewHolder>() {

        class AddressBookViewHolder(view: View): RecyclerView.ViewHolder(view)

        init {
            model.addressBooks.observe(activity, Observer {
                notifyDataSetChanged()
            })
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
                AddressBookViewHolder(
                        LayoutInflater.from(parent.context).inflate(R.layout.account_carddav_item, parent, false)
                )

        override fun getItemCount() = model.addressBooks.value?.size ?: 0

        override fun onBindViewHolder(holder: AddressBookViewHolder, position: Int) {
            val info = model.addressBooks.value?.get(position) ?: return
            val v = holder.itemView

            v.setOnClickListener {
                model.updateCollectionSelected(info, !info.sync)
            }

            v.findViewById<CheckBox>(R.id.checked).apply {
                isChecked = info.sync
            }

            v.findViewById<TextView>(R.id.title).text =
                    if (!info.displayName.isNullOrBlank()) info.displayName else info.url.toString()

            v.findViewById<TextView>(R.id.description).apply {
                if (info.description.isNullOrBlank())
                    visibility = View.GONE
                else {
                    text = info.description
                    visibility = View.VISIBLE
                }
            }

            v.findViewById<View>(R.id.read_only).visibility =
                    if (!info.privWriteContent || info.forceReadOnly) View.VISIBLE else View.GONE

            v.findViewById<ImageView>(R.id.action_overflow).apply {
                setOnClickListener { view ->
                    activity.onActionOverflowListener(view, info, this@AddressBookAdapter, position)
                }
            }
        }

    }

    open class CalendarAdapter(
            val activity: AccountActivity,
            val model: Model
    ): RecyclerView.Adapter<CalendarAdapter.CalendarViewHolder>() {

        open fun data() = model.calendars

        class CalendarViewHolder(view: View): RecyclerView.ViewHolder(view)

        init {
            data().observe(activity, Observer {
                notifyDataSetChanged()
            })
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
                CalendarViewHolder(
                        LayoutInflater.from(parent.context).inflate(R.layout.account_caldav_item, parent, false)
                )

        override fun getItemCount() = data().value?.size ?: 0

        override fun onBindViewHolder(holder: CalendarViewHolder, position: Int) {
            val info = data().value?.get(position) ?: return
            val v = holder.itemView

            val enabled = info.sync || info.supportsVEVENT != false || info.supportsVTODO != false
            if (enabled)
                v.setOnClickListener {
                    onClickListener(it, position, info)
                }
            else
                v.setOnClickListener(null)
            v.findViewById<CheckBox>(R.id.checked).apply {
                isEnabled = enabled
                isChecked = info.sync
            }

            v.findViewById<View>(R.id.color).apply {
                if (info.color != null) {
                    setBackgroundColor(info.color!!)
                    visibility = View.VISIBLE
                } else
                    visibility = View.INVISIBLE
            }

            v.findViewById<TextView>(R.id.title).text =
                    if (!info.displayName.isNullOrBlank()) info.displayName else info.url.toString()

            v.findViewById<TextView>(R.id.description).apply {
                if (info.description.isNullOrBlank())
                    visibility = View.GONE
                else {
                    text = info.description
                    visibility = View.VISIBLE
                }
            }

            v.findViewById<View>(R.id.read_only).visibility =
                    if (!info.privWriteContent || info.forceReadOnly) View.VISIBLE else View.GONE

            v.findViewById<ImageView>(R.id.events).visibility =
                    if (info.supportsVEVENT != false) View.VISIBLE else View.GONE

            v.findViewById<ImageView>(R.id.tasks).visibility =
                    if (info.supportsVTODO != false) View.VISIBLE else View.GONE

            val overflow = v.findViewById<ImageView>(R.id.action_overflow)
            if (info.type == Collection.TYPE_WEBCAL)
                overflow.visibility = View.INVISIBLE
            else {
                overflow.setOnClickListener { view ->
                    activity.onActionOverflowListener(view, info, this@CalendarAdapter, position)
                }
            }
        }

        open fun onClickListener(parent: View, position: Int, info: Collection) {
            model.updateCollectionSelected(info, !info.sync)
        }

    }

    class WebcalAdapter(
            activity: AccountActivity,
            model: Model
    ): CalendarAdapter(activity, model) {

        override fun data() = model.webcals

        override fun onClickListener(parent: View, position: Int, info: Collection) {
            val nowChecked = !info.sync
            var uri = Uri.parse(info.source)

            if (nowChecked) {
                // subscribe to Webcal feed
                when {
                    uri.scheme.equals("http", true) -> uri = uri.buildUpon().scheme("webcal").build()
                    uri.scheme.equals("https", true) -> uri = uri.buildUpon().scheme("webcals").build()
                }

                val intent = Intent(Intent.ACTION_VIEW, uri)
                info.displayName?.let { intent.putExtra("title", it) }
                info.color?.let { intent.putExtra("color", it) }

                if (activity.packageManager.resolveActivity(intent, 0) != null)
                    activity.startActivity(intent)
                else {
                    val snack = Snackbar.make(parent, R.string.account_no_webcal_handler_found, Snackbar.LENGTH_LONG)

                    val installIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=at.bitfire.icsdroid"))
                    if (activity.packageManager.resolveActivity(installIntent, 0) != null)
                        snack.setAction(R.string.account_install_icsx5) {
                            activity.startActivityForResult(installIntent, REQUEST_CODE_PERMISSIONS_UPDATED)
                        }

                    snack.show()
                }
            } else {
                // unsubscribe from Webcal feed
                model.unsubscribeWebcal(info)
            }
        }

    }


    /* USER ACTIONS */

    private fun deleteAccount() {
        val accountManager = AccountManager.get(this)

        if (Build.VERSION.SDK_INT >= 22)
            accountManager.removeAccount(model.account, this, { future ->
                try {
                    if (future.result.getBoolean(AccountManager.KEY_BOOLEAN_RESULT))
                        Handler(Looper.getMainLooper()).post {
                            finish()
                        }
                } catch(e: Exception) {
                    Logger.log.log(Level.SEVERE, "Couldn't remove account", e)
                }
            }, null)
        else
            accountManager.removeAccount(model.account, { future ->
                try {
                    if (future.result)
                        Handler(Looper.getMainLooper()).post {
                            finish()
                        }
                } catch (e: Exception) {
                    Logger.log.log(Level.SEVERE, "Couldn't remove account", e)
                }
            }, null)
    }

    private fun requestSync() {
        DavUtils.requestSync(this, model.account)
        Snackbar.make(binding.root, R.string.account_synchronizing_now, Snackbar.LENGTH_LONG).show()
    }


    /* MODEL */

    class Model(
            val context: Application
    ): AndroidViewModel(context), SyncStatusObserver, DavService.RefreshingStatusListener {

        lateinit var account: Account
        val accountName = MutableLiveData<String>()

        val subscribedWebcals = MutableLiveData<Set<String>>()
        var calendarProvider: ContentProviderClient? = null
        var calendarObserver: ContentObserver? = null

        val database = AppDatabase.getInstance(context)
        val services: LiveData<List<Service>> = Transformations.switchMap(accountName) { name ->
            database.serviceDao().observeByAccount(name)
        }

        private fun transformServiceToHasHomesets(liveServiceId: LiveData<Long>): LiveData<Boolean> =
                Transformations.switchMap(liveServiceId) { serviceId ->
                    serviceId?.let {
                        Transformations.map(database.homeSetDao().observeByService(serviceId)) {
                            it.isNotEmpty()
                        }
                    }
                }

        private fun transformServiceToCollections(liveServiceId: LiveData<Long>, type: String): LiveData<List<Collection>> =
                Transformations.switchMap(liveServiceId) { serviceId ->
                    serviceId?.let {
                        database.collectionDao().observeByServiceAndType(serviceId, type)
                    }
                }

        val cardDavServiceId: LiveData<Long> = Transformations.map(services) { services ->
            services.firstOrNull { it.type == Service.TYPE_CARDDAV }?.id
        }

        val hasAddressBookHomeSets: LiveData<Boolean> = transformServiceToHasHomesets(cardDavServiceId)
        val addressBooks = transformServiceToCollections(cardDavServiceId, Collection.TYPE_ADDRESSBOOK)
        val askForContactsPermissions = ContactsPermissionsCalculator(context, addressBooks)

        val calDavServiceId: LiveData<Long> = Transformations.map(services) { services ->
            services.firstOrNull { it.type == Service.TYPE_CALDAV }?.id
        }
        val hasCalendarHomeSets = transformServiceToHasHomesets(calDavServiceId)
        val calendars = transformServiceToCollections(calDavServiceId, Collection.TYPE_CALENDAR)
        val webcals = WebcalSource(
                subscribedWebcals,
                transformServiceToCollections(calDavServiceId, Collection.TYPE_WEBCAL)
        )
        val askForCalendarPermissions = CalendarPermissionsCalculator(context, calDavServiceId)

        var syncStatusListener: Any? = null
        val cardDavRefreshing = MutableLiveData<Boolean>()
        val calDavRefreshing = MutableLiveData<Boolean>()

        @Volatile
        private var davService: DavService.InfoBinder? = null
        private var davServiceConn: ServiceConnection? = null

        private val executor = Executors.newSingleThreadExecutor()


        @MainThread
        fun initialize(account: Account) {
            if (accountName.value != null)
                return
            this.account = account
            accountName.value = account.name

            thread {
                initializeAsync()
            }
        }

        @WorkerThread
        private fun initializeAsync() {
            if (checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED) {
                calendarProvider = context.contentResolver.acquireContentProviderClient(CalendarContract.AUTHORITY)

                object: ContentObserver(null) {
                    override fun onChange(selfChange: Boolean) {
                        calendarProvider?.query(CalendarContract.Calendars.CONTENT_URI, arrayOf(CalendarContract.Calendars.NAME),
                                "${CalendarContract.Calendars.NAME} LIKE 'http%'", null, null)?.use { cursor ->
                            val result = mutableSetOf<String>()
                            while (cursor.moveToNext())
                                result += cursor.getString(0)
                            subscribedWebcals.postValue(result)
                        }
                    }
                }.let { observer ->
                    calendarObserver = observer
                    context.contentResolver.registerContentObserver(CalendarContract.Calendars.CONTENT_URI, false, observer)
                    observer.onChange(true)
                }

            }

            // observe Account sync status
            syncStatusListener = ContentResolver.addStatusChangeListener(ContentResolver.SYNC_OBSERVER_TYPE_ACTIVE, this)

            // observe DavService status
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
            if (context.bindService(Intent(context, DavService::class.java), svcConn, Context.BIND_AUTO_CREATE))
                davServiceConn = svcConn

            updateRefreshing()
        }

        override fun onCleared() {
            calendarObserver?.let { context.contentResolver.unregisterContentObserver(it) }
            calendarProvider?.closeCompat()

            davServiceConn?.let { context.unbindService(it) }
            ContentResolver.removeStatusChangeListener(syncStatusListener)
        }

        fun onPermissionsUpdated() {
            askForContactsPermissions.recalculate()
            askForCalendarPermissions.recalculate()
        }

        override fun onStatusChanged(which: Int) {
            updateRefreshing()
        }

        override fun onDavRefreshStatusChanged(id: Long, refreshing: Boolean) {
            if (id == cardDavServiceId.value || id == calDavServiceId.value)
                updateRefreshing()
        }

        private fun updateRefreshing() {
            // address books authority for current account syncing?
            var contactsSyncActive = ContentResolver.isSyncActive(account, context.getString(R.string.address_books_authority))
            // any contacts provider authority for address book accounts of current main account syncing?
            val accountManager = AccountManager.get(context)
            for (addrBookAccount in accountManager.getAccountsByType(context.getString(R.string.account_type_address_book))) {
                val addressBook = LocalAddressBook(context, addrBookAccount, null)
                try {
                    if (account == addressBook.mainAccount)
                        if (ContentResolver.isSyncActive(addrBookAccount, ContactsContract.AUTHORITY))
                            contactsSyncActive = true
                } catch (e: Exception) {
                }
            }
            val svcCardDavRefreshing = cardDavServiceId.value?.let { id -> davService?.isRefreshing(id) } ?: false
            cardDavRefreshing.postValue(contactsSyncActive || svcCardDavRefreshing)

            val calendarSyncActive = ContentResolver.isSyncActive(account, context.getString(R.string.address_books_authority))
            val tasksSyncActive = ContentResolver.isSyncActive(account, TaskProvider.ProviderName.OpenTasks.authority)
            val svcCalDavRefreshing = calDavServiceId.value?.let { id -> davService?.isRefreshing(id) } ?: false
            calDavRefreshing.postValue(calendarSyncActive || tasksSyncActive || svcCalDavRefreshing)
        }


        fun unsubscribeWebcal(collection: Collection) =
                calendarProvider?.delete(CalendarContract.Calendars.CONTENT_URI, "${CalendarContract.Calendars.NAME}=?", arrayOf(collection.source))

        fun updateCollectionSelected(info: Collection, selected: Boolean) {
            executor.submit {
                info.sync = selected
                AppDatabase.getInstance(context).collectionDao().update(info)
            }
        }

        fun updateCollectionReadOnly(info: Collection, readOnly: Boolean) {
            executor.submit {
                info.forceReadOnly = readOnly
                AppDatabase.getInstance(context).collectionDao().update(info)
            }
        }

    }

    class ContactsPermissionsCalculator(
            private val context: Context,
            private val addressBooks: LiveData<List<Collection>>
    ): MediatorLiveData<Boolean>() {

        companion object {
            val permissions = arrayOf(
                    Manifest.permission.READ_CONTACTS,
                    Manifest.permission.WRITE_CONTACTS
            )
        }

        init {
            addSource(addressBooks) {
                recalculate(it)
            }
        }

        fun recalculate() = addressBooks.value?.let { recalculate(it) }

        private fun recalculate(addressbooks: List<Collection>) {
            // permissions required?
            val required = addressbooks.any { it.sync }

            val ask = if (required)
                // if permissions required: any (not yet) granted permission?
                permissions.any { ActivityCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
            else
                false

            if (value != ask)
                value = ask
        }

    }

    class CalendarPermissionsCalculator(
            private val context: Context,
            private val serviceId: LiveData<Long>
    ): MediatorLiveData<List<String>>() {

        companion object {
            val calendarPermissions = arrayOf(
                Manifest.permission.READ_CALENDAR,
                Manifest.permission.WRITE_CALENDAR
            )
            val taskPermissions = arrayOf(
                    TaskProvider.PERMISSION_READ_TASKS,
                    TaskProvider.PERMISSION_WRITE_TASKS
            )
        }

        init {
            addSource(serviceId) {
                recalculate()
            }
        }

        fun recalculate() {
            val permissions = mutableListOf<String>()

            // As soon as there is a CalDAV service, we need calendar (and task) permissions
            permissions.addAll(calendarPermissions)
            if (LocalTaskList.tasksProviderAvailable(context))
                permissions.addAll(taskPermissions)

            // only ask for permissions which are not granted yet
            val ask = permissions.filter { ActivityCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }

            if (value != ask)
                value = ask
        }

    }

    class WebcalSource(
            subscribedWebcals: LiveData<Set<String>>,
            webcals: LiveData<List<Collection>>
    ): MediatorLiveData<List<Collection>>() {

        private var activeSubscriptions: Set<String>? = null
        private var unprocessedWebcals: List<Collection>? = null

        init {
            addSource(webcals) {
                unprocessedWebcals = it
                processWebcals()
            }
            addSource(subscribedWebcals) {
                activeSubscriptions = it
                processWebcals()
            }
        }

        private fun processWebcals() {
            val result = unprocessedWebcals?.map { webcal ->
                webcal.sync = activeSubscriptions?.contains(webcal.source.toString()) ?: false
                webcal
            } ?: return
            value = result
        }

    }

}
