/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.repository

import android.content.Context
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import androidx.preference.PreferenceManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject

/**
 * Repository to access preferences. Preferences are stored in a shared preferences file
 * and reflect settings that are very low-level and are therefore not covered by
 * [at.bitfire.davdroid.settings.SettingsManager].
 */
class PreferenceRepository @Inject constructor(
    @ApplicationContext context: Context
) {

    companion object {
        const val LOG_TO_FILE = "log_to_file"
        const val UNIFIED_PUSH_ENDPOINT = "unified_push_endpoint"
    }

    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)


    /**
     * Updates the "log to file" (verbose logging") preference.
     */
    fun logToFile(logToFile: Boolean) {
        preferences
            .edit()
            .putBoolean(LOG_TO_FILE, logToFile)
            .apply()
    }

    /**
    * Gets the "log to file" (verbose logging) preference.
    */
    fun logToFile(): Boolean =
        preferences.getBoolean(LOG_TO_FILE, false)

    /**
     * Gets the "log to file" (verbose logging) preference as a live value.
     */
    fun logToFileFlow(): Flow<Boolean> = observeAsFlow(LOG_TO_FILE) {
        logToFile()
    }


    fun unifiedPushEndpoint() =
        preferences.getString(UNIFIED_PUSH_ENDPOINT, null)

    fun unifiedPushEndpointFlow() = observeAsFlow(UNIFIED_PUSH_ENDPOINT) {
        unifiedPushEndpoint()
    }

    fun unifiedPushEndpoint(endpoint: String?) {
        preferences
            .edit()
            .putString(UNIFIED_PUSH_ENDPOINT, endpoint)
            .apply()
    }


    // helpers

    private fun<T> observeAsFlow(keyToObserve: String, getValue: () -> T): Flow<T> =
        callbackFlow {
            val listener = OnSharedPreferenceChangeListener { _, key ->
                if (key == keyToObserve) {
                    trySend(getValue())
                }
            }
            preferences.registerOnSharedPreferenceChangeListener(listener)

            // Emit the initial value
            trySend(getValue())

            awaitClose {
                preferences.unregisterOnSharedPreferenceChangeListener(listener)
            }
    }

}