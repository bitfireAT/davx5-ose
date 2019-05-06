package at.bitfire.davdroid.ui.account

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.ContentProviderClient
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.WorkerThread
import androidx.core.content.ContextCompat
import androidx.lifecycle.*
import androidx.room.Transaction
import at.bitfire.dav4jvm.UrlUtils
import at.bitfire.davdroid.Constants
import at.bitfire.davdroid.R
import at.bitfire.davdroid.closeCompat
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.model.AppDatabase
import at.bitfire.davdroid.model.Collection
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.account_caldav_item.view.*
import okhttp3.HttpUrl
import java.util.logging.Level

class WebcalFragment: CollectionsFragment() {

    override val noCollectionsStringId = R.string.account_no_webcals

    lateinit var webcalModel: WebcalModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webcalModel = ViewModelProviders.of(this).get(WebcalModel::class.java)
        webcalModel.calendarPermission.observe(this, Observer { granted ->
            if (!granted)
                requestPermissions(arrayOf(Manifest.permission.READ_CALENDAR), 0)
        })
        webcalModel.subscribedUrls.observe(this, Observer { urls ->
            Logger.log.log(Level.FINE, "Got Android calendar list", urls.keys)
        })

        webcalModel.initialize(arguments?.getLong(EXTRA_SERVICE_ID) ?: throw IllegalArgumentException("EXTRA_SERVICE_ID required"))
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) =
            webcalModel.calendarPermission.check()

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) =
            inflater.inflate(R.menu.caldav_actions, menu)

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        menu.findItem(R.id.create).isVisible = false
    }


    override fun createAdapter(): CollectionAdapter = WebcalAdapter(accountModel, webcalModel)


    class CalendarViewHolder(
            private val parent: ViewGroup,
            accountModel: AccountActivity.Model,
            private val webcalModel: WebcalModel
    ): CollectionViewHolder(parent, R.layout.account_caldav_item, accountModel) {

        override fun bindTo(item: Collection) {
            val v = itemView
            v.color.setBackgroundColor(item.color ?: Constants.DAVDROID_GREEN_RGBA)

            v.sync.isChecked = item.sync
            v.title.text = item.title()

            if (item.description.isNullOrBlank())
                v.description.visibility = View.GONE
            else {
                v.description.text = item.description
                v.description.visibility = View.VISIBLE
            }

            v.read_only.visibility = View.VISIBLE
            v.events.visibility = if (item.supportsVEVENT == true) View.VISIBLE else View.GONE
            v.tasks.visibility = if (item.supportsVTODO == true) View.VISIBLE else View.GONE

            itemView.setOnClickListener {
                if (item.sync)
                    webcalModel.unsubscribe(item)
                else
                    subscribe(item)
            }
            v.action_overflow.setOnClickListener(CollectionPopupListener(accountModel, item))
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

        val calendarPermission = CalendarPermission(application)
        private val calendarProvider = object: MediatorLiveData<ContentProviderClient>() {
            var havePermission = false

            init {
                addSource(calendarPermission) { granted ->
                    havePermission = granted
                    if (granted)
                        connect()
                    else
                        disconnect()
                }
            }

            override fun onActive() {
                super.onActive()
                connect()
            }

            fun connect() {
                if (havePermission && value == null)
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
        val subscribedUrls = object: MediatorLiveData<MutableMap<HttpUrl, Long>>() {
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
                val subscriptions = mutableMapOf<HttpUrl, Long>()
                provider.query(Calendars.CONTENT_URI, arrayOf(Calendars._ID, Calendars.NAME),null, null, null)?.use { cursor ->
                    while (cursor.moveToNext())
                        HttpUrl.parse(cursor.getString(1))?.let { url ->
                            subscriptions[url] = cursor.getLong(0)
                        }
                }

                // update "sync" field in database accordingly (will update UI)
                db.collectionDao().getByServiceAndType(serviceId, Collection.TYPE_WEBCAL).forEach { webcal ->
                    val newSync = subscriptions.keys
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
            calendarPermission.check()
        }

        fun unsubscribe(webcal: Collection) {
            workerHandler.post {
                subscribedUrls.value?.get(webcal.source)?.let { id ->
                    // delete subscription from Android calendar list
                    calendarProvider.value?.delete(Calendars.CONTENT_URI,
                            "${Calendars._ID}=?", arrayOf(id.toString()))
                }
            }
        }

    }

    class CalendarPermission(val context: Context): LiveData<Boolean>() {
        init {
            check()
        }

        fun check() {
            value = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
        }
    }

}