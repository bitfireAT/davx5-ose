/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.settings

import android.annotation.TargetApi
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import at.bitfire.davdroid.log.Logger
import java.io.Closeable
import java.lang.ref.WeakReference
import java.util.*
import java.util.logging.Level

class Settings: Service(), Provider.Observer {

    private val providers = LinkedList<Provider>()
    private val observers = LinkedList<WeakReference<ISettingsObserver>>()

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    override fun onCreate() {
        Logger.log.info("Initializing Settings service")

        // always add a defaults provider first
        providers.add(DefaultsProvider())

        // always add a provider for local preferences
        providers.add(SharedPreferencesProvider(this))
    }

    override fun onDestroy() {
        Logger.log.info("Shutting down Settings service")
        providers.forEach { it.close() }
        providers.clear()
    }

    override fun onTrimMemory(level: Int) {
        stopSelf()
    }


    override fun onReload() {
        observers.forEach {
            Handler(Looper.getMainLooper()).post {
                it.get()?.onSettingsChanged()
            }
        }
    }


    private fun has(key: String): Boolean {
        Logger.log.fine("Looking for setting $key")
        var result = false
        for (provider in providers) {
            val (value, further) = provider.has(key)
            Logger.log.finer("${provider::class.java.simpleName}: has $key = $value, continue: $further")
            if (value) {
                result = true
                break
            }
            if (!further)
                break
        }
        Logger.log.fine("Looking for setting $key -> $result")
        return result
    }

    private fun<T> getValue(key: String, reader: (Provider) -> Pair<T?, Boolean>): T? {
        Logger.log.fine("Looking up setting $key")
        var result: T? = null
        for (provider in providers) {
            val (value, further) = reader(provider)
            Logger.log.finer("${provider::class.java.simpleName}: value = $value, continue: $further")
            value?.let { result = it }
            if (!further)
                break
        }
        Logger.log.fine("Looked up setting $key -> $result")
        return result
    }

    fun getBoolean(key: String) =
            getValue(key, { provider -> provider.getBoolean(key) })

    fun getInt(key: String) =
            getValue(key, { provider -> provider.getInt(key) })

    fun getLong(key: String) =
            getValue(key, { provider -> provider.getLong(key) })

    fun getString(key: String) =
            getValue(key, { provider -> provider.getString(key) })


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

    private fun<T> putValue(key: String, value: T?, writer: (Provider) -> Boolean): Boolean {
        Logger.log.fine("Trying to write setting $key = $value")
        for (provider in providers) {
            val (writable, further) = provider.isWritable(key)
            Logger.log.finer("${provider::class.java.simpleName}: writable = $writable, continue: $further")
            if (writable)
                return writer(provider)
            if (!further)
                return false
        }
        return false
    }

    fun putBoolean(key: String, value: Boolean?) =
            putValue(key, value, { provider -> provider.putBoolean(key, value) })

    fun putInt(key: String, value: Int?) =
            putValue(key, value, { provider -> provider.putInt(key, value) })

    fun putLong(key: String, value: Long?) =
            putValue(key, value, { provider -> provider.putLong(key, value) })

    fun putString(key: String, value: String?) =
            putValue(key, value, { provider -> provider.putString(key, value) })

    fun remove(key: String): Boolean {
        var deleted = false
        providers.forEach { deleted = deleted || it.remove(key) }
        return deleted
    }


    val binder = object: ISettings.Stub() {

        override fun has(key: String) =
                this@Settings.has(key)

        override fun getBoolean(key: String, defaultValue: Boolean) =
                this@Settings.getBoolean(key) ?: defaultValue

        override fun getInt(key: String, defaultValue: Int) =
                this@Settings.getInt(key) ?: defaultValue

        override fun getLong(key: String, defaultValue: Long) =
                this@Settings.getLong(key) ?: defaultValue

        override fun getString(key: String, defaultValue: String?) =
                this@Settings.getString(key) ?: defaultValue

        override fun isWritable(key: String) =
                this@Settings.isWritable(key)

        override fun remove(key: String) =
                this@Settings.remove(key)

        override fun putBoolean(key: String, value: Boolean) =
                this@Settings.putBoolean(key, value)

        override fun putString(key: String, value: String?) =
                this@Settings.putString(key, value)

        override fun putInt(key: String, value: Int) =
                this@Settings.putInt(key, value)

        override fun putLong(key: String, value: Long) =
                this@Settings.putLong(key, value)

        override fun registerObserver(observer: ISettingsObserver) {
            observers += WeakReference(observer)
        }

        override fun unregisterObserver(observer: ISettingsObserver) {
            observers.removeAll { it.get() == observer }
        }

    }

    override fun onBind(intent: Intent?) = binder


    class Stub(
            delegate: ISettings,
            private val context: Context,
            private val serviceConn: ServiceConnection
    ): ISettings by delegate, Closeable {

        override fun close() {
            try {
                serviceConn.let { context.unbindService(it) }
            } catch(e: Exception) {
                Logger.log.log(Level.SEVERE, "Couldn't unbind Settings service", e)
            }
        }

    }

    companion object {

        fun getInstance(context: Context): Stub? {
            if (Looper.getMainLooper().thread == Thread.currentThread())
                throw IllegalStateException("Must not be called from main thread")

            var service: ISettings? = null
            val serviceLock = Object()
            val serviceConn = object: ServiceConnection {
                override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                    synchronized(serviceLock) {
                        service = ISettings.Stub.asInterface(binder)
                        serviceLock.notify()
                    }
                }
                override fun onServiceDisconnected(name: ComponentName) {
                    service = null
                }
            }

            if (!context.bindService(Intent(context, Settings::class.java), serviceConn, Context.BIND_AUTO_CREATE or Context.BIND_IMPORTANT))
                return null

            synchronized(serviceLock) {
                if (service == null)
                    try {
                        serviceLock.wait()
                    } catch(e: InterruptedException) {
                    }

                if (service == null) {
                    try {
                        context.unbindService(serviceConn)
                    } catch (e: IllegalArgumentException) {
                    }
                    return null
                }
            }

            return Stub(service!!, context, serviceConn)
        }

    }

}
