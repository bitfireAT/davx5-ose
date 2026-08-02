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
 * To run an operation that must not be canceled together with UI state (like `viewModelScope`),
 * wrap only that operation in `applicationScope.async { }.await()` – don't launch the whole
 * UI-updating coroutine in the application scope.
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
