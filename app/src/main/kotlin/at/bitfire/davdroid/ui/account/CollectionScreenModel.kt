/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.account

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.repository.DavCollectionRepository
import at.bitfire.davdroid.repository.DavSyncStatsRepository
import at.bitfire.davdroid.settings.Settings
import at.bitfire.davdroid.settings.SettingsManager
import at.bitfire.davdroid.util.DavUtils.lastSegment
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = CollectionScreenModel.Factory::class)
class CollectionScreenModel @AssistedInject constructor(
    @Assisted val collectionId: Long,
    db: AppDatabase,
    private val collectionRepository: DavCollectionRepository,
    private val settings: SettingsManager,
    syncStatsRepository: DavSyncStatsRepository
): ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(collectionId: Long): CollectionScreenModel
    }

    /** Whether an operation (like deleting the collection) is currently in progress */
    var inProgress by mutableStateOf(false)
        private set

    var invalid by mutableStateOf(false)
    var error by mutableStateOf<Exception?>(null)
        private set

    val collection = collectionRepository.getFlow(collectionId)
        .map {
            if (it == null)
                invalid = true
            it
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)


    enum class ReadOnlyState {
        READ_ONLY_BY_SETTING,
        READ_ONLY_BY_SERVER,
        READ_ONLY_BY_USER,
        READ_WRITE;

        fun canUserChange() = this == READ_WRITE || this == READ_ONLY_BY_USER
        fun isReadOnly() = this != READ_WRITE
    }

    /** whether address-books are read-only by policy (if yes, it overrides everything else) */
    private val forceReadOnlyAddressBooks = settings.getBooleanFlow(Settings.FORCE_READ_ONLY_ADDRESSBOOKS, false)

    val readOnly: Flow<ReadOnlyState> = combine(collection, forceReadOnlyAddressBooks) { collection, forceReadOnlyAddressBook ->
        when {
            collection?.type == Collection.TYPE_ADDRESSBOOK && forceReadOnlyAddressBook ->
                ReadOnlyState.READ_ONLY_BY_SETTING
            collection?.privWriteContent == false ->
                ReadOnlyState.READ_ONLY_BY_SERVER
            collection?.forceReadOnly == true ->
                ReadOnlyState.READ_ONLY_BY_USER
            else ->
                ReadOnlyState.READ_WRITE
        }
    }


    private val principalDao = db.principalDao()
    val owner: Flow<String?> = collection.map { collection ->
        collection?.ownerId?.let { ownerId ->
            val principal = principalDao.getAsync(ownerId)
            principal.displayName ?: principal.url.lastSegment
        }
    }

    val lastSynced = syncStatsRepository.getLastSyncedFlow(collectionId)


    /** Scope for operations that must not be cancelled. */
    private val noCancellationScope = CoroutineScope(SupervisorJob())

    /**
     * Deletes the collection from the database and the server.
     */
    fun delete() {
        val collection = collection.value ?: return

        inProgress = true
        noCancellationScope.launch {
            try {
                collectionRepository.deleteRemote(collection)
            } catch (e: Exception) {
                error = e
            } finally {
                inProgress = false
            }
        }
    }

    fun resetError() {
        error = null
    }

    fun setForceReadOnly(forceReadOnly: Boolean) {
        viewModelScope.launch {
            collectionRepository.setForceReadOnly(collectionId, forceReadOnly)
        }
    }

    fun setSync(sync: Boolean) {
        viewModelScope.launch {
            collectionRepository.setSync(collectionId, sync)
        }
    }

}