/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.account

import android.accounts.Account
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.core.app.TaskStackBuilder

class CreateAddressBookActivity: CreateCollectionActivity() {

    companion object {
        const val EXTRA_ACCOUNT = "account"
    }

    val account by lazy {
        intent.getParcelableExtra<Account>(EXTRA_ACCOUNT) ?: throw IllegalArgumentException("EXTRA_ACCOUNT must be set")
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            CreateAddressBookScreen(
                account = account,
                onNavUp = ::onSupportNavigateUp,
                onFinish = ::finish
            )
            /*model.createCollectionResult.observeAsState().value?.let { result ->
                if (result.isEmpty)
                    finish()
                else
                    ExceptionInfoDialog(
                        exception = result.get(),
                        onDismiss = {
                            isCreating = false
                            model.createCollectionResult.value = null
                        }
                    )
            }*/
        }
    }

    override fun supportShouldUpRecreateTask(targetIntent: Intent) = true

    override fun onPrepareSupportNavigateUpTaskStack(builder: TaskStackBuilder) {
        builder.editIntentAt(builder.intentCount - 1)?.putExtra(AccountActivity.EXTRA_ACCOUNT, account)
    }

}