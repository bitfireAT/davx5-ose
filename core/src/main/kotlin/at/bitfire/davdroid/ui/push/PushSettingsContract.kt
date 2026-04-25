/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.push

import android.graphics.drawable.Drawable

interface PushSettingsContract {

    data class State(
        val isPushEnabled: Boolean = true,
        val selectedPushDistributor: String? = null,
        val defaultPushDistributor: String? = null,
        val pushDistributors: List<PushDistributorInfo> = emptyList(),
    )

    data class PushDistributorInfo(
        val packageName: String,
        val appName: String,
        val appIcon: Drawable?,
    )

    sealed interface Event {
        data class PushEnabled(val enabled: Boolean) : Event
        data class PushDistributorSelected(val packageName: String) : Event
    }
}