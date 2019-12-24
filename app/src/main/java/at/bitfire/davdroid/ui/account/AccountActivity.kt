package at.bitfire.davdroid.ui.account

import android.Manifest
import android.accounts.Account
import android.accounts.AccountManager
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import androidx.annotation.MainThread
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentStatePagerAdapter
import androidx.lifecycle.*
import at.bitfire.davdroid.DavUtils
import at.bitfire.davdroid.R
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.model.AppDatabase
import at.bitfire.davdroid.model.Collection
import at.bitfire.davdroid.model.Service
import at.bitfire.davdroid.resource.LocalTaskList
import at.bitfire.ical4android.TaskProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.activity_account.*
import java.util.concurrent.Executors
import java.util.logging.Level
import kotlin.concurrent.thread

class AccountActivity: AppCompatActivity() {

    companion object {
        const val EXTRA_ACCOUNT = "account"
    }

    lateinit var model: Model


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        model = ViewModelProviders.of(this).get(Model::class.java)
        (intent.getParcelableExtra(EXTRA_ACCOUNT) as? Account)?.let { account ->
            model.initialize(account)
        }

        title = model.account.name
        setContentView(R.layout.activity_account)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        tab_layout.setupWithViewPager(view_pager)
        val tabsAdapter = TabsAdapter(this)
        view_pager.adapter = tabsAdapter
        model.cardDavService.observe(this, Observer {
            tabsAdapter.cardDavSvcId = it
        })
        model.calDavService.observe(this, Observer {
            tabsAdapter.calDavSvcId = it
        })

        model.askForPermissions.observe(this, Observer { permissions ->
            if (permissions.isNotEmpty())
                ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 0)
        })

        sync.setOnClickListener {
            DavUtils.requestSync(this, model.account)
            Snackbar.make(view_pager, R.string.account_synchronizing_now, Snackbar.LENGTH_LONG).show()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.activity_account, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val itemRename = menu.findItem(R.id.rename_account)
        // renameAccount is available for API level 21+
        itemRename.isVisible = Build.VERSION.SDK_INT >= 21
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (grantResults.contains(PackageManager.PERMISSION_GRANTED))
            model.gotPermissions()
    }


    // menu actions

    fun openAccountSettings(menuItem: MenuItem) {
        val intent = Intent(this, SettingsActivity::class.java)
        intent.putExtra(SettingsActivity.EXTRA_ACCOUNT, model.account)
        startActivity(intent, null)
    }

    fun renameAccount(menuItem: MenuItem) {
        if (Build.VERSION.SDK_INT >= 21)
            RenameAccountFragment.newInstance(model.account).show(supportFragmentManager, null)
    }

    fun deleteAccount(menuItem: MenuItem) {
        MaterialAlertDialogBuilder(this)
                .setIcon(R.drawable.ic_error_dark)
                .setTitle(R.string.account_delete_confirmation_title)
                .setMessage(R.string.account_delete_confirmation_text)
                .setNegativeButton(android.R.string.no, null)
                .setPositiveButton(android.R.string.yes) { _, _ ->
                    deleteAccount()
                }
                .show()
    }

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


    // adapter

    class TabsAdapter(
            val activity: AppCompatActivity
    ): FragmentStatePagerAdapter(activity.supportFragmentManager, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {
        
        var cardDavSvcId: Long? = null
            set(value) {
                field = value
                recalculate()
            }
        var calDavSvcId: Long? = null
            set(value) {
                field = value
                recalculate()
            }

        private var idxCardDav: Int? = null
        private var idxCalDav: Int? = null
        private var idxWebcal: Int? = null

        private fun recalculate() {
            var currentIndex = 0

            idxCardDav = if (cardDavSvcId != null)
                currentIndex++
            else
                null

            if (calDavSvcId != null) {
                idxCalDav = currentIndex++
                idxWebcal = currentIndex
            } else {
                idxCalDav = null
                idxWebcal = null
            }

            notifyDataSetChanged()
        }

        override fun getCount() =
                (if (idxCardDav != null) 1 else 0) +
                (if (idxCalDav != null) 1 else 0) +
                (if (idxWebcal != null) 1 else 0)

        override fun getItem(position: Int): Fragment {
            val args = Bundle(1)
            when (position) {
                idxCardDav -> {
                    val frag = AddressBooksFragment()
                    args.putLong(CollectionsFragment.EXTRA_SERVICE_ID, cardDavSvcId!!)
                    args.putString(CollectionsFragment.EXTRA_COLLECTION_TYPE, Collection.TYPE_ADDRESSBOOK)
                    frag.arguments = args
                    return frag
                }
                idxCalDav -> {
                    val frag = CalendarsFragment()
                    args.putLong(CollectionsFragment.EXTRA_SERVICE_ID, calDavSvcId!!)
                    args.putString(CollectionsFragment.EXTRA_COLLECTION_TYPE, Collection.TYPE_CALENDAR)
                    frag.arguments = args
                    return frag
                }
                idxWebcal -> {
                    val frag = WebcalFragment()
                    args.putLong(CollectionsFragment.EXTRA_SERVICE_ID, calDavSvcId!!)
                    args.putString(CollectionsFragment.EXTRA_COLLECTION_TYPE, Collection.TYPE_WEBCAL)
                    frag.arguments = args
                    return frag
                }
            }
            throw IllegalArgumentException()
        }

        override fun getPageTitle(position: Int): String =
                when (position) {
                    idxCardDav -> activity.getString(R.string.account_carddav)
                    idxCalDav -> activity.getString(R.string.account_caldav)
                    idxWebcal -> activity.getString(R.string.account_webcal)
                    else -> throw IllegalArgumentException()
                }

    }


    // model

    class Model(application: Application): AndroidViewModel(application) {

        private var initialized = false
        lateinit var account: Account
            private set

        private val db = AppDatabase.getInstance(application)
        private val executor = Executors.newSingleThreadExecutor()

        val cardDavService = MutableLiveData<Long>()
        val calDavService = MutableLiveData<Long>()

        private val needContactPermissions: LiveData<Boolean> = Transformations.switchMap(cardDavService) { cardDavId ->
            if (cardDavId != null)
                db.collectionDao().observeHasSyncByService(cardDavId)
            else
                MutableLiveData<Boolean>().apply { value = false }
        }
        private val needCalendarPermissions: LiveData<Boolean> = Transformations.map(calDavService) { calDavId ->
            calDavId != null
        }
        val askForPermissions = PermissionCalculator(application, needContactPermissions, needCalendarPermissions)


        @MainThread
        fun initialize(account: Account) {
            if (initialized)
                return
            initialized = true

            this.account = account

            thread {
                cardDavService.postValue(db.serviceDao().getIdByAccountAndType(account.name, Service.TYPE_CARDDAV))
                calDavService.postValue(db.serviceDao().getIdByAccountAndType(account.name, Service.TYPE_CALDAV))
            }
        }

        fun gotPermissions() {
            askForPermissions.calculate()
        }

        fun toggleSync(item: Collection) =
                executor.execute {
                    val newItem = item.copy(sync = !item.sync)
                    db.collectionDao().update(newItem)
                }

        fun toggleReadOnly(item: Collection) =
                executor.execute {
                    val newItem = item.copy(forceReadOnly = !item.forceReadOnly)
                    db.collectionDao().update(newItem)
                }

    }

    class PermissionCalculator(
            val context: Context,
            needContactPermissions: LiveData<Boolean>,
            needCalendarPermissions: LiveData<Boolean>
    ): MediatorLiveData<List<String>>() {

        companion object {
            val contactPermissions = arrayOf(
                    Manifest.permission.READ_CONTACTS,
                    Manifest.permission.WRITE_CONTACTS
            )
            val calendarPermissions = arrayOf(
                    Manifest.permission.READ_CALENDAR,
                    Manifest.permission.WRITE_CALENDAR
            )
            val taskPermissions = arrayOf(
                    TaskProvider.PERMISSION_READ_TASKS,
                    TaskProvider.PERMISSION_WRITE_TASKS
            )
        }

        private var usesContacts: Boolean? = null
        private var usesCalendars: Boolean? = null

        init {
            addSource(needContactPermissions) {
                usesContacts = it
                calculate()
            }
            addSource(needCalendarPermissions) {
                usesCalendars = it
                calculate()
            }
        }

        fun calculate() {
            val contacts = usesContacts ?: return
            val calendar = usesCalendars ?: return

            val required = mutableListOf<String>()
            if (contacts)
                required.addAll(contactPermissions)

            if (calendar) {
                required.addAll(calendarPermissions)
                if (LocalTaskList.tasksProviderAvailable(context))
                    required.addAll(taskPermissions)
            }

            // only ask for permissions which are not granted
            val askFor = required.filter {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_DENIED
            }
            if (value != askFor)
                value = askFor
        }

    }

}
