/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.webdav.di

import dagger.hilt.DefineComponent
import dagger.hilt.components.SingletonComponent

@WebdavScoped
@DefineComponent(parent = SingletonComponent::class)
interface WebdavComponent {

    @DefineComponent.Builder
    interface Builder {
        fun build(): WebdavComponent
    }

}