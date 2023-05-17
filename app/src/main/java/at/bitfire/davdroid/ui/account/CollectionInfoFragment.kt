/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui.account

import android.app.Application
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.map
import androidx.lifecycle.switchMap
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.resource.TaskUtils
import com.google.accompanist.themeadapter.material.MdcTheme
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class CollectionInfoFragment: DialogFragment() {

    companion object {

        private const val ARGS_COLLECTION_ID = "collectionId"

        fun newInstance(collectionId: Long): CollectionInfoFragment {
            val frag = CollectionInfoFragment()
            val args = Bundle(1)
            args.putLong(ARGS_COLLECTION_ID, collectionId)
            frag.arguments = args
            return frag
        }

    }

    @Inject lateinit var modelFactory: Model.Factory
    val model by viewModels<Model> {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>) =
                modelFactory.create(requireArguments().getLong(ARGS_COLLECTION_ID)) as T
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setContent {
                MdcTheme {
                    CollectionInfoDialog()
                }
            }
        }
    }

    @Composable
    fun CollectionInfoDialog() {
        Column(Modifier.padding(16.dp)) {
            // URL
            val collectionState = model.collection.observeAsState()
            collectionState.value?.let { collection ->
                Text(stringResource(R.string.collection_properties_url), style = MaterialTheme.typography.h5)
                Text(collection.url.toString(), modifier = Modifier.padding(bottom = 16.dp), fontFamily = FontFamily.Monospace)
            }

            // Owner
            val owner = model.owner.observeAsState()
            owner.value?.let { principal ->
                Text(stringResource(R.string.collection_properties_owner), style = MaterialTheme.typography.h5)
                Text(principal.displayName ?: principal.url.toString(), Modifier.padding(bottom = 16.dp))
            }

            // Last synced (for all applicable authorities)
            val lastSyncedState = model.lastSynced.observeAsState()
            lastSyncedState.value?.let { lastSynced ->
                Text(stringResource(R.string.collection_properties_sync_time), style = MaterialTheme.typography.h5)
                if (lastSynced.isEmpty())
                    Text(stringResource(R.string.collection_properties_sync_time_never))
                else
                    for ((app, timestamp) in lastSynced.entries) {
                        Text(app)
                        val timeStr = DateUtils.getRelativeDateTimeString(requireContext(), timestamp,
                            DateUtils.SECOND_IN_MILLIS, DateUtils.WEEK_IN_MILLIS, 0).toString()
                        Text(timeStr, Modifier.padding(bottom = 8.dp))
                    }
            }
        }
    }


    class Model @AssistedInject constructor(
        application: Application,
        val db: AppDatabase,
        @Assisted collectionId: Long
    ): AndroidViewModel(application) {

        @AssistedFactory
        interface Factory {
            fun create(collectionId: Long): Model
        }

        val collection = db.collectionDao().getLive(collectionId)
        val owner = collection.switchMap { collection ->
            collection.ownerId?.let { ownerId ->
                db.principalDao().getLive(ownerId)
            }
        }

        val lastSynced: LiveData<Map<String, Long>> =   // map: app name -> last sync timestamp
            db.syncStatsDao().getLiveByCollectionId(collectionId).map { syncStatsList ->
                // map: authority -> syncStats
                val syncStatsMap = syncStatsList.associateBy { it.authority }

                val interestingAuthorities = listOfNotNull(
                    ContactsContract.AUTHORITY,
                    CalendarContract.AUTHORITY,
                    TaskUtils.currentProvider(getApplication())?.authority
                )

                val result = mutableMapOf<String, Long>()
                // map (authority name) -> (app name, last sync timestamp)
                for (authority in interestingAuthorities) {
                    val lastSync = syncStatsMap[authority]?.lastSync
                    if (lastSync != null)
                        result[getAppNameFromAuthority(authority)] = lastSync
                }
                result
           }

        /**
         * Tries to find the application name for given authority. Returns the authority if not
         * found.
         *
         * @param authority authority to find the application name for (ie "at.techbee.jtx")
         * @return the application name of authority (ie "jtx Board")
         */
        private fun getAppNameFromAuthority(authority: String): String {
            val packageManager = getApplication<Application>().packageManager
            @Suppress("DEPRECATION")
            val packageName = packageManager.resolveContentProvider(authority, 0)?.packageName ?: authority
            return try {
                @Suppress("DEPRECATION")
                val appInfo = packageManager.getPackageInfo(packageName, 0).applicationInfo
                packageManager.getApplicationLabel(appInfo).toString()
            } catch (e: PackageManager.NameNotFoundException) {
                Logger.log.warning("Application name not found for authority: $authority")
                authority
            }
        }

    }

}