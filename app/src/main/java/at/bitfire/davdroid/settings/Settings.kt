/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.settings

import androidx.appcompat.app.AppCompatDelegate

object Settings {

    const val BATTERY_OPTIMIZATION = "battery_optimization"
    const val FOREGROUND_SERVICE = "foreground_service"

    const val DISTRUST_SYSTEM_CERTIFICATES = "distrust_system_certs"

    const val PROXY_TYPE = "proxy_type"
    const val PROXY_TYPE_SYSTEM = -1
    const val PROXY_TYPE_NONE = 0
    const val PROXY_TYPE_HTTP = 1
    const val PROXY_TYPE_SOCKS = 2
    const val PROXY_HOST = "proxy_host"
    const val PROXY_PORT = "proxy_port"

    /**
     * Default sync interval (long), in seconds.
     * Used to initialize an account.
     */
    const val DEFAULT_SYNC_INTERVAL = "default_sync_interval"

    /**
     * Preferred theme (light/dark). Value must be one of [AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM]
     * (default if setting is missing), [AppCompatDelegate.MODE_NIGHT_NO] or [AppCompatDelegate.MODE_NIGHT_YES].
     */
    const val PREFERRED_THEME = "preferred_theme"
    const val PREFERRED_THEME_DEFAULT = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM

    const val PREFERRED_TASKS_PROVIDER = "preferred_tasks_provider"

    /** whether detected collections are selected for synchronization for default */
    const val SYNC_ALL_COLLECTIONS = "sync_all_collections"

    /** whether all address books are forced to be read-only */
    const val FORCE_READ_ONLY_ADDRESSBOOKS = "force_read_only_addressbooks"
    
}
