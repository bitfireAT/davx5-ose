package at.bitfire.davdroid.ui.account

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.ContentProviderClient
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.view.*
import androidx.annotation.WorkerThread
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MediatorLiveData
import androidx.room.Transaction
import at.bitfire.dav4jvm.UrlUtils
import at.bitfire.davdroid.Constants
import at.bitfire.davdroid.PermissionUtils
import at.bitfire.davdroid.R
import at.bitfire.davdroid.closeCompat
import at.bitfire.davdroid.databinding.AccountCaldavItemBinding
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.model.AppDatabase
import at.bitfire.davdroid.model.Collection
import com.google.android.material.snackbar.Snackbar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.logging.Level
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set

class WebcalFragment: CollectionsFragment() {

    override val noCollectionsStringId = R.string.account_no_webcals

    val webcalModel by viewModels<WebcalModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webcalModel.subscribedUrls.observe(this, { urls ->
            Logger.log.log(Level.FINE, "Got Android calendar list", urls.keys)
        })

        webcalModel.initialize(arguments?.getLong(EXTRA_SERVICE_ID) ?: throw IllegalArgumentException("EXTRA_SERVICE_ID required"))
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) =
            inflater.inflate(R.menu.caldav_actions, menu)

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        menu.findItem(R.id.create_calendar).isVisible = false
    }


    override fun checkPermissions() {
        if (PermissionUtils.havePermissions(requireActivity(), PermissionUtils.CALENDAR_PERMISSIONS))
            binding.permissionsCard.visibility = View.GONE
        else {
            binding.permissionsText.setText(R.string.account_webcal_missing_calendar_permissions)
            binding.permissionsCard.visibility = View.VISIBLE
        }
    }

    override fun createAdapter(): CollectionAdapter = WebcalAdapter(accountModel, webcalModel)


    class CalendarViewHolder(
            private val parent: ViewGroup,
            accountModel: AccountActivity.Model,
            private val webcalModel: WebcalModel
    ): CollectionViewHolder<AccountCaldavItemBinding>(parent, AccountCaldavItemBinding.inflate(LayoutInflater.from(parent.context), parent, false), accountModel) {

        override fun bindTo(item: Collection) {
            binding.color.setBackgroundColor(item.color ?: Constants.DAVDROID_GREEN_RGBA)

            binding.sync.isChecked = item.sync
            binding.title.text = item.title()

            if (item.description.isNullOrBlank())
                binding.description.visibility = View.GONE
            else {
                binding.description.text = item.description
                binding.description.visibility = View.VISIBLE
            }

            binding.readOnly.visibility = View.VISIBLE
            binding.events.visibility = if (item.supportsVEVENT == true) View.VISIBLE else View.GONE
            binding.tasks.visibility = if (item.supportsVTODO == true) View.VISIBLE else View.GONE

            itemView.setOnClickListener {
                if (item.sync)
                    webcalModel.unsubscribe(item)
                else
                    subscribe(item)
            }
            binding.actionOverflow.setOnClickListener(CollectionPopupListener(accountModel, item))
        }

        private fun subscribe(item: Collection) {
            var uri = Uri.parse(item.source.toString())
            when {
                uri.scheme.equals("http", true) -> uri = uri.buildUpon().scheme("webcal").build()
                uri.scheme.equals("https", true) -> uri = uri.buildUpon().scheme("webcals").build()
            }

            val intent = Intent(Intent.ACTION_VIEW, uri)
            item.displayName?.let { intent.putExtra("title", it) }
            item.color?.let { intent.putExtra("color", it) }

            Logger.log.info("Intent: ${intent.extras}")

            val activity = parent.context as Activity
            if (activity.packageManager.resolveActivity(intent, 0) != null)
                activity.startActivity(intent)
            else {
                val snack = Snackbar.make(parent, R.string.account_no_webcal_handler_found, Snackbar.LENGTH_LONG)

                val installIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=at.bitfire.icsdroid"))
                if (activity.packageManager.resolveActivity(installIntent, 0) != null)
                    snack.setAction(R.string.account_install_icsx5) {
                        activity.startActivityForResult(installIntent, 0)
                    }

                snack.show()
            }

        }

    }

    class WebcalAdapter(
            accountModel: AccountActivity.Model,
            private val webcalModel: WebcalModel
    ): CollectionAdapter(accountModel) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
                CalendarViewHolder(parent, accountModel, webcalModel)

    }


    class WebcalModel(application: Application): AndroidViewModel(application) {

        private var initialized = false
        private var serviceId: Long = 0

        private val workerThread = HandlerThread(javaClass.simpleName)
        init { workerThread.start() }
        val workerHandler = Handler(workerThread.looper)

        private val db = AppDatabase.getInstance(application)
        private val resolver = application.contentResolver

        private var calendarPermission = false
        private val calendarProvider = object: MediatorLiveData<ContentProviderClient>() {
            init {
                init()
            }

            fun init() {
                calendarPermission = ContextCompat.checkSelfPermission(getApplication(), Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
                if (calendarPermission)
                    connect()
            }

            override fun onActive() {
                super.onActive()
                connect()
            }

            fun connect() {
                if (calendarPermission && value == null)
                    value = resolver.acquireContentProviderClient(CalendarContract.AUTHORITY)
            }

            override fun onInactive() {
                super.onInactive()
                disconnect()
            }

            fun disconnect() {
                value?.closeCompat()
                value = null
            }
        }
        val subscribedUrls = object: MediatorLiveData<MutableMap<Long, HttpUrl>>() {
            var provider: ContentProviderClient? = null
            var observer: ContentObserver? = null

            init {
                addSource(calendarProvider) { provider ->
                    this.provider = provider
                    if (provider != null) {
                        connect()
                    } else
                        unregisterObserver()
                }
            }

            override fun onActive() {
                super.onActive()
                connect()
            }

            private fun connect() {
                unregisterObserver()
                provider?.let { provider ->
                    val newObserver = object: ContentObserver(workerHandler) {
                        override fun onChange(selfChange: Boolean) {
                            queryCalendars(provider)
                        }
                    }
                    getApplication<Application>().contentResolver.registerContentObserver(Calendars.CONTENT_URI, false, newObserver)
                    observer = newObserver

                    workerHandler.post {
                        queryCalendars(provider)
                    }
                }
            }

            override fun onInactive() {
                super.onInactive()
                unregisterObserver()
            }

            private fun unregisterObserver() {
                observer?.let {
                    application.contentResolver.unregisterContentObserver(it)
                    observer = null
                }
            }

            @WorkerThread
            @Transaction
            private fun queryCalendars(provider: ContentProviderClient) {
                // query subscribed URLs from Android calendar list
                val subscriptions = mutableMapOf<Long, HttpUrl>()
                provider.query(Calendars.CONTENT_URI, arrayOf(Calendars._ID, Calendars.NAME),null, null, null)?.use { cursor ->
                    while (cursor.moveToNext())
                        cursor.getString(1)?.let { rawName ->
                            rawName.toHttpUrlOrNull()?.let { url ->
                                subscriptions[cursor.getLong(0)] = url
                            }
                        }
                }

                // update "sync" field in database accordingly (will update UI)
                db.collectionDao().getByServiceAndType(serviceId, Collection.TYPE_WEBCAL).forEach { webcal ->
                    val newSync = subscriptions.values
                            .any { webcal.source?.let { source -> UrlUtils.equals(source, it) } ?: false }
                    if (newSync != webcal.sync)
                        db.collectionDao().update(webcal.copy(sync = newSync))
                }

                postValue(subscriptions)
            }
        }


        fun initialize(dbServiceId: Long) {
            if (initialized)
                return
            initialized = true

            serviceId = dbServiceId
        }

        fun unsubscribe(webcal: Collection) {
            workerHandler.post {
                // find first matching source (Webcal) URL
                subscribedUrls.value?.entries?.firstOrNull { (_, source) ->
                    UrlUtils.equals(source, webcal.source!!)
                }?.key?.let { id ->
                    // delete first matching subscription from Android calendar list
                    calendarProvider.value?.delete(Calendars.CONTENT_URI,
                            "${Calendars._ID}=?", arrayOf(id.toString()))
                }
            }
        }

    }

}