/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.startup

interface StartupPlugin {

    companion object {
        const val PRIORITY_DEFAULT = 100
        const val PRIORITY_HIGHEST = 0
    }

    /**
     * Runs synchronously during [com.davx5.ose.App.onCreate]. Use only for tasks that must be completed before
     * the app can run. Causes the app to start slower.
     *
     * Will be run before [onAppCreateAsync].
     */
    fun onAppCreate()

    /**
     * Priority of this plugin's [onAppCreate]. Lower values are executed first.
     */
    fun priority(): Int


    /**
     * Runs asynchronously after [com.davx5.ose.App.onCreate]. Use for tasks that can be run in the background.
     *
     * Will be run after [onAppCreate].
     *
     * The coroutine scope will usually be `GlobalScope` (which has no end because we don't get
     * a signal before the app is terminated) on the default dispatcher, but can be a custom scope
     * for testing.
     */
    suspend fun onAppCreateAsync()

    /**
     * Priority of this plugin's [onAppCreateAsync]. Lower values are executed first.
     */
    fun priorityAsync(): Int

}