/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.settings

import androidx.appcompat.app.AppCompatDelegate

object Settings {

    const val DISTRUST_SYSTEM_CERTIFICATES = "distrust_system_certs"

    const val PROXY_TYPE = "proxy_type"         // Integer
    const val PROXY_TYPE_SYSTEM = -1
    const val PROXY_TYPE_NONE = 0
    const val PROXY_TYPE_HTTP = 1
    const val PROXY_TYPE_SOCKS = 2
    const val PROXY_HOST = "proxy_host"         // String
    const val PROXY_PORT = "proxy_port"         // Integer

    /**
     * Whether to ignore VPNs at internet connection detection, true by default because VPN connections
     * seem to include "VALIDATED" by default even without actual internet connection
     */
    const val IGNORE_VPN_NETWORK_CAPABILITY = "ignore_vpns"         // Boolean

    /**
     * Default sync interval (Long), in seconds.
     * Used to initialize an account.
     */
    const val DEFAULT_SYNC_INTERVAL = "default_sync_interval"

    /**
     * Preferred theme (light/dark). Value must be one of [AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM]
     * (default if setting is missing), [AppCompatDelegate.MODE_NIGHT_NO] or [AppCompatDelegate.MODE_NIGHT_YES].
     */
    const val PREFERRED_THEME = "preferred_theme"
    const val PREFERRED_THEME_DEFAULT = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM

    /**
     * Selected tasks app. When at least one tasks app is installed, this setting is set to its authority.
     * In case of multiple available tasks app, the user can choose one and this setting will reflect the selected one.
     *
     * If no tasks app is available, this setting is not set.
     */
    const val SELECTED_TASKS_PROVIDER = "preferred_tasks_provider"

    /** whether collections are automatically selected for synchronization after their initial detection */
    const val PRESELECT_COLLECTIONS = "preselect_collections"
    /** collections are not automatically selected for synchronization */
    const val PRESELECT_COLLECTIONS_NONE = 0
    /** all collections (except those matching [PRESELECT_COLLECTIONS_EXCLUDED]) are automatically selected for synchronization */
    const val PRESELECT_COLLECTIONS_ALL = 1
    /** personal collections (except those matching [PRESELECT_COLLECTIONS_EXCLUDED]) are automatically selected for synchronization */
    const val PRESELECT_COLLECTIONS_PERSONAL = 2

    /** regular expression to match URLs of collections to be excluded from pre-selection */
    const val PRESELECT_COLLECTIONS_EXCLUDED = "preselect_collections_excluded"


    /** whether all address books are forced to be read-only */
    const val FORCE_READ_ONLY_ADDRESSBOOKS = "force_read_only_addressbooks"
    
}
