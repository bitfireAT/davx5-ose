/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.settings

import android.content.Context
import android.util.NoSuchPropertyException
import androidx.annotation.AnyThread
import at.bitfire.davdroid.AndroidSingleton
import at.bitfire.davdroid.log.Logger
import java.io.Writer
import java.lang.ref.WeakReference
import java.util.*
import java.util.logging.Level

/**
 * Settings manager which coordinates [SettingsProvider]s to read/write
 * application settings.
 */
class SettingsManager private constructor(
        context: Context
) {

    companion object: AndroidSingleton<SettingsManager>() {
        override fun createInstance(context: Context) = SettingsManager(context)
    }

    private val providers = LinkedList<SettingsProvider>()
    private var writeProvider: SettingsProvider? = null

    private val observers = LinkedList<WeakReference<OnChangeListener>>()

    init {
        val factories = ServiceLoader.load(SettingsProviderFactory::class.java, context.classLoader)
        Logger.log.fine("Loading settings providers from ${factories.count()} factories")
        for (factory in factories)
            providers.addAll(factory.getProviders(context, this))

        writeProvider = providers.first { it.canWrite() }
        Logger.log.fine("Changed settings are handled by $writeProvider")
    }

    /**
     * Requests all providers to reload their settings.
     */
    @AnyThread
    fun forceReload() {
        for (provider in providers)
            provider.forceReload()
        onSettingsChanged()
    }


    /*** OBSERVERS ***/

    fun addOnChangeListener(observer: OnChangeListener) {
        synchronized(observers) {
            observers += WeakReference(observer)
        }
    }

    fun removeOnChangeListener(observer: OnChangeListener) {
        synchronized(observers) {
            observers.removeAll { it.get() == null || it.get() == observer }
        }
    }

    /**
     * Notifies registered listeners about changes in the configuration.
     * Should be called by config providers when settings have changed.
     */
    @AnyThread
    fun onSettingsChanged() {
        synchronized(observers) {
            for (observer in observers.mapNotNull { it.get() })
                observer.onSettingsChanged()
        }
    }


    /*** SETTINGS ACCESS ***/

    fun containsKey(key: String) = providers.any { it.contains(key) }

    private fun<T> getValue(key: String, reader: (SettingsProvider) -> T?): T? {
        Logger.log.fine("Looking up setting $key")
        val result: T? = null
        for (provider in providers)
            try {
                val value = reader(provider)
                Logger.log.finer("${provider::class.java.simpleName}: value = $value")
                if (value != null) {
                    Logger.log.fine("Looked up setting $key -> $value")
                    return value
                }
            } catch(e: Exception) {
                Logger.log.log(Level.SEVERE, "Couldn't read setting from $provider", e)
            }
        Logger.log.fine("Looked up setting $key -> no result")
        return result
    }

    fun getBooleanOrNull(key: String): Boolean? = getValue(key) { provider -> provider.getBoolean(key) }
    fun getBoolean(key: String): Boolean = getBooleanOrNull(key) ?: throw NoSuchPropertyException(key)

    fun getIntOrNull(key: String): Int? = getValue(key) { provider -> provider.getInt(key) }
    fun getInt(key: String): Int = getIntOrNull(key) ?: throw NoSuchPropertyException(key)

    fun getLongOrNull(key: String): Long? = getValue(key) { provider -> provider.getLong(key) }
    fun getLong(key: String) = getLongOrNull(key) ?: throw NoSuchPropertyException(key)

    fun getString(key: String) = getValue(key) { provider -> provider.getString(key) }


    fun isWritable(key: String): Boolean {
        for (provider in providers) {
            if (provider.canWrite())
                return true
            else if (provider.contains(key))
                // non-writeable provider contains this key -> setting will always be provided by this read-only provider
                return false
        }
        return false
    }

    private fun<T> putValue(key: String, value: T?, writer: (SettingsProvider) -> Unit) {
        Logger.log.fine("Trying to write setting $key = $value")
        val provider = writeProvider ?: return
        try {
            writer(provider)
        } catch (e: Exception) {
            Logger.log.log(Level.SEVERE, "Couldn't write setting to $writeProvider", e)
        }
    }

    fun putBoolean(key: String, value: Boolean?) =
            putValue(key, value) { provider -> provider.putBoolean(key, value) }

    fun putInt(key: String, value: Int?) =
            putValue(key, value) { provider -> provider.putInt(key, value) }

    fun putLong(key: String, value: Long?) =
            putValue(key, value) { provider -> provider.putLong(key, value) }

    fun putString(key: String, value: String?) =
            putValue(key, value) { provider -> provider.putString(key, value) }

    fun remove(key: String) = putString(key, null)


    /*** HELPERS ***/

    fun dump(writer: Writer) {
        for ((idx, provider) in providers.withIndex()) {
            writer.write("${idx + 1}. ${provider::class.java.simpleName} canWrite=${provider.canWrite()}\n")
            provider.dump(writer)
        }
    }


    interface OnChangeListener {
        /**
         * Will be called when something has changed in a [SettingsProvider].
         * May run in worker thread!
         */
        @AnyThread
        fun onSettingsChanged()
    }

}