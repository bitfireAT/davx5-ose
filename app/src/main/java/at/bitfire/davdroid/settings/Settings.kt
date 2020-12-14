package at.bitfire.davdroid.settings

object Settings {

    const val DISTRUST_SYSTEM_CERTIFICATES = "distrust_system_certs"

    const val OVERRIDE_PROXY = "override_proxy"
    const val OVERRIDE_PROXY_HOST = "override_proxy_host"
    const val OVERRIDE_PROXY_PORT = "override_proxy_port"

    /**
     * Default sync interval (long), in seconds.
     * Used to initialize an account.
     */
    const val DEFAULT_SYNC_INTERVAL = "default_sync_interval"

    const val PREFERRED_TASKS_PROVIDER = "preferred_tasks_provider"

}
