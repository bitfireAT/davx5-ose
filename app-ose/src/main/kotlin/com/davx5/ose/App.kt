/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.davx5.ose

import androidx.work.Configuration
import at.bitfire.davdroid.CoreApp
import dagger.hilt.android.HiltAndroidApp

/**
 * Actual implementation of Application, used for Hilt. Delegates to [CoreApp].
 */
@HiltAndroidApp
class App: CoreApp(), Configuration.Provider {

    /**
     * Required for Hilt/WorkManager integration, see:
     * https://developer.android.com/training/dependency-injection/hilt-jetpack#workmanager
     *
     * This requires to remove the androidx.work.WorkManagerInitializer from App Startup
     * in the AndroidManifest, see:
     * https://developer.android.com/develop/background-work/background-tasks/persistent/configuration/custom-configuration#remove-default
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

}