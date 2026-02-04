/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.settings

import at.bitfire.davdroid.util.TextTable
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntKey
import dagger.multibindings.IntoMap
import java.io.Writer
import javax.inject.Inject

class DefaultsProvider @Inject constructor(): SettingsProvider {

    val booleanDefaults = mutableMapOf(
        Pair(Settings.DISTRUST_SYSTEM_CERTIFICATES, false),
        Pair(Settings.FORCE_READ_ONLY_ADDRESSBOOKS, false),
        Pair(Settings.IGNORE_VPN_NETWORK_CAPABILITY, true)
    )

    val intDefaults = mapOf(
        Pair(Settings.PRESELECT_COLLECTIONS, Settings.PRESELECT_COLLECTIONS_NONE),
        Pair(Settings.PROXY_TYPE, Settings.PROXY_TYPE_SYSTEM),
        Pair(Settings.PROXY_PORT, 9050)     // Orbot SOCKS
    )

    val longDefaults = mapOf<String, Long>(
        Pair(Settings.DEFAULT_SYNC_INTERVAL, 4*3600)    /* 4 hours */
    )

    val stringDefaults = mapOf(
        Pair(Settings.PROXY_HOST, "localhost"),
        Pair(Settings.PRESELECT_COLLECTIONS_EXCLUDED, "/z-app-generated--contactsinteraction--recent/") // Nextcloud "Recently Contacted" address book
    )


    override fun canWrite() = false

    override fun close() {
        // no resources to close
    }

    override fun setOnChangeListener(listener: SettingsProvider.OnChangeListener) {
        // default settings never change
    }

    override fun forceReload() {
        // default settings never change
    }


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


    override fun dump(writer: Writer) {
        val strValues = mutableMapOf<String, String?>()
        strValues.putAll(booleanDefaults.mapValues { (_, value) -> value.toString() })
        strValues.putAll(intDefaults.mapValues { (_, value) -> value.toString() })
        strValues.putAll(longDefaults.mapValues { (_, value) -> value.toString() })
        strValues.putAll(stringDefaults)

        val table = TextTable("Setting", "Value")
        for ((key, value) in strValues.toSortedMap())
            table.addLine(key, value)
        writer.write(table.toString())
    }


    @Module
    @InstallIn(SingletonComponent::class)
    abstract class DefaultsProviderModule {
        @Binds
        @IntoMap
        @IntKey(/* priority */ 0)
        abstract fun defaultsProvider(impl: DefaultsProvider): SettingsProvider
    }

}