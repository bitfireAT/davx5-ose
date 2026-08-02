/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.account

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.TaskStackBuilder
import at.bitfire.davdroid.accounts.AccountId
import at.bitfire.davdroid.accounts.AccountIdIntentSerializer
import at.bitfire.davdroid.ui.account.AccountActivity.Companion.editAccountActivityIntent
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class CollectionActivity: AppCompatActivity() {

    companion object {
        private const val EXTRA_ACCOUNT = "account"
        private const val EXTRA_COLLECTION_ID = "collection_id"
        
        fun createIntent(context: Context, accountId: AccountId, collectionId: Long): Intent {
            return Intent(context, CollectionActivity::class.java).apply {
                AccountIdIntentSerializer.addExtra(this, EXTRA_ACCOUNT, accountId)
                putExtra(EXTRA_COLLECTION_ID, collectionId)
            }
        }
    }

    val accountId by lazy {
        AccountIdIntentSerializer.fromIntent(intent, EXTRA_ACCOUNT) 
            ?: throw IllegalArgumentException("EXTRA_ACCOUNT must be set")
    }
    val collectionId by lazy { intent.getLongExtra(EXTRA_COLLECTION_ID, -1) }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            CollectionScreen(
                collectionId = collectionId,
                onFinish = ::finish,
                onNavUp = ::onSupportNavigateUp
            )
        }
    }

    override fun supportShouldUpRecreateTask(targetIntent: Intent) = true

    override fun onPrepareSupportNavigateUpTaskStack(builder: TaskStackBuilder) {
        builder.editIntentAt(builder.intentCount - 1)?.editAccountActivityIntent(accountId)
    }

}