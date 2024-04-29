/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.account

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import at.bitfire.davdroid.repository.DavCollectionRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class CollectionScreenModel @AssistedInject constructor(
    @Assisted val collectionId: Long,
    val collectionRepository: DavCollectionRepository
): ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(collectionId: Long): CollectionScreenModel
    }

    companion object {
        fun factoryFromCollection(assistedFactory: Factory, collectionId: Long) = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return assistedFactory.create(collectionId) as T
            }
        }
    }

    var invalid by mutableStateOf(false)
    val collection = collectionRepository.getFlow(collectionId)
        .map {
            if (it == null)
                invalid = true
            it
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * Deletes the collection from the database and the server.
     */
    fun delete() {
        val collection = collection.value ?: return
        //collectionRepository.deleteFromServer()
    }

    /*

    val deleteCollectionResult = MutableLiveData<Optional<Exception>>()
    /** Deletes the given collection from the database and the server. */
    fun deleteCollection(collection: Collection) = viewModelScope.launch(Dispatchers.IO) {
        HttpClient.Builder(context, AccountSettings(context, account))
            .setForeground(true)
            .build().use { httpClient ->
                try {
                    // delete on server
                    val davResource = DavResource(httpClient.okHttpClient, collection.url)
                    davResource.delete(null) {}

                    // delete in database
                    db.collectionDao().delete(collection)

                    // post success
                    deleteCollectionResult.postValue(Optional.empty())
                } catch (e: Exception) {
                    Logger.log.log(Level.SEVERE, "Couldn't delete collection", e)
                    // post error
                    deleteCollectionResult.postValue(Optional.of(e))
                }
            }
    }
     */

}