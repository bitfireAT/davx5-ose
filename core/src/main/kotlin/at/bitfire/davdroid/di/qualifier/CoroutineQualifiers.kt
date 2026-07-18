/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.di.qualifier

import javax.inject.Qualifier


// CoroutineScope qualifiers

/**
 * A [kotlinx.coroutines.CoroutineScope] that lives for as long as the application process.
 *
 * Its default dispatcher is only a safe fallback (off the main thread) — callers must choose
 * their own dispatcher explicitly when calling `.launch(...)` on it instead of relying on that
 * default.
 */
@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class ApplicationScope


// CoroutineDispatcher qualifiers

@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class IoDispatcher
