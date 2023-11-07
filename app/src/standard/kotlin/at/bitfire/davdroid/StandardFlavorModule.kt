/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid

import at.bitfire.davdroid.ui.AboutActivity
import at.bitfire.davdroid.ui.AccountsDrawerHandler
import at.bitfire.davdroid.ui.StandardAccountsDrawerHandler
import at.bitfire.davdroid.ui.intro.IntroFragmentFactory
import at.bitfire.davdroid.ui.intro.PermissionsIntroFragment
import at.bitfire.davdroid.ui.intro.TasksIntroFragment
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
interface StandardFlavorModule {

    @Binds
    fun accountsDrawerHandler(handler: StandardAccountsDrawerHandler): AccountsDrawerHandler

}