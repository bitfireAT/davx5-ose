/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import at.bitfire.davdroid.ui.navigation.Destination
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class NavModel(
    initialDestination: Destination,
) : ViewModel() {

    private val _backStack = MutableStateFlow(listOf(initialDestination))
    val backStack get() = _backStack.asStateFlow()

    private val mutex = Semaphore(1)

    /**
     * Handles back navigation.
     * @param amount The number of entries to pop from the end of the backstack, as calculated by the `NavDisplay`'s `sceneStrategy`.
     */
    fun popBackStack(amount: Int) {
        viewModelScope.launch {
            mutex.withPermit {
                val backStack = backStack.value.toMutableList().apply {
                    repeat(amount) { removeAt(lastIndex) }
                }
                _backStack.emit(backStack)
            }
        }
    }

    class Factory(
        private val initialDestination: Destination = Destination.Accounts()
    ): ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return NavModel(initialDestination) as T
        }
    }

}
