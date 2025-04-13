/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.repository

import android.content.Context
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.unifiedpush.android.connector.data.PublicKeySet
import org.unifiedpush.android.connector.data.PushEndpoint
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
        const val UNIFIED_PUSH_ENDPOINT_URL = "unified_push_endpoint_url"
        const val UNIFIED_PUSH_ENDPOINT_KEY = "unified_push_endpoint_key"
        const val UNIFIED_PUSH_ENDPOINT_AUTH = "unified_push_endpoint_auth"
    }

    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)


    /**
     * Updates the "log to file" (verbose logging") preference.
     */
    fun logToFile(logToFile: Boolean) {
        preferences.edit {
            putBoolean(LOG_TO_FILE, logToFile)
        }
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


    fun unifiedPushEndpoint(): PushEndpoint? {
        val url = preferences.getString(UNIFIED_PUSH_ENDPOINT_URL, null) ?: return null
        val key = preferences.getString(UNIFIED_PUSH_ENDPOINT_KEY, null)
        val auth = preferences.getString(UNIFIED_PUSH_ENDPOINT_AUTH, null)
        val publicKeySet = if (key != null && auth != null) PublicKeySet(key, auth) else null
        return PushEndpoint(url, publicKeySet)
    }

    fun unifiedPushEndpoint(endpoint: PushEndpoint?) {
        preferences.edit {
            putString(UNIFIED_PUSH_ENDPOINT_URL, endpoint?.url)
            putString(UNIFIED_PUSH_ENDPOINT_KEY, endpoint?.pubKeySet?.pubKey)
            putString(UNIFIED_PUSH_ENDPOINT_AUTH, endpoint?.pubKeySet?.auth)
        }
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