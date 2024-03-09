/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid

import at.bitfire.davdroid.ui.intro.IntroPageFactory
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

interface StandardAndGplayFlavorModules {

    @Module
    @InstallIn(SingletonComponent::class)
    interface Global {
        @Binds
        fun introPageFactory(impl: StandardAndGplayIntroPageFactory): IntroPageFactory
    }

}