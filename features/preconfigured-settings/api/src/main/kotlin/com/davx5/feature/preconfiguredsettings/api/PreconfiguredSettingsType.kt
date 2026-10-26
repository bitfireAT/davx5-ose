/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.davx5.feature.preconfiguredsettings.api

import javax.inject.Qualifier

/**
 * Qualifier annotation for dependency injection to mark the `LoginType` implementation for "preconfigured settings".
 */
@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class PreconfiguredSettingsType
