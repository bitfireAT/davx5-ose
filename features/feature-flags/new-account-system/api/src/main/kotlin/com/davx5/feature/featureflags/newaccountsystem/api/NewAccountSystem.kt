/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.davx5.feature.featureflags.newaccountsystem.api

import com.davx5.feature.featureflags.api.FeatureFlags
import com.davx5.feature.featureflags.api.FeatureName

/**
 * Name of the feature flag for the new account system (storing accounts in the database).
 */
val FeatureFlags.NewAccountSystem: FeatureName
    get() = FeatureName("newAccountSystem")
