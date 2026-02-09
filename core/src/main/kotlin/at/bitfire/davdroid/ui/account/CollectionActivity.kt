/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.account

import android.accounts.Account
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.TaskStackBuilder
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class CollectionActivity: AppCompatActivity() {

    companion object {
        const val EXTRA_ACCOUNT = "account"
        const val EXTRA_COLLECTION_ID = "collection_id"
    }

    val account by lazy { intent.getParcelableExtra<Account>(EXTRA_ACCOUNT)!! }
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
        builder.editIntentAt(builder.intentCount - 1)?.putExtra(AccountActivity.EXTRA_ACCOUNT, account)
    }

}