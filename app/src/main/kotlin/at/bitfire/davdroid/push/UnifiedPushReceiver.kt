/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.push

import android.content.Context
import android.provider.CalendarContract
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.repository.AccountRepository
import at.bitfire.davdroid.repository.PreferenceRepository
import at.bitfire.davdroid.syncadapter.OneTimeSyncWorker
import dagger.hilt.android.AndroidEntryPoint
import org.unifiedpush.android.connector.MessagingReceiver
import javax.inject.Inject

@AndroidEntryPoint
class UnifiedPushReceiver: MessagingReceiver() {

    @Inject
    lateinit var accountRepository: AccountRepository

    @Inject
    lateinit var preferenceRepository: PreferenceRepository

    override fun onNewEndpoint(context: Context, endpoint: String, instance: String) {
        // remember new endpoint
        preferenceRepository.unifiedPushEndpoint(endpoint)

        // register new endpoint at CalDAV/CardDAV servers
        PushRegistrationWorker.enqueue(context)
    }

    override fun onUnregistered(context: Context, instance: String) {
        // reset known endpoint
        preferenceRepository.unifiedPushEndpoint(null)
    }

    override fun onMessage(context: Context, message: ByteArray, instance: String) {
        // received WebDAV Push notification, sync all accounts
        // TODO: sync only affected account / collection
        for (account in accountRepository.getAll())
            OneTimeSyncWorker.enqueue(context, account, CalendarContract.AUTHORITY)
    }

}