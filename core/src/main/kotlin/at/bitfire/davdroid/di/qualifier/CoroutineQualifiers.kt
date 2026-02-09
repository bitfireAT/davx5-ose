/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.di.qualifier

import javax.inject.Qualifier


// CoroutineScope qualifiers

@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class ApplicationScope


// CoroutineDispatcher qualifiers

@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class DefaultDispatcher

@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class IoDispatcher

@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class MainDispatcher

@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class SyncDispatcher
