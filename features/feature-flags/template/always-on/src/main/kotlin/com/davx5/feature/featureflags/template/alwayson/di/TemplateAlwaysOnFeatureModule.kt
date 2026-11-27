/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.davx5.feature.featureflags.template.alwayson.di

import com.davx5.feature.featureflags.api.FeatureFlag
import com.davx5.feature.featureflags.template.alwayson.TemplateFeatureFlag
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
class TemplateAlwaysOnFeatureModule {
    @Provides
    @IntoSet
    fun featureFlag(): FeatureFlag = TemplateFeatureFlag
}
