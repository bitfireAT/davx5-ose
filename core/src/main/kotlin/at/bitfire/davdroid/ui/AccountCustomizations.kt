/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui

import android.accounts.Account
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import dagger.BindsOptionalOf
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

interface AccountCustomizations {
    /**
     * Allows to customize the image of every account. This is the image that shows above the account name in the accounts list.
     *
     * By default
     * ```kotlin
     * Icon(
     *     imageVector = Icons.Default.AccountCircle,
     *     contentDescription = null,
     *     modifier = Modifier
     *         .align(Alignment.CenterHorizontally)
     *         .size(48.dp)
     * )
     * ```
     */
    @Composable
    fun ColumnScope.AccountImage(account: Account)
}

@Module
@InstallIn(SingletonComponent::class)
interface AccountCustomizationsModule {
    @BindsOptionalOf
    fun accountCustomizations(): AccountCustomizations
}
