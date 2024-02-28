/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui.account

import android.Manifest
import android.accounts.Account
import android.accounts.AccountManager
import android.annotation.SuppressLint
import android.app.Application
import android.content.ContentResolver
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.AlertDialog
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import at.bitfire.davdroid.InvalidAccountException
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.resource.LocalAddressBook
import at.bitfire.davdroid.resource.LocalTaskList
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.syncadapter.AccountsCleanupWorker
import at.bitfire.davdroid.syncadapter.PeriodicSyncWorker
import at.bitfire.davdroid.syncadapter.SyncWorker
import at.bitfire.ical4android.TaskProvider
import com.google.accompanist.themeadapter.material.MdcTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import java.util.logging.Level
import javax.inject.Inject

@AndroidEntryPoint
class RenameAccountFragment: DialogFragment() {

    companion object {

        private const val ARG_ACCOUNT = "account"

        fun newInstance(account: Account): RenameAccountFragment {
            val fragment = RenameAccountFragment()
            val args = Bundle(1)
            args.putParcelable(ARG_ACCOUNT, account)
            fragment.arguments = args
            return fragment
        }

    }

    val model by viewModels<Model>()


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val oldAccount: Account = requireArguments().getParcelable(ARG_ACCOUNT)!!

        model.errorMessage.observe(this) { msg ->
            // we use a Toast to show the error message because a Snackbar is not usable for the input dialog fragment
            Toast.makeText(requireActivity(), msg, Toast.LENGTH_LONG).show()
        }

        model.finishActivity.observe(this) {
            requireActivity().finish()
        }

