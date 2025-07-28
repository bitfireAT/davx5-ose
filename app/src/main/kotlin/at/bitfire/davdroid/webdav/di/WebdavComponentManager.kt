/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.webdav.di

import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class WebdavComponentManager @Inject constructor(
    private val componentBuilder: Provider<WebdavComponent.Builder>
) {

    private var _component: WebdavComponent? = null

    @Synchronized
    fun getComponent(): WebdavComponent {
        // return cached component, if available
        _component?.let { return it }

        // create and cache new component
        val newComponent = componentBuilder.get().build()
        _component = newComponent
        return newComponent
    }

    @Synchronized
    fun resetComponent() {
        _component = null
    }

}