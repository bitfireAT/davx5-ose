/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.di.qualifier

import javax.inject.Qualifier

/**
 * Test-only: a [kotlinx.coroutines.CoroutineDispatcher] bound to the real main [android.os.Looper].
 * Production code uses [kotlinx.coroutines.Dispatchers.Main] directly instead.
 */
@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class RealMainDispatcher
