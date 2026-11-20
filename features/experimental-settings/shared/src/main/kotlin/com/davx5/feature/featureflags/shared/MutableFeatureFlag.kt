/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.davx5.feature.featureflags.shared

import com.davx5.feature.featureflags.api.FeatureFlag
import kotlinx.coroutines.flow.Flow

interface MutableFeatureFlag : FeatureFlag {
    override var isEnabled: Boolean
    val isEnabledFlow: Flow<Boolean>
}
