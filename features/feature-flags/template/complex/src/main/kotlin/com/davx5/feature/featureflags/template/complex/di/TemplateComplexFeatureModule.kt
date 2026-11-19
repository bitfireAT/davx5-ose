/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.davx5.feature.featureflags.template.complex.di

import com.davx5.feature.featureflags.api.FeatureFlag
import com.davx5.feature.featureflags.template.complex.TemplateFeatureFlag
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
interface TemplateComplexFeatureModule {
    @Binds
    @IntoSet
    fun featureFlag(impl: TemplateFeatureFlag): FeatureFlag
}
