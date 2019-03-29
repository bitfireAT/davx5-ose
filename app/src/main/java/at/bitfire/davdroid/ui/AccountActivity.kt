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
import android.net.Uri
import android.os.*
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.Settings
import android.view.*
import android.widget.*
import androidx.annotation.MainThread
import androidx.annotation.UiThread
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import at.bitfire.davdroid.*
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

class AccountActivity: AppCompatActivity(), Toolbar.OnMenuItemClickListener, PopupMenu.OnMenuItemClickListener {

    companion object {
        const val EXTRA_ACCOUNT = "account"

        const val REQUEST_CODE_RELOAD = 0
    }

    private lateinit var model: Model
    private lateinit var binding: ActivityAccountBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        model = ViewModelProviders.of(this).get(Model::class.java)

        binding = DataBindingUtil.setContentView<ActivityAccountBinding>(this, R.layout.activity_account)
        binding.lifecycleOwner = this
        binding.model = model

        // account may be a DAVx5 address book account -> use main account in this case
        model.initialize(LocalAddressBook.mainAccount(this,
                requireNotNull(intent.getParcelableExtra(EXTRA_ACCOUNT))))
        title = model.account.name

        val icMenu = AppCompatResources.getDrawable(this, R.drawable.ic_menu_light)

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

        model.requiredPermissions.observe(this, Observer { permissions ->
            val askPermissions = permissions.filter { ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
            if (askPermissions.isNotEmpty())
                ActivityCompat.requestPermissions(this, askPermissions.toTypedArray(), 0)
        })
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (grantResults.any { it == PackageManager.PERMISSION_GRANTED })
            // we've got additional permissions; load everything again
            // (especially Webcal subscriptions, whose status could not be determined without calendar permission)
            reload()
        else if (grantResults.any { it == PackageManager.PERMISSION_DENIED }) {
            if (permissions.map { ActivityCompat.shouldShowRequestPermissionRationale(this, it) }.any())
                Snackbar
                    .make(binding.root, R.string.account_missing_permissions, Snackbar.LENGTH_LONG)
                    .setAction(R.string.account_missing_permissions_fix) {
                        Toast.makeText(this, R.string.account_missing_permissions_explanation, Toast.LENGTH_LONG)
                                .show()
                        val settingsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", BuildConfig.APPLICATION_ID, null))
                        startActivityForResult(settingsIntent, REQUEST_CODE_RELOAD)
                    }
                    .show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_RELOAD)
            reload()
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
                    info.uiEnabled = false
                    adapter.notifyItemChanged(position)

                    val nowForceReadOnly = !info.forceReadOnly
                    model.updateCollectionReadOnly(info, nowForceReadOnly) {
                        info.forceReadOnly = nowForceReadOnly
                        info.uiEnabled = true
                        adapter.notifyItemChanged(position)
                    }
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

    fun reload() {
        model.reload()
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

            if (info.uiEnabled)
                v.setOnClickListener {
                    info.uiEnabled = false
                    notifyItemChanged(position)

                    val nowChecked = !info.sync
                    model.updateCollectionSelected(info, nowChecked) {
                        info.sync = nowChecked
                        v.findViewById<CheckBox>(R.id.checked).isChecked = nowChecked

                        info.uiEnabled = true
                        notifyItemChanged(position)
                    }
                }
            else
                v.setOnClickListener(null)

            v.findViewById<CheckBox>(R.id.checked).apply {
                isEnabled = info.uiEnabled
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
                if (info.uiEnabled)
                    setOnClickListener { view ->
                        activity.onActionOverflowListener(view, info, this@AddressBookAdapter, position)
                    }
                else
                    setOnClickListener(null)
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

            val enabled = (info.sync || info.supportsVEVENT != false || info.supportsVTODO != false) && info.uiEnabled
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
                if (info.uiEnabled)
                    overflow.setOnClickListener { view ->
                        activity.onActionOverflowListener(view, info, this@CalendarAdapter, position)
                    }
                else
                    overflow.setOnClickListener(null)
            }
        }

        open fun onClickListener(parent: View, position: Int, info: Collection)
        {
            info.uiEnabled = false
            notifyItemChanged(position)

            val nowChecked = !info.sync
            model.updateCollectionSelected(info, nowChecked) {
                info.sync = nowChecked
                parent.findViewById<CheckBox>(R.id.checked).isChecked = nowChecked

                info.uiEnabled = true
                notifyItemChanged(position)
            }
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
                            activity.startActivityForResult(installIntent, REQUEST_CODE_RELOAD)
                        }

                    snack.show()
                }
            } else {
                // unsubscribe from Webcal feed
                if (ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED)
                    activity.contentResolver.acquireContentProviderClient(CalendarContract.AUTHORITY)?.let { provider ->
                        try {
                            provider.delete(CalendarContract.Calendars.CONTENT_URI, "${CalendarContract.Calendars.NAME}=?", arrayOf(info.source))
                            activity.reload()
                        } finally {
                            provider.closeCompat()
                        }
                    }

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

        private var initialized = false
        lateinit var account: Account

        val cardDavServiceId = MutableLiveData<Long>()
        val cardDavRefreshing = MutableLiveData<Boolean>()
        val addressBooks = MutableLiveData<List<Collection>>()
        val hasAddressBookHomeSets = MutableLiveData<Boolean>()

        val calDavServiceId = MutableLiveData<Long>()
        val calDavRefreshing = MutableLiveData<Boolean>()
        val calendars = MutableLiveData<List<Collection>>()
        val hasCalendarHomeSets = MutableLiveData<Boolean>()

        val webcals = MutableLiveData<List<Collection>>()

        val requiredPermissions = MutableLiveData<List<String>>()

        var syncStatusListener: Any? = null

        @Volatile
        private var davService: DavService.InfoBinder? = null
        private var davServiceConn: ServiceConnection? = null

        private val executor = Executors.newSingleThreadExecutor()

        @MainThread
        fun initialize(account: Account) {
            if (initialized)
                return
            this.account = account

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

            reload()
            updateRefreshing()
        }

        override fun onCleared() {
            davServiceConn?.let { context.unbindService(it) }
            ContentResolver.removeStatusChangeListener(syncStatusListener)
        }

        fun reload() {
            executor.submit {
                val neededPermissions = mutableListOf<String>()

                val db = AppDatabase.getInstance(context)
                val collectionDao = db.collectionDao()
                for (service in db.serviceDao().getByAccount(account.name)) {
                    when (service.type) {
                        Service.TYPE_CARDDAV -> {
                            cardDavServiceId.postValue(service.id)
                            val collections = collectionDao.getByService(service.id)
                            if (collections.isNotEmpty())
                                neededPermissions.addAll(arrayOf(
                                        Manifest.permission.READ_CONTACTS,
                                        Manifest.permission.WRITE_CONTACTS
                                ))
                            addressBooks.postValue(collections)
                        }
                        Service.TYPE_CALDAV -> {
                            calDavServiceId.postValue(service.id)
                            val collections = collectionDao.getByService(service.id)
                            if (collections.isNotEmpty()) {
                                neededPermissions.addAll(arrayOf(
                                        Manifest.permission.READ_CALENDAR,
                                        Manifest.permission.WRITE_CALENDAR
                                ))
                                if (LocalTaskList.tasksProviderAvailable(context)) {
                                    neededPermissions.addAll(arrayOf(
                                            TaskProvider.PERMISSION_READ_TASKS,
                                            TaskProvider.PERMISSION_WRITE_TASKS
                                    ))
                                }

                                calendars.postValue(collections.filter { it.type == Collection.TYPE_CALENDAR })

                                val webcalCollections = collections.filter { it.type == Collection.TYPE_WEBCAL }
                                if (webcalCollections.isNotEmpty() && ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED)
                                    context.contentResolver.acquireContentProviderClient(CalendarContract.AUTHORITY)?.let { provider ->
                                        try {
                                            for (info in webcalCollections) {
                                                provider.query(CalendarContract.Calendars.CONTENT_URI, null,
                                                        "${CalendarContract.Calendars.NAME}=?", arrayOf(info.source), null)?.use { cursor ->
                                                    if (cursor.moveToNext())
                                                        info.sync = true
                                                }
                                            }
                                        } finally {
                                            provider.closeCompat()
                                        }
                                    }
                                webcals.postValue(webcalCollections)
                            }
                        }
                    }
                }
                requiredPermissions.postValue(neededPermissions)
            }
        }

        override fun onStatusChanged(which: Int) {
            updateRefreshing()
        }

        override fun onDavRefreshStatusChanged(id: Long, refreshing: Boolean) {
            if (id == cardDavServiceId.value || id == calDavServiceId.value)
                updateRefreshing()
        }

        override fun onDavRefreshFinished(id: Long) {
            if (id == cardDavServiceId.value || id == calDavServiceId.value)
                reload()
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
                } catch(e: Exception) {
                }
            }
            val svcCardDavRefreshing = cardDavServiceId.value?.let { id -> davService?.isRefreshing(id) } ?: false
            cardDavRefreshing.postValue(contactsSyncActive || svcCardDavRefreshing)

            val calendarSyncActive = ContentResolver.isSyncActive(account, context.getString(R.string.address_books_authority))
            val tasksSyncActive = ContentResolver.isSyncActive(account, TaskProvider.ProviderName.OpenTasks.authority)
            val svcCalDavRefreshing = calDavServiceId.value?.let { id -> davService?.isRefreshing(id) } ?: false
            calDavRefreshing.postValue(calendarSyncActive || tasksSyncActive || svcCalDavRefreshing)
        }

        fun updateCollectionSelected(info: Collection, selected: Boolean, @UiThread onSuccess: () -> Unit) {
            executor.submit {
                info.sync = true
                AppDatabase.getInstance(context).collectionDao().update(info)
                Handler(Looper.getMainLooper()).post(onSuccess)
            }
        }

        fun updateCollectionReadOnly(info: Collection, readOnly: Boolean, @UiThread onSuccess: () -> Unit) {
            executor.submit {
                info.forceReadOnly = true
                AppDatabase.getInstance(context).collectionDao().update(info)
                Handler(Looper.getMainLooper()).post(onSuccess)
            }
        }

    }

}
