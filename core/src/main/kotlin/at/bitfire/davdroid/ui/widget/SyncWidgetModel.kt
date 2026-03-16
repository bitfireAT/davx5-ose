/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.widget

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.bitfire.davdroid.repository.AccountRepository
import at.bitfire.davdroid.sync.worker.SyncWorkerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

class SyncWidgetModel @Inject constructor(
    private val accountRepository: AccountRepository,
    @ApplicationContext val context: Context,
    private val syncWorkerManager: SyncWorkerManager
): ViewModel() {

    fun requestSync() = viewModelScope.launch(Dispatchers.Default) {
        for (account in accountRepository.getAll())
            syncWorkerManager.enqueueOneTimeAllAuthorities(account, manual = true)
    }

}
