/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.account

import AccountOverview
import android.accounts.Account
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
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

        model.invalid.observe(this) { invalid ->
            if (invalid)
                // account does not exist anymore
                finish()
        }
        model.renameAccountError.observe(this) { error ->
            if (error != null) {
                Toast.makeText(this, error, Toast.LENGTH_LONG).show()
                model.renameAccountError.value = null
            }
        }

        setContent {
            val cardDavSvc by model.cardDavSvc.observeAsState()
            val canCreateAddressBook by model.canCreateAddressBook.observeAsState(false)
            val cardDavRefreshing by model.cardDavRefreshing.observeAsState(false)
            val cardDavSyncPending by model.cardDavSyncPending.observeAsState(false)
            val cardDavSyncing by model.cardDavSyncing.observeAsState(false)
            val cardDavProgress: AccountProgress = when {
                cardDavRefreshing || cardDavSyncing -> AccountProgress.Active
                cardDavSyncPending -> AccountProgress.Pending
                else -> AccountProgress.Idle
            }
            val addressBooks by model.addressBooksPager.observeAsState()

            val calDavSvc by model.calDavSvc.observeAsState()
            val canCreateCalendar by model.canCreateCalendar.observeAsState(false)
            val calDavRefreshing by model.calDavRefreshing.observeAsState(false)
            val calDavSyncPending by model.calDavSyncPending.observeAsState(false)
            val calDavSyncing by model.calDavSyncing.observeAsState(false)
            val calDavProgress: AccountProgress = when {
                calDavRefreshing || calDavSyncing -> AccountProgress.Active
                calDavSyncPending -> AccountProgress.Pending
                else -> AccountProgress.Idle
            }
            val calendars by model.calendarsPager.observeAsState()
            val subscriptions by model.webcalPager.observeAsState()

            var installIcsx5 by remember { mutableStateOf(false) }

            AccountOverview(
                account = model.account,
                showOnlyPersonal =
                    model.showOnlyPersonal.observeAsState(
                        AccountSettings.ShowOnlyPersonal(onlyPersonal = false, locked = true)
                    ).value,
                onSetShowOnlyPersonal = {
                    model.setShowOnlyPersonal(it)
                },
                hasCardDav = cardDavSvc != null,
                canCreateAddressBook = canCreateAddressBook,
                cardDavProgress = cardDavProgress,
                cardDavRefreshing = cardDavRefreshing,
                addressBooks = addressBooks?.flow?.collectAsLazyPagingItems(),
                hasCalDav = calDavSvc != null,
                canCreateCalendar = canCreateCalendar,
                calDavProgress = calDavProgress,
                calDavRefreshing = calDavRefreshing,
                calendars = calendars?.flow?.collectAsLazyPagingItems(),
                subscriptions = subscriptions?.flow?.collectAsLazyPagingItems(),
                onUpdateCollectionSync = { collectionId, sync ->
                    model.setCollectionSync(collectionId, sync)
                },
                onChangeForceReadOnly = { id, forceReadOnly ->
                    model.setCollectionForceReadOnly(id, forceReadOnly)
                },
                onSubscribe = { item ->
                    installIcsx5 = !subscribeWebcal(item)
                },
                installIcsx5 = installIcsx5,
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
                onNavUp = ::onSupportNavigateUp
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