/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.di.qualifier

import javax.inject.Qualifier


// CoroutineScope qualifiers

/**
 * A [kotlinx.coroutines.CoroutineScope] that lives for as long as the application process.
 *
 * It is bound to [DefaultDispatcher] because that's the default for all standard coroutine
 * builders when nothing else is specified in the context.
 *
 * In order to use the application scope for operations that may still modify UI state
 * (like `viewModelScope`), explicitly launch the child in [UiDispatcher]. (Attention:
 * referenced UI objects can't be garbage-collected as long as they can be accessed.)
 */
@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class ApplicationScope


// CoroutineDispatcher qualifiers

/**
 * Same as [kotlinx.coroutines.Dispatchers.Default].
 *
 * Only for instrumented tests, it's wrapped in a shared test scheduler.
 */
@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class DefaultDispatcher

/**
 * Same as [kotlinx.coroutines.Dispatchers.IO].
 *
 * Only for instrumented tests, it's wrapped in a shared test scheduler.
 */
@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class IoDispatcher

/**
 * Same as `Dispatchers.Main.immediate`.
 */
@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class UiDispatcher