        return ComposeView(requireContext()).apply {
            // Dispose of the Composition when the view's LifecycleOwner is destroyed
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MdcTheme {
                    var accountName by remember { mutableStateOf(oldAccount.name) }
                    AlertDialog(
                        onDismissRequest = { dismiss() },
                        title = { Text(stringResource(R.string.account_rename)) },
                        text = { Column {
                            Text(
                                stringResource(R.string.account_rename_new_name_description),
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            TextField(
                                value = accountName,
                                onValueChange = { accountName = it },
                                label = { Text(stringResource(R.string.account_rename_new_name)) },
                            )
                        }},
                        confirmButton = {
                            TextButton(
                                onClick = { model.renameAccount(oldAccount, accountName) },
                                enabled = oldAccount.name != accountName
                            ) {
                                Text(stringResource(R.string.account_rename_rename).uppercase())
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { dismiss() }) {
                                Text(stringResource(android.R.string.cancel).uppercase())
                            }
                        },
                    )
                }
            }
        }
    }


    @HiltViewModel
    class Model @Inject constructor(
        application: Application,
        val db: AppDatabase
    ): AndroidViewModel(application) {

        val errorMessage = MutableLiveData<String>()
        val finishActivity = MutableLiveData<Boolean>()

        /**
         * Will try to rename the given account to given string
         *
         * @param oldAccount the account to be renamed
         * @param newName the new name
         */
        fun renameAccount(oldAccount: Account, newName: String) {
            val context: Application = getApplication()

            // remember sync intervals
            val oldSettings = try {
                AccountSettings(context, oldAccount)
            } catch (e: InvalidAccountException) {
                errorMessage.postValue(context.getString(R.string.account_invalid))
                finishActivity.value = true
                return
            }

            val authorities = arrayOf(
                context.getString(R.string.address_books_authority),
                CalendarContract.AUTHORITY,
                TaskProvider.ProviderName.OpenTasks.authority
            )
            val syncIntervals = authorities.map { Pair(it, oldSettings.getSyncInterval(it)) }

            val accountManager = AccountManager.get(context)
            // check whether name is already taken
            if (accountManager.getAccountsByType(context.getString(R.string.account_type)).map { it.name }.contains(newName)) {
                Logger.log.log(Level.WARNING, "Account with name \"$newName\" already exists")
                errorMessage.postValue(context.getString(R.string.account_rename_exists_already))
                return
            }

            try {
                /* https://github.com/bitfireAT/davx5/issues/135
                Lock accounts cleanup so that the AccountsCleanupWorker doesn't run while we rename the account
                because this can cause problems when:
                1. The account is renamed.
                2. The AccountsCleanupWorker is called BEFORE the services table is updated.
                   → AccountsCleanupWorker removes the "orphaned" services because they belong to the old account which doesn't exist anymore
                3. Now the services would be renamed, but they're not here anymore. */
                AccountsCleanupWorker.lockAccountsCleanup()

                // Renaming account
                accountManager.renameAccount(oldAccount, newName, @MainThread {
                    if (it.result?.name == newName /* account has new name -> success */)
                        viewModelScope.launch(Dispatchers.Default + NonCancellable) {
                            try {
                                onAccountRenamed(accountManager, oldAccount, newName, syncIntervals)
                            } finally {
                                // release AccountsCleanupWorker mutex at the end of this async coroutine
                                AccountsCleanupWorker.unlockAccountsCleanup()
                            }
                        } else
                            // release AccountsCleanupWorker mutex now
                            AccountsCleanupWorker.unlockAccountsCleanup()

                    // close AccountActivity with old name
                    finishActivity.postValue(true)
                }, null)
            } catch (e: Exception) {
                Logger.log.log(Level.WARNING, "Couldn't rename account", e)
                errorMessage.postValue(context.getString(R.string.account_rename_couldnt_rename))
            }
        }

        /**
         * Called when an account has been renamed.
         *
         * @param oldAccount the old account
         * @param newName the new account
         * @param syncIntervals map with entries of type (authority -> sync interval) of the old account
         */
        @SuppressLint("Recycle")
        @WorkerThread
        fun onAccountRenamed(accountManager: AccountManager, oldAccount: Account, newName: String, syncIntervals: List<Pair<String, Long?>>) {
            // account has now been renamed
            Logger.log.info("Updating account name references")
            val context: Application = getApplication()

            // disable periodic workers of old account
            syncIntervals.forEach { (authority, _) ->
                PeriodicSyncWorker.disable(context, oldAccount, authority)
            }

            // cancel maybe running synchronization
            SyncWorker.cancelSync(context, oldAccount)
            for (addrBookAccount in accountManager.getAccountsByType(context.getString(R.string.account_type_address_book)))
                SyncWorker.cancelSync(context, addrBookAccount)

            // update account name references in database
            try {
                db.serviceDao().renameAccount(oldAccount.name, newName)
            } catch (e: Exception) {
                Logger.log.log(Level.SEVERE, "Couldn't update service DB", e)
                errorMessage.postValue(context.getString(R.string.account_rename_couldnt_rename))
                return
            }

            // update main account of address book accounts
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CONTACTS) == PackageManager.PERMISSION_GRANTED)
                try {
                    context.contentResolver.acquireContentProviderClient(ContactsContract.AUTHORITY)?.use { provider ->
                        for (addrBookAccount in accountManager.getAccountsByType(context.getString(R.string.account_type_address_book))) {
                            val addressBook = LocalAddressBook(context, addrBookAccount, provider)
                            if (oldAccount == addressBook.mainAccount)
                                addressBook.mainAccount = Account(newName, oldAccount.type)
                        }
                    }
                } catch (e: Exception) {
                    Logger.log.log(Level.SEVERE, "Couldn't update address book accounts", e)
                }

            // calendar provider doesn't allow changing account_name of Events
            // (all events will have to be downloaded again)

            // update account_name of local tasks
            try {
                LocalTaskList.onRenameAccount(context, oldAccount.name, newName)
            } catch (e: Exception) {
                Logger.log.log(Level.SEVERE, "Couldn't propagate new account name to tasks provider", e)
            }

            // retain sync intervals
            val newAccount = Account(newName, oldAccount.type)
            val newSettings = AccountSettings(context, newAccount)
            for ((authority, interval) in syncIntervals) {
                if (interval == null)
                    ContentResolver.setIsSyncable(newAccount, authority, 0)
                else {
                    ContentResolver.setIsSyncable(newAccount, authority, 1)
                    newSettings.setSyncInterval(authority, interval)
                }
            }

            // synchronize again
            SyncWorker.enqueueAllAuthorities(context, newAccount)
        }

    }

}