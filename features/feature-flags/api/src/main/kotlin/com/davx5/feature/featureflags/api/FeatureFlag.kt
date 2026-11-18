/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.davx5.feature.featureflags.api

interface FeatureFlag {
    val name: FeatureName
    val isEnabled: Boolean
}
