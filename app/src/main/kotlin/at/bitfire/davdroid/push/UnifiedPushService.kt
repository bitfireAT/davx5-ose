/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.push

import dagger.Lazy
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.unifiedpush.android.connector.FailedReason
import org.unifiedpush.android.connector.PushService
import org.unifiedpush.android.connector.data.PushEndpoint
import org.unifiedpush.android.connector.data.PushMessage
import java.util.logging.Logger
import javax.inject.Inject

/**
 * Entry point for UnifiedPush.
 *
 * Calls [PushRegistrationManager] for most tasks, except incoming push messages,
 * which are handled directly.
 */
@AndroidEntryPoint
class UnifiedPushService : PushService() {

    @Inject
    lateinit var logger: Logger

    @Inject
    lateinit var pushMessageHandler: Lazy<PushMessageHandler>

    @Inject
    lateinit var pushRegistrationManager: Lazy<PushRegistrationManager>

    val serviceScope = CoroutineScope(SupervisorJob())


    override fun onNewEndpoint(endpoint: PushEndpoint, instance: String) {
        val serviceId = instance.toLongOrNull() ?: return
        logger.warning("Got UnifiedPush endpoint for service $serviceId: ${endpoint.url}")

        // register new endpoint at CalDAV/CardDAV servers
        serviceScope.launch {
            pushRegistrationManager.get().processSubscription(serviceId, endpoint)
        }
    }

    override fun onRegistrationFailed(reason: FailedReason, instance: String) {
        val serviceId = instance.toLongOrNull() ?: return
        logger.warning("UnifiedPush registration failed for service $serviceId: $reason")

        // unregister subscriptions
        serviceScope.launch {
            pushRegistrationManager.get().removeSubscription(serviceId)
        }
    }

    override fun onUnregistered(instance: String) {
        val serviceId = instance.toLongOrNull() ?: return
        logger.warning("UnifiedPush unregistered for service $serviceId")

        serviceScope.launch {
            pushRegistrationManager.get().removeSubscription(serviceId)
        }
    }

    override fun onMessage(message: PushMessage, instance: String) {
        serviceScope.launch {
            pushMessageHandler.get().processMessage(message, instance)
        }
    }

}