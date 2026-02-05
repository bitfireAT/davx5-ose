/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.di.scope

import javax.inject.Qualifier

@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class LightColorScheme

@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class DarkColorScheme
