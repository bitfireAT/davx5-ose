/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.account

import AccountOverview
import android.accounts.Account
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.servicedetection.RefreshCollectionsWorker
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.syncadapter.OneTimeSyncWorker
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class AccountActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ACCOUNT = "account"
    }

    @Inject
    lateinit var modelFactory: AccountModel.Factory
    val model by viewModels<AccountModel> {
        val account = intent.getParcelableExtra(EXTRA_ACCOUNT) as? Account
            ?: throw IllegalArgumentException("AccountActivity requires EXTRA_ACCOUNT")
        object: ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T: ViewModel> create(modelClass: Class<T>) =
                modelFactory.create(account) as T
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val invalidAccount by model.invalidAccount.collectAsStateWithLifecycle(false)
            val cardDavSvc by model.cardDavSvc.collectAsStateWithLifecycle(null)
            val canCreateAddressBook by model.canCreateAddressBook.collectAsStateWithLifecycle(false)
            val cardDavProgress by model.cardDavProgress.collectAsStateWithLifecycle(AccountProgress.Idle)
            val addressBooks by model.addressBooksPager.collectAsState(null)

            val calDavSvc by model.calDavSvc.collectAsStateWithLifecycle(null)
            val canCreateCalendar by model.canCreateCalendar.collectAsStateWithLifecycle(false)
            val calDavProgress by model.calDavProgress.collectAsStateWithLifecycle(AccountProgress.Idle)
            val calendars by model.calendarsPager.collectAsStateWithLifecycle(null)
            val subscriptions by model.webcalPager.collectAsStateWithLifecycle(null)

            var noWebcalApp by remember { mutableStateOf(false) }

            AccountOverview(
                account = model.account,
                error = model.error,
                resetError = model::resetError,
                invalidAccount = invalidAccount,
                showOnlyPersonal = model.showOnlyPersonal.collectAsStateWithLifecycle(
                    initialValue = AccountSettings.ShowOnlyPersonal(onlyPersonal = false, locked = false)
                ).value,
                onSetShowOnlyPersonal = {
                    model.setShowOnlyPersonal(it)
                },
                hasCardDav = cardDavSvc != null,
                canCreateAddressBook = canCreateAddressBook,
                cardDavProgress = cardDavProgress,
                addressBooks = addressBooks?.flow?.collectAsLazyPagingItems(),
                hasCalDav = calDavSvc != null,
                canCreateCalendar = canCreateCalendar,
                calDavProgress = calDavProgress,
                calendars = calendars?.flow?.collectAsLazyPagingItems(),
                subscriptions = subscriptions?.flow?.collectAsLazyPagingItems(),
                onUpdateCollectionSync = { collectionId, sync ->
                    model.setCollectionSync(collectionId, sync)
                },
                onChangeForceReadOnly = { id, forceReadOnly ->
                    model.setCollectionForceReadOnly(id, forceReadOnly)
                },
                onSubscribe = { item ->
                    noWebcalApp = !subscribeWebcal(item)
                },
                noWebcalApp = noWebcalApp,
                resetNoWebcalApp = { noWebcalApp = false },
                onRefreshCollections = {
                    cardDavSvc?.let { svc ->
                        RefreshCollectionsWorker.enqueue(this@AccountActivity, svc.id)
                    }
                    calDavSvc?.let { svc ->
                        RefreshCollectionsWorker.enqueue(this@AccountActivity, svc.id)
                    }
                },
                onSync = {
                    OneTimeSyncWorker.enqueueAllAuthorities(this, model.account, manual = true)
                },
                onAccountSettings = {
                    val intent = Intent(this, AccountSettingsActivity::class.java)
                    intent.putExtra(AccountSettingsActivity.EXTRA_ACCOUNT, model.account)
                    startActivity(intent, null)
                },
                onRenameAccount = { newName ->
                    model.renameAccount(newName)
                },
                onDeleteAccount = {
                    model.deleteAccount()
                },
                onNavUp = ::onSupportNavigateUp,
                onFinish = ::finish
            )
        }
    }

    /**
     * Subscribes to a Webcal using a compatible app like ICSx5.
     *
     * @return true if a compatible Webcal app is installed, false otherwise
     */
    private fun subscribeWebcal(item: Collection): Boolean {
        // subscribe
        var uri = Uri.parse(item.source.toString())
        when {
            uri.scheme.equals("http", true) -> uri = uri.buildUpon().scheme("webcal").build()
            uri.scheme.equals("https", true) -> uri = uri.buildUpon().scheme("webcals").build()
        }

        val intent = Intent(Intent.ACTION_VIEW, uri)
        item.displayName?.let { intent.putExtra("title", it) }
        item.color?.let { intent.putExtra("color", it) }

        if (packageManager.resolveActivity(intent, 0) != null) {
            startActivity(intent)
            return true
        }

        return false
    }

}