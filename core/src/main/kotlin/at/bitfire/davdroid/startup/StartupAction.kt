/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.startup

interface StartupAction {

    companion object {
        const val PRIORITY_FIRST = 0
        const val PRIORITY_DEFAULT = 100
    }

    /**
     * Priority of this action's [onAppCreate]. Lower values are executed first.
     * `null` if this action has no synchronous startup work.
     */
    val priority: Int? get() = null

    /**
     * Synchronous startup action that will be run during [at.bitfire.davdroid.CoreApp.onCreate]
     * and before [onAppCreateAsync].
     *
     * Will only be run when [priority] is not null (see there for order of execution).
     *
     * **Must only be used for tasks that must be completed before the app
     * can run. Causes the app to start slower.**
     */
    fun onAppCreate() {
        // default implementation is empty so that implementations don't have to override it
    }

    /**
     * Priority of this action's [onAppCreateAsync]. Lower values are executed first.
     * `null` if this action has no asynchronous startup work.
     */
    val priorityAsync: Int?
        get() = null

    /**
     * Runs on a background thread after [at.bitfire.davdroid.CoreApp.onCreate]. Use for startup tasks that
     * don't need to complete before the app can run.
     *
     * Will be run in a separate thread that is launched at the end of [at.bitfire.davdroid.CoreApp.onCreate].
     *
     * Will only be run if [priorityAsync] is not null (see there for order of execution).
     *
     * If the action uses coroutines, it shall choose its scope (like
     * [at.bitfire.davdroid.di.qualifier.ApplicationScope]) and dispatcher explicitly, so this method
     * is non-suspending.
     */
    fun onAppCreateAsync() {
        // default implementation is empty so that implementations don't have to override it
    }

}