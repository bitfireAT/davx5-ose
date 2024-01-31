/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.di

import dagger.hilt.DefineComponent
import dagger.hilt.components.SingletonComponent
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Scope
import javax.inject.Singleton

@Scope
@Retention(AnnotationRetention.BINARY)
annotation class SyncScoped

/**
 * Custom Hilt component for running syncs, lifetime managed by [SyncComponentManager].
 * Dependencies installed in this component and scoped with [SyncScoped] (like SyncValidators)
 * will have a lifetime of all active syncs.
 */
@SyncScoped
@DefineComponent(parent = SingletonComponent::class)
interface SyncComponent

@DefineComponent.Builder
interface SyncComponentBuilder {
    fun build(): SyncComponent
}

/**
 * Manages the lifecycle of [SyncComponent] by using [WeakReference].
 *
 * @sample at.bitfire.davdroid.syncadapter.LicenseValidator
 * @sample at.bitfire.davdroid.syncadapter.PaymentValidator
 */
@Singleton
class SyncComponentManager @Inject constructor(
    val provider: Provider<SyncComponentBuilder>
) {

    private var componentRef: WeakReference<SyncComponent>? = null

    /**
     * Returns a [SyncComponent]. When there is already a known [SyncComponent],
     * it will be used. Otherwise, a new one will be created and returned.
     *
     * It is then stored using a [WeakReference] – so as long as the component
     * stays in memory, it will always be returned. When it's not used anymore
     * by anyone, it can be removed by garbage collection. After this, it will be
     * created again when [get] is called.
     *
     * @return singleton [SyncComponent] (will be garbage collected when not referenced anymore)
     */
    @Synchronized
    fun get(): SyncComponent {
        val component = componentRef?.get()

        // check for cached component
        if (component != null)
            return component

        // cached component not available, build new one
        val newComponent = provider.get().build()
        componentRef = WeakReference(newComponent)
        return newComponent
    }

}