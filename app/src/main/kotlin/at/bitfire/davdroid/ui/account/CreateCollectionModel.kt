/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.account

import android.accounts.Account
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import at.bitfire.davdroid.db.HomeSet
import at.bitfire.davdroid.repository.DavCollectionRepository
import at.bitfire.davdroid.repository.DavHomeSetRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CreateCollectionModel @AssistedInject constructor(
    val collectionRepository: DavCollectionRepository,
    homeSetRepository: DavHomeSetRepository,
    @Assisted val account: Account
): ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(account: Account): CreateCollectionModel
    }

    companion object {
        fun factoryFromAccount(assistedFactory: Factory, account: Account) = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return assistedFactory.create(account) as T
            }
        }
    }


    val addressBookHomeSets = homeSetRepository.getAddressBookHomeSetsFlow(account)
    val calendarHomeSets = homeSetRepository.getCalendarHomeSetsFlow(account)


    // UI state

    data class UiState(
        val success: Boolean = false,

        val displayName: String = "",
        val description: String = "",
        val selectedHomeSet: HomeSet? = null,
        val isCreating: Boolean = false
    ) {
        val canCreate = !isCreating && displayName.isNotBlank() && selectedHomeSet != null
    }

    var uiState by mutableStateOf(UiState())
        private set

    fun setDisplayName(displayName: String) {
        uiState = uiState.copy(displayName = displayName)
    }

    fun setDescription(description: String) {
        uiState = uiState.copy(description = description)
    }

    fun setHomeSet(homeSet: HomeSet) {
        uiState = uiState.copy(selectedHomeSet = homeSet)
    }


    // actions

    /* Creating collections shouldn't be cancelled when the view is destroyed, otherwise we might
    end up with collections on the server that are not represented in the database/UI. */
    private val createCollectionScope = CoroutineScope(SupervisorJob())

    fun createAddressBook() {
        val homeSet = uiState.selectedHomeSet ?: return
        uiState = uiState.copy(isCreating = true)

        createCollectionScope.launch {
            collectionRepository.createAddressBook(
                account = account,
                homeSet = homeSet,
                displayName = uiState.displayName,
                description = uiState.description
            )

            // TODO error handling

            uiState = uiState.copy(isCreating = false, success = true)
        }
    }

}