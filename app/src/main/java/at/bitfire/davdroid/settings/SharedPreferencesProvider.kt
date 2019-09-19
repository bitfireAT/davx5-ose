/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.settings

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import android.preference.PreferenceManager
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.model.AppDatabase

class SharedPreferencesProvider(
        val context: Context
): SettingsProvider, SharedPreferences.OnSharedPreferenceChangeListener {

    companion object {
        private const val META_VERSION = "version"
        private const val CURRENT_VERSION = 0
    }

    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)

    init {
        val meta = context.getSharedPreferences("meta", MODE_PRIVATE)
        val version = meta.getInt(META_VERSION, -1)
        if (version == -1) {
            // first call, check whether to migrate from SQLite database (DAVdroid <1.9)
            firstCall(context)
            meta.edit().putInt(META_VERSION, CURRENT_VERSION).apply()
        }

        preferences.registerOnSharedPreferenceChangeListener(this)
    }

    override fun forceReload() {
    }

    override fun close() {
        preferences.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        Settings.getInstance(context).onSettingsChanged()
    }


    override fun has(key: String) =
            Pair(preferences.contains(key), true)

    private fun<T> getValue(key: String, reader: (SharedPreferences) -> T): Pair<T?, Boolean> {
        if (preferences.contains(key))
            return Pair(
                    try { reader(preferences) } catch(e: ClassCastException) { null },
                    true)

        return Pair(null, true)
    }

    override fun getBoolean(key: String): Pair<Boolean?, Boolean> =
            getValue(key) { preferences -> preferences.getBoolean(key, /* will never be used: */ false) }

    override fun getInt(key: String): Pair<Int?, Boolean> =
            getValue(key) { preferences -> preferences.getInt(key, /* will never be used: */ -1) }

    override fun getLong(key: String): Pair<Long?, Boolean> =
            getValue(key) { preferences -> preferences.getLong(key, /* will never be used: */ -1) }

    override fun getString(key: String): Pair<String?, Boolean> =
            getValue(key) { preferences -> preferences.getString(key, /* will never be used: */ null) }


    override fun isWritable(key: String) =
            Pair(first = true, second = true)

    private fun<T> putValue(key: String, value: T?, writer: (SharedPreferences.Editor, T) -> Unit): Boolean {
        return if (value == null)
            remove(key)
        else {
            Logger.log.fine("Writing setting $key = $value")
            val edit = preferences.edit()
            writer(edit, value)
            edit.apply()
            true
        }
    }

    override fun putBoolean(key: String, value: Boolean?) =
            putValue(key, value) { editor, v -> editor.putBoolean(key, v) }

    override fun putInt(key: String, value: Int?) =
            putValue(key, value) { editor, v -> editor.putInt(key, v) }

    override fun putLong(key: String, value: Long?) =
            putValue(key, value) { editor, v -> editor.putLong(key, v) }

    override fun putString(key: String, value: String?) =
            putValue(key, value) { editor, v -> editor.putString(key, v) }

    override fun remove(key: String): Boolean {
        Logger.log.fine("Removing setting $key")
        preferences.edit()
                .remove(key)
                .apply()
        return true
    }


    private fun firstCall(context: Context) {
        // remove possible artifacts from DAVdroid <1.9
        val edit = preferences.edit()
        edit.remove("override_proxy")
        edit.remove("proxy_host")
        edit.remove("proxy_port")
        edit.remove("log_to_external_storage")
        edit.apply()

        // open ServiceDB to upgrade it and possibly migrate settings
        AppDatabase.getInstance(context)
    }


    class Factory : ISettingsProviderFactory {
        override fun getProviders(context: Context) = listOf(SharedPreferencesProvider(context))
    }

}