/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.webdav

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.WebDavMount
import at.bitfire.davdroid.webdav.CredentialsStore
import at.bitfire.davdroid.webdav.DavDocumentsProvider
import at.bitfire.davdroid.webdav.WebDavMountRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WebdavMountsModel @Inject constructor(
    val context: Application,
    val db: AppDatabase,
    val mountRepository: WebDavMountRepository
): ViewModel() {

    private val mounts = mountRepository.getAllFlow()
    val mountInfos = mountRepository.getAllWithRootFlow()

    var refreshingQuota by mutableStateOf(false)
        private set

    init {
        // refresh quota as soon as (new) mounts are available
        viewModelScope.launch {
            mounts.collect {
                refreshQuota()
            }
        }
    }

    /**
     * Queries every root document of the currently known mounts, causing the quota values to be updated in the database.
     */
    fun refreshQuota() {
        if (refreshingQuota)
            return
        refreshingQuota = true

        viewModelScope.launch {
            mountRepository.refreshAllQuota()
            refreshingQuota = false
        }
    }

    /**
     * Removes the mountpoint (deleting connection information)
     */
    fun remove(mount: WebDavMount) {
        viewModelScope.launch(Dispatchers.Default) {
            // remove mount from database
            db.webDavMountDao().delete(mount)

            // remove credentials, too
            CredentialsStore(context).setCredentials(mount.id, null)

            // notify content URI listeners
            DavDocumentsProvider.notifyMountsChanged(context)
        }
    }

}