/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.settings

import android.content.Context
import androidx.annotation.WorkerThread
import at.bitfire.davdroid.log.Logger
import java.lang.ref.WeakReference
import java.util.*
import java.util.logging.Level

class Settings(
        appContext: Context
) {

    companion object {

        // settings keys and default values
        const val DISTRUST_SYSTEM_CERTIFICATES = "distrust_system_certs"
        const val DISTRUST_SYSTEM_CERTIFICATES_DEFAULT = false
        const val OVERRIDE_PROXY = "override_proxy"
        const val OVERRIDE_PROXY_DEFAULT = false
        const val OVERRIDE_PROXY_HOST = "override_proxy_host"
        const val OVERRIDE_PROXY_PORT = "override_proxy_port"

        const val OVERRIDE_PROXY_HOST_DEFAULT = "localhost"
        const val OVERRIDE_PROXY_PORT_DEFAULT = 8118


        private var singleton: Settings? = null

        fun getInstance(context: Context): Settings {
            singleton?.let { return it }

            val newInstance = Settings(context.applicationContext)
            singleton = newInstance
            return newInstance
        }

    }

    private val providers = LinkedList<SettingsProvider>()
    private val observers = LinkedList<WeakReference<OnChangeListener>>()

    init {
        synchronized(javaClass) {       // ServiceLoader is not thread-safe
            val factories = ServiceLoader.load(ISettingsProviderFactory::class.java)
            Logger.log.fine("Loading settings providers from ${factories.count()} factories")
            factories.forEach { factory ->
                providers.addAll(factory.getProviders(appContext))
            }
        }
    }

    fun forceReload() {
        providers.forEach {
            it.forceReload()
        }
        onSettingsChanged()
    }


    /*** OBSERVERS ***/

    fun addOnChangeListener(observer: OnChangeListener) {
        synchronized(this) {
            observers += WeakReference(observer)
        }
    }

    fun removeOnChangeListener(observer: OnChangeListener) {
        synchronized(this) {
            observers.removeAll { it.get() == null || it.get() == observer }
        }
    }

    fun onSettingsChanged() {
        synchronized(this) {
            observers.mapNotNull { it.get() }.forEach {
                it.onSettingsChanged()
            }
        }
    }


    /*** SETTINGS ACCESS ***/

    fun has(key: String): Boolean {
        Logger.log.fine("Looking for setting $key")
        var result = false
        for (provider in providers)
            try {
                val (value, further) = provider.has(key)
                Logger.log.finer("${provider::class.java.simpleName}: has $key = $value, continue: $further")
                if (value) {
                    result = true
                    break
                }
                if (!further)
                    break
            } catch(e: Exception) {
                Logger.log.log(Level.SEVERE, "Couldn't look up setting in $provider", e)
            }
        Logger.log.fine("Looking for setting $key -> $result")
        return result
    }

    private fun<T> getValue(key: String, reader: (SettingsProvider) -> Pair<T?, Boolean>): T? {
        Logger.log.fine("Looking up setting $key")
        var result: T? = null
        for (provider in providers)
            try {
                val (value, further) = reader(provider)
                Logger.log.finer("${provider::class.java.simpleName}: value = $value, continue: $further")
                value?.let { result = it }
                if (!further)
                    break
            } catch(e: Exception) {
                Logger.log.log(Level.SEVERE, "Couldn't read setting from $provider", e)
            }
        Logger.log.fine("Looked up setting $key -> $result")
        return result
    }

    fun getBoolean(key: String) =
            getValue(key) { provider -> provider.getBoolean(key) }

    fun getInt(key: String) =
            getValue(key) { provider -> provider.getInt(key) }

    fun getLong(key: String) =
            getValue(key) { provider -> provider.getLong(key) }

    fun getString(key: String) =
            getValue(key) { provider -> provider.getString(key) }


    fun isWritable(key: String): Boolean {
        for (provider in providers) {
            val (value, further) = provider.isWritable(key)
            if (value)
                return true
            if (!further)
                return false
        }
        return false
    }

    private fun<T> putValue(key: String, value: T?, writer: (SettingsProvider) -> Boolean): Boolean {
        Logger.log.fine("Trying to write setting $key = $value")
        for (provider in providers) {
            val (writable, further) = provider.isWritable(key)
            Logger.log.finer("${provider::class.java.simpleName}: writable = $writable, continue: $further")
            if (writable)
                return try {
                    writer(provider)
                } catch (e: Exception) {
                    Logger.log.log(Level.SEVERE, "Couldn't write setting to $provider", e)
                    false
                }
            if (!further)
                return false
        }
        return false
    }

    fun putBoolean(key: String, value: Boolean?) =
            putValue(key, value) { provider -> provider.putBoolean(key, value) }

    fun putInt(key: String, value: Int?) =
            putValue(key, value) { provider -> provider.putInt(key, value) }

    fun putLong(key: String, value: Long?) =
            putValue(key, value) { provider -> provider.putLong(key, value) }

    fun putString(key: String, value: String?) =
            putValue(key, value) { provider -> provider.putString(key, value) }

    fun remove(key: String): Boolean {
        var deleted = false
        providers.forEach { deleted = deleted || it.remove(key) }
        return deleted
    }


    interface OnChangeListener {
        /**
         * Will be called when something has changed in a [SettingsProvider].
         * Runs in worker thread!
         */
        @WorkerThread
        fun onSettingsChanged()
    }

}
