/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.davx5.feature.featureflags.template.api

import com.davx5.feature.featureflags.api.FeatureFlags
import com.davx5.feature.featureflags.api.FeatureName

/**
 * TODO: Describe feature flag
 */
val FeatureFlags.Template: FeatureName
    // TODO: Replace with a unique string
    get() = FeatureName("templateIdentifier")
