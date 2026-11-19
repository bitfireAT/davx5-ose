/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.davx5.feature.featureflags.template.alwayson

import com.davx5.feature.featureflags.api.FeatureFlag
import com.davx5.feature.featureflags.api.FeatureFlags
import com.davx5.feature.featureflags.api.FeatureName
import com.davx5.feature.featureflags.template.api.Template

object TemplateFeatureFlag : FeatureFlag {
    override val name: FeatureName
        get() = FeatureFlags.Template

    override val isEnabled: Boolean = true
}
