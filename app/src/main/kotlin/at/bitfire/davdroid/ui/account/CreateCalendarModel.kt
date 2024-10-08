/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.account

import android.accounts.Account
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import at.bitfire.davdroid.db.HomeSet
import at.bitfire.davdroid.repository.DavCollectionRepository
import at.bitfire.davdroid.repository.DavHomeSetRepository
import at.bitfire.ical4android.Css3Color
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.Collator
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = CreateCalendarModel.Factory::class)
class CreateCalendarModel @AssistedInject constructor(
    @Assisted val account: Account,
    private val collectionRepository: DavCollectionRepository,
    homeSetRepository: DavHomeSetRepository
): ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(account: Account): CreateCalendarModel
    }

    val calendarHomeSets = homeSetRepository.getCalendarHomeSetsFlow(account)

    data class TimeZoneInfo(
        val id: String,
        val displayName: String,
    )

    /** List of available time zones as <display name, ID> pairs. */
    val timeZones: Flow<List<TimeZoneInfo>> = flow {
        val timeZones = mutableListOf<TimeZoneInfo>()
        val locale = Locale.getDefault()
        for (id in ZoneId.getAvailableZoneIds())
            timeZones += TimeZoneInfo(
                id,
                ZoneId.of(id).getDisplayName(TextStyle.FULL, locale),
            )

        val collator = Collator.getInstance()
        val result = timeZones.sortedBy { collator.getCollationKey(it.displayName) }

        emit(result)
    }.flowOn(Dispatchers.Default)


    // UI state

    data class UiState(
        val error: Exception? = null,
        val success: Boolean = false,

        val color: Int = Css3Color.entries.random().argb,
        val displayName: String = "",
        val description: String = "",
        val timeZoneId: String? = null,
        val supportVEVENT: Boolean = true,
        val supportVTODO: Boolean = true,
        val supportVJOURNAL: Boolean = true,
        val homeSet: HomeSet? = null,
        val isCreating: Boolean = false
    ) {
        val canCreate = !isCreating && displayName.isNotBlank() && homeSet != null
    }

    var uiState by mutableStateOf(UiState())
        private set

    fun resetError() {
        uiState = uiState.copy(error = null)
    }

    fun setColor(color: Int) {
        uiState = uiState.copy(color = color)
    }

    fun setDisplayName(displayName: String) {
        uiState = uiState.copy(displayName = displayName)
    }

    fun setDescription(description: String) {
        uiState = uiState.copy(description = description)
    }

    fun setTimeZoneId(timeZoneId: String?) {
        uiState = uiState.copy(timeZoneId = timeZoneId)
    }

    fun setSupportVEVENT(supportVEVENT: Boolean) {
        uiState = uiState.copy(supportVEVENT = supportVEVENT)
    }

    fun setSupportVTODO(supportVTODO: Boolean) {
        uiState = uiState.copy(supportVTODO = supportVTODO)
    }

    fun setSupportVJOURNAL(supportVJOURNAL: Boolean) {
        uiState = uiState.copy(supportVJOURNAL = supportVJOURNAL)
    }

    fun setHomeSet(homeSet: HomeSet) {
        uiState = uiState.copy(homeSet = homeSet)
    }


    // actions

    /* Creating collections shouldn't be cancelled when the view is destroyed, otherwise we might
    end up with collections on the server that are not represented in the database/UI. */
    private val createCollectionScope = CoroutineScope(SupervisorJob())

    fun createCalendar() {
        val homeSet = uiState.homeSet ?: return
        uiState = uiState.copy(isCreating = true)

        createCollectionScope.launch {
            uiState = try {
                collectionRepository.createCalendar(
                    account = account,
                    homeSet = homeSet,
                    color = uiState.color,
                    displayName = uiState.displayName,
                    description = uiState.description,
                    timeZoneId = uiState.timeZoneId,
                    supportVEVENT = uiState.supportVEVENT,
                    supportVTODO = uiState.supportVTODO,
                    supportVJOURNAL = uiState.supportVJOURNAL
                )

                uiState.copy(isCreating = false, success = true)
            } catch (e: Exception) {
                uiState.copy(isCreating = false, error = e)
            }
        }
    }

}