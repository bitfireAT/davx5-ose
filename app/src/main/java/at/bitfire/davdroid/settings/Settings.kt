package at.bitfire.davdroid.settings

import androidx.appcompat.app.AppCompatDelegate

object Settings {

    const val FOREGROUND_SERVICE = "foreground_service"

    const val DISTRUST_SYSTEM_CERTIFICATES = "distrust_system_certs"

    const val OVERRIDE_PROXY = "override_proxy"
    const val OVERRIDE_PROXY_HOST = "override_proxy_host"
    const val OVERRIDE_PROXY_PORT = "override_proxy_port"

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

}
