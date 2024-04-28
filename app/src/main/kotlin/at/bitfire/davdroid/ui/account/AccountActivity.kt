/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.account

import AccountScreen
import android.accounts.Account
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import at.bitfire.davdroid.db.Collection
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AccountActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ACCOUNT = "account"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val account = intent.getParcelableExtra(EXTRA_ACCOUNT) as? Account
            ?: throw IllegalArgumentException("AccountActivity requires EXTRA_ACCOUNT")

        setContent {
            AccountScreen(account = account)
        }

        /*setContent {
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

            AccountScreen(
                accountName = model.account.name,
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
        }*/
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