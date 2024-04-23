/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.webdav

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.bitfire.davdroid.db.WebDavMount
import at.bitfire.davdroid.webdav.WebDavMountRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WebdavMountsModel @Inject constructor(
    private val mountRepository: WebDavMountRepository
): ViewModel() {

    private val mounts = mountRepository.getAllFlow()

    // UI state
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
     * Refreshes quota of all mounts (causes progress bar to be shown during refresh).
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
     * Removes the mountpoint locally (= deletes connection information).
     */
    fun remove(mount: WebDavMount) {
        viewModelScope.launch {
            mountRepository.delete(mount)
        }
    }

}