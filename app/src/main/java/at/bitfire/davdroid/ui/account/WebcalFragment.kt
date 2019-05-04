package at.bitfire.davdroid.ui.account

import android.Manifest
import android.app.Activity
import android.app.Application
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
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProviders
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

class WebcalFragment: CollectionsFragment() {

    lateinit var webcalModel: WebcalModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webcalModel = ViewModelProviders.of(this).get(WebcalModel::class.java)
        webcalModel.initialize(arguments?.getLong(EXTRA_SERVICE_ID) ?: throw IllegalArgumentException("EXTRA_SERVICE_ID required"))
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) =
            inflater.inflate(R.menu.caldav_actions, menu)

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        menu.findItem(R.id.create).isVisible = false
    }


    override fun createAdapter(): CollectionAdapter = WebcalAdapter(accountModel, webcalModel)


    class CalendarViewHolder(
            private val parent: ViewGroup,
            accountModel: AccountActivity2.Model,
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
            accountModel: AccountActivity2.Model,
            private val webcalModel: WebcalModel
    ): CollectionAdapter(accountModel) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
                CalendarViewHolder(parent, accountModel, webcalModel)

    }


    class WebcalModel(application: Application): AndroidViewModel(application) {

        private var initialized = false
        private var serviceId: Long = 0

        private val db = AppDatabase.getInstance(application)
        private val resolver = application.contentResolver

        private val calendarPermissions = ContextCompat.checkSelfPermission(application, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
        private val calendarProvider = if (calendarPermissions) resolver.acquireContentProviderClient(CalendarContract.AUTHORITY) else null

        private val subscribedUrls = mutableMapOf<HttpUrl, Long>()

        private val workerThread = HandlerThread(javaClass.simpleName)
        init { workerThread.start() }
        val workerHandler = Handler(workerThread.looper)

        val observer = object: ContentObserver(workerHandler) {
            override fun onChange(selfChange: Boolean) {
                queryCalendars()
            }
        }

        fun initialize(dbServiceId: Long) {
            if (initialized)
                return
            initialized = true

            serviceId = dbServiceId

            if (calendarPermissions)
                resolver.registerContentObserver(Calendars.CONTENT_URI, false, observer)

            workerHandler.post {
                queryCalendars()
            }
        }

        override fun onCleared() {
            if (calendarPermissions)
                resolver.unregisterContentObserver(observer)

            calendarProvider?.closeCompat()
        }

        @WorkerThread
        @Transaction
        private fun queryCalendars() {
            // query subscribed URLs from Android calendar list
            subscribedUrls.clear()
            calendarProvider?.query(Calendars.CONTENT_URI, arrayOf(Calendars._ID, Calendars.NAME),null, null, null)?.use { cursor ->
                while (cursor.moveToNext())
                    HttpUrl.parse(cursor.getString(1))?.let { url ->
                        subscribedUrls[url] = cursor.getLong(0)
                    }
            }

            // update "sync" field in database accordingly (will update UI)
            db.collectionDao().getByServiceAndType(serviceId, Collection.TYPE_WEBCAL).forEach { webcal ->
                val newSync = subscribedUrls.keys
                        .any { webcal.source?.let { source -> UrlUtils.equals(source, it) } ?: false }
                if (newSync != webcal.sync)
                    db.collectionDao().update(webcal.copy(sync = newSync))
            }
        }

        fun unsubscribe(webcal: Collection) {
            workerHandler.post {
                subscribedUrls[webcal.source]?.let { id ->
                    // delete subscription from Android calendar list
                    calendarProvider?.delete(Calendars.CONTENT_URI,
                            "${Calendars._ID}=?", arrayOf(id.toString()))
                }
            }
        }

    }

}