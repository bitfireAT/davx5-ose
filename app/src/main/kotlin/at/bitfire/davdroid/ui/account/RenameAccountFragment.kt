/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui.account

import android.Manifest
import android.accounts.Account
import android.accounts.AccountManager
import android.annotation.SuppressLint
import android.app.Application
import android.app.Dialog
import android.content.ContentResolver
import android.content.DialogInterface
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import at.bitfire.davdroid.*
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.resource.LocalAddressBook
import at.bitfire.davdroid.resource.LocalTaskList
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.syncadapter.AccountsCleanupWorker
import at.bitfire.davdroid.syncadapter.SyncWorker
import at.bitfire.ical4android.TaskProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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


    @SuppressLint("Recycle")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val oldAccount: Account = requireArguments().getParcelable(ARG_ACCOUNT)!!

        val editText = EditText(requireActivity()).apply {
            setText(oldAccount.name)
            requestFocus()
        }
        val layout = LinearLayout(requireContext())
        val density = requireActivity().resources.displayMetrics.density.toInt()
        layout.setPadding(8*density, 8*density, 8*density, 8*density)
        layout.addView(editText)

        model.errorMessage.observe(this) { msg ->
            // we use a Toast to show the error message because a Snackbar is not usable for the input dialog fragment
            Toast.makeText(requireActivity(), msg, Toast.LENGTH_LONG).show()
        }

        model.finishActivity.observe(this) {
            requireActivity().finish()
        }

        return MaterialAlertDialogBuilder(requireActivity())
                .setTitle(R.string.account_rename)
                .setMessage(R.string.account_rename_new_name)
                .setView(layout)
                .setPositiveButton(R.string.account_rename_rename, DialogInterface.OnClickListener { _, _ ->
                    val newName = editText.text.toString()
                    if (newName == oldAccount.name)
                        return@OnClickListener

                    model.renameAccount(oldAccount, newName)
                })
                .setNegativeButton(android.R.string.cancel) { _, _ -> }
                .create()
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

        @SuppressLint("Recycle")
        @WorkerThread
        fun onAccountRenamed(accountManager: AccountManager, oldAccount: Account, newName: String, syncIntervals: List<Pair<String, Long?>>) {
            // account has now been renamed
            Logger.log.info("Updating account name references")
            val context: Application = getApplication()

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