/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.settings

import androidx.annotation.AnyThread
import java.io.Writer

/**
 * Defines a settings provider, which provides settings from a certain source
 * to the [SettingsManager].
 *
 * Implementations must be thread-safe and synchronize get/put operations on their own.
 */
interface SettingsProvider {

    fun interface OnChangeListener {
        /**
         * Called when a setting has changed.
         *
         * @param key The key of the setting that has changed, or null if the key is not
         * available. In this case, the listener should reload all settings.
         */
        fun onSettingsChanged(key: String?)
    }


    /**
     * Whether this provider can write settings.
     *
     * If this method returns false, the put...() methods will never be called for this provider.
     *
     * @return true = this provider provides read/write settings;
     *         false = this provider provides read-only settings
     */
    fun canWrite(): Boolean

    /**
     * Closes the provider and releases resources.
     */
    fun close()

    /**
     * Sets an on-changed listener. The provider calls the listener whenever a setting
     * has changed.
     */
    fun setOnChangeListener(listener: OnChangeListener)

    @AnyThread
    fun forceReload()


    fun contains(key: String): Boolean

    fun getBoolean(key: String): Boolean?
    fun getInt(key: String): Int?
    fun getLong(key: String): Long?
    fun getString(key: String): String?

    fun putBoolean(key: String, value: Boolean?)
    fun putInt(key: String, value: Int?)
    fun putLong(key: String, value: Long?)
    fun putString(key: String, value: String?)

    fun remove(key: String)


    fun dump(writer: Writer)

}