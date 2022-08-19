/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui.account

import android.Manifest
import android.content.ContentProviderClient
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.view.*
import androidx.annotation.WorkerThread
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.*
import androidx.room.Transaction
import at.bitfire.dav4jvm.UrlUtils
import at.bitfire.davdroid.Constants
import at.bitfire.davdroid.PermissionUtils
import at.bitfire.davdroid.R
import at.bitfire.davdroid.closeCompat
import at.bitfire.davdroid.databinding.AccountCaldavItemBinding
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.log.Logger
import com.google.android.material.snackbar.Snackbar
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.logging.Level
import javax.inject.Inject
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set

@AndroidEntryPoint
class WebcalFragment: CollectionsFragment() {

    override val noCollectionsStringId = R.string.account_no_webcals

    @Inject lateinit var webcalModelFactory: WebcalModel.Factory
    val webcalModel by viewModels<WebcalModel>() {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>) =
                webcalModelFactory.create(
                    requireArguments().getLong(EXTRA_SERVICE_ID)
                ) as T
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webcalModel.subscribedUrls.observe(this, Observer { urls ->
            Logger.log.log(Level.FINE, "Got Android calendar list", urls.keys)
        })
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

    override fun createAdapter(): CollectionAdapter = WebcalAdapter(accountModel, webcalModel, this)


    class CalendarViewHolder(
        private val parent: ViewGroup,
        accountModel: AccountActivity.Model,
        private val webcalModel: WebcalModel,
        private val webcalFragment: WebcalFragment
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
            binding.actionOverflow.setOnClickListener(CollectionPopupListener(accountModel, item, webcalFragment.parentFragmentManager))
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

            val activity = webcalFragment.requireActivity()
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
        private val webcalModel: WebcalModel,
        val webcalFragment: WebcalFragment
    ): CollectionAdapter(accountModel) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
                CalendarViewHolder(parent, accountModel, webcalModel, webcalFragment)

    }


    class WebcalModel @AssistedInject constructor(
        @ApplicationContext context: Context,
        val db: AppDatabase,
        @Assisted val serviceId: Long
    ): ViewModel() {

        @AssistedFactory
        interface Factory {
            fun create(serviceId: Long): WebcalModel
        }

        private val resolver = context.contentResolver

        private var calendarPermission = false
        private val calendarProvider = object: MediatorLiveData<ContentProviderClient>() {
            init {
                calendarPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
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
                    val newObserver = object: ContentObserver(null) {
                        override fun onChange(selfChange: Boolean) {
                            viewModelScope.launch(Dispatchers.IO) {
                                queryCalendars(provider)
                            }
                        }
                    }
                    context.contentResolver.registerContentObserver(Calendars.CONTENT_URI, false, newObserver)
                    observer = newObserver

                    viewModelScope.launch(Dispatchers.IO) {
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
                    context.contentResolver.unregisterContentObserver(it)
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


        fun unsubscribe(webcal: Collection) {
            viewModelScope.launch(Dispatchers.IO) {
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