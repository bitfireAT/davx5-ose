/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.davx5.feature.preconfiguredsettings.shared

import io.ktor.http.HttpStatusCode

class InvalidPreconfiguredSettingsException(
    val status: HttpStatusCode,
    val body: String?,
    cause: Throwable?
) : RuntimeException("The server responded with an error code, or invalid preconfigured settings.", cause)
