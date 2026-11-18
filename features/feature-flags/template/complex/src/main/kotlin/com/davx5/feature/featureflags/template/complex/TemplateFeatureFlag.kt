/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.davx5.feature.featureflags.template.complex

import at.bitfire.davdroid.repository.AccountRepository
import com.davx5.feature.featureflags.api.FeatureFlag
import com.davx5.feature.featureflags.api.FeatureFlags
import com.davx5.feature.featureflags.api.FeatureName
import com.davx5.feature.featureflags.template.api.Template
import javax.inject.Inject
import javax.inject.Provider

class TemplateFeatureFlag @Inject constructor(
    private val accountRepository: Provider<AccountRepository>,
) : FeatureFlag {
    override val name: FeatureName
        get() = FeatureFlags.Template

    override val isEnabled: Boolean
        get() {
            // TODO: Replace this with the actual logic to decide whether or not to enable the feature
            val accountRepository = accountRepository.get()
            return accountRepository.getAllBlocking().any { accountId ->
                accountRepository.getAccountNameBlocking(accountId) == "special account"
            }
        }
}
