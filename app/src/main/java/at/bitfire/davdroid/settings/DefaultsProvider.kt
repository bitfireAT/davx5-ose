/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.settings

import android.content.Context

open class DefaultsProvider(
        private val allowOverride: Boolean = true
): SettingsProvider {

    open val booleanDefaults = mapOf(
            Pair(Settings.DISTRUST_SYSTEM_CERTIFICATES, false),
            Pair(Settings.OVERRIDE_PROXY, false)
    )

    open val intDefaults = mapOf(
            Pair(Settings.OVERRIDE_PROXY_PORT, 8118)
    )

    open val longDefaults = mapOf<String, Long>()

    open val stringDefaults = mapOf(
            Pair(Settings.OVERRIDE_PROXY_HOST, "localhost")
    )

    override fun forceReload() {
    }

    override fun close() {
    }

    override fun canWrite() = false


    override fun contains(key: String) =
            booleanDefaults.containsKey(key) ||
            intDefaults.containsKey(key) ||
            longDefaults.containsKey(key) ||
            stringDefaults.containsKey(key)


    override fun getBoolean(key: String) = booleanDefaults[key]
    override fun getInt(key: String) = intDefaults[key]
    override fun getLong(key: String) = longDefaults[key]
    override fun getString(key: String) = stringDefaults[key]

    override fun putBoolean(key: String, value: Boolean?) = throw NotImplementedError()
    override fun putInt(key: String, value: Int?) = throw NotImplementedError()
    override fun putLong(key: String, value: Long?) = throw NotImplementedError()
    override fun putString(key: String, value: String?) = throw NotImplementedError()

    override fun remove(key: String) = throw NotImplementedError()


    class Factory : SettingsProviderFactory {
        override fun getProviders(context: Context, settingsManager: SettingsManager) = listOf(DefaultsProvider())
    }

}