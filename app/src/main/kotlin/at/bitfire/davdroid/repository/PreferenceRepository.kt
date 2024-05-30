/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.repository

import android.app.Application
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import androidx.preference.PreferenceManager
import at.bitfire.davdroid.log.Logger
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
    context: Application
) {

    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)

    /**
     * Updates the "log to file" (verbose logging") setting.
     */
    fun logToFile(logToFile: Boolean) {
        preferences
            .edit()
            .putBoolean(Logger.LOG_TO_FILE, logToFile)
            .apply()
    }

    /**
     * Gets the "log to file" (verbose logging) setting as a live value.
     */
    fun logToFileFlow(): Flow<Boolean> = observeAsFlow(Logger.LOG_TO_FILE) {
        preferences.getBoolean(Logger.LOG_TO_FILE, false)
    }


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