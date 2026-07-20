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
    val priority: Int?

    /**
     * Run during [at.bitfire.davdroid.CoreApp.onCreate].
     *
     * Keep the runtime of this method at a minimum; it directly increases app startup
     * time which is a critical measure.
     *
     * If ever possible, implementations should do work asynchronously by launching a
     * coroutine in [at.bitfire.davdroid.di.qualifier.ApplicationScope] and doing it there.
     * (Don't forget to off-load blocking code to the I/O dispatcher.)
     */
    fun onAppCreate()


}