package at.bitfire.davdroid.ui.account

import android.Manifest
import android.accounts.Account
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Menu
import androidx.annotation.MainThread
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import androidx.lifecycle.*
import at.bitfire.davdroid.DavUtils
import at.bitfire.davdroid.R
import at.bitfire.davdroid.model.AppDatabase
import at.bitfire.davdroid.model.Collection
import at.bitfire.davdroid.model.Service
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.activity_account2.*
import java.util.concurrent.Executors
import kotlin.concurrent.thread

class AccountActivity2 : AppCompatActivity() {

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

        setContentView(R.layout.activity_account2)
        model.cardDavService.observe(this, Observer { cardDavService ->
            tab_layout.setupWithViewPager(view_pager)
            view_pager.adapter = TabsAdapter(supportFragmentManager,
                    cardDavService, model.calDavService.value)
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

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (grantResults.contains(PackageManager.PERMISSION_GRANTED))
            model.gotPermission()
    }

    class TabsAdapter(
            fm: FragmentManager,
            private val cardDavService: Long?,
            private val calDavService: Long?
    ): FragmentPagerAdapter(fm) {

        private var idxContacts: Int? = null
        private var idxEventsTasks: Int? = null
        private var idxWebcals: Int? = null

        init {
            var currentIndex = 0
            if (cardDavService != null)
                idxContacts = currentIndex++
            if (calDavService != null) {
                idxEventsTasks = currentIndex++
                idxWebcals = currentIndex
            }
        }

        override fun getCount() =
                (if (idxContacts != null) 1 else 0) +
                (if (idxEventsTasks != null) 1 else 0) +
                (if (idxWebcals != null) 1 else 0)

        override fun getItem(position: Int): Fragment {
            when (position) {
                idxContacts -> {
                    val frag = AddressBooksFragment()
                    val args = Bundle(1)
                    args.putLong(CollectionsFragment.EXTRA_SERVICE_ID, cardDavService!!)
                    frag.arguments = args
                    return frag
                }
                idxEventsTasks -> {
                    val frag = CalendarsFragment()
                    val args = Bundle(1)
                    args.putLong(CollectionsFragment.EXTRA_SERVICE_ID, calDavService!!)
                    frag.arguments = args
                    return frag
                }
                idxWebcals ->
                    return Fragment()
            }
            throw IllegalArgumentException()
        }

        override fun getPageTitle(position: Int) =
                when (position) {
                    idxContacts -> "Address books"
                    idxEventsTasks -> "Calendars"
                    idxWebcals -> "Webcal"
                    else -> throw IllegalArgumentException()
                }
    }


    class Model(application: Application): AndroidViewModel(application) {

        private var initialized = false
        lateinit var account: Account
            private set

        private val db = AppDatabase.getInstance(application)
        private val executor = Executors.newSingleThreadExecutor()

        var calDavService = MutableLiveData<Long>()
        val cardDavService = MutableLiveData<Long>()

        val hasActiveAddressBooks: LiveData<Boolean> = Transformations.switchMap(cardDavService) { cardDavId ->
            db.collectionDao().observeHasSyncByService(cardDavId)
        }
        val askForPermissions = PermissionCalculator(application, calDavService, hasActiveAddressBooks)


        @MainThread
        fun initialize(account: Account) {
            if (initialized)
                return
            initialized = true

            this.account = account

            thread {
                calDavService.postValue(db.serviceDao().getIdByAccountAndType(account.name, Service.TYPE_CARDDAV))
                cardDavService.postValue(db.serviceDao().getIdByAccountAndType(account.name, Service.TYPE_CARDDAV))
            }
        }

        fun gotPermission() {
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
            calDavService: LiveData<Long>,
            hasActiveAddressBooks: LiveData<Boolean>
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
        }

        var needCalendarAccess: Boolean? = null
        var needContactsAccess: Boolean? = null

        init {
            addSource(calDavService, Observer {
                needCalendarAccess = it != null
                calculate()
            })
            addSource(hasActiveAddressBooks, Observer {
                needContactsAccess = it
                calculate()
            })
        }

        fun calculate() {
            val calendar = needCalendarAccess ?: return
            val contacts = needContactsAccess ?: return

            val required = mutableListOf<String>()
            if (contacts)
                required.addAll(contactPermissions)
            if (calendar)
                required.addAll(calendarPermissions)

            val askFor = required.filter { permission ->
                ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_DENIED
            }
            postValue(askFor)
        }

    }

}
