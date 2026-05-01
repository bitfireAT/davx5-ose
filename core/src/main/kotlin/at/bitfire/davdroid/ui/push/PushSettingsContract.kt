/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.push

import android.graphics.drawable.Drawable

interface PushSettingsContract {

    sealed interface State {
        data object Loading : State

        data class Content(
            val isPushEnabled: Boolean = true,
            val pushCollections: PushCollections = PushCollections.None,
            val selectedPushDistributor: String? = null,
            val defaultPushDistributor: String? = null,
            val pushDistributors: List<PushDistributorInfo> = emptyList()
        ) : State {
            fun couldSelectDefaultDistributor(packageName: String): Boolean {
                val unifiedPushDistributors = pushDistributors.filter {
                    // Filter out FCM (Play Services) distributor embedded in our own app DAVx5
                    it.packageName != packageName
                }
                return defaultPushDistributor == null && unifiedPushDistributors.size > 1
            }
        }
    }

    data class PushDistributorInfo(
        val packageName: String,
        val appName: String,
        val appIcon: Drawable?
    )

    sealed interface Event {
        data class PushEnabled(val enabled: Boolean) : Event
        data class PushDistributorSelected(val packageName: String) : Event

        data object DefaultPushDistributorSelected : Event
    }

    enum class PushCollections {
        All,     // All collections are push-capable
        Some,    // Some collections are push-capable
        None;    // Zero collections are push-capable
    }
}