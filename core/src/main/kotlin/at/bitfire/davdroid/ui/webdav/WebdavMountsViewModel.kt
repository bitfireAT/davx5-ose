/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.webdav

import android.content.Context
import android.content.Intent
import android.provider.DocumentsContract
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.WebDavMount
import at.bitfire.davdroid.webdav.WebDavMountRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WebdavMountsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mountRepository: WebDavMountRepository
): ViewModel() {

    /** authority of our WebDAV document provider ([at.bitfire.davdroid.webdav.DavDocumentsProvider]) */
    private val authority = context.getString(R.string.webdav_authority)

    private val mounts = mountRepository.getAllFlow()

    // UI state
    val mountInfos = mountRepository.getAllWithRootFlow()
    var refreshingQuota by mutableStateOf(false)
        private set

    /** Whether the device has a file manager that can browse our document roots (independent of the mount). */
    var canBrowseFiles by mutableStateOf(false)
        private set

    init {
        // root ID doesn't matter for intent-filter matching, but the intent shape must match browseFiles()
        canBrowseFiles = browseFilesIntent("").hasHandler()

        // refresh quota as soon as mounts are available
        viewModelScope.launch {
            mounts.collect {
                refreshQuota()
            }
        }
    }

    /**
     * Returns an intent to browse [mount] in the system file manager, or null if none is available.
     */
    fun browseFiles(mount: WebDavMount): Intent? {
        val intent = browseFilesIntent(mount.id.toString())
        return if (intent.hasHandler()) intent else null
    }

    private fun browseFilesIntent(rootId: String) = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(
            DocumentsContract.buildRootUri(authority, rootId),
            DocumentsContract.Root.MIME_TYPE_ITEM
        )
    }

    /** Returns whether there's an app that can handle this intent. */
    private fun Intent.hasHandler() = resolveActivity(context.packageManager) != null

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