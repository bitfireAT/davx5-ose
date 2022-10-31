/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui.account

import android.Manifest
import android.accounts.Account
import android.accounts.AccountManager
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ContentResolver
import android.content.Context
import android.content.DialogInterface
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.annotation.WorkerThread
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.bitfire.davdroid.DavUtils
import at.bitfire.davdroid.InvalidAccountException
import at.bitfire.davdroid.R
import at.bitfire.davdroid.closeCompat
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.resource.LocalAddressBook
import at.bitfire.davdroid.resource.LocalTaskList
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.syncadapter.AccountsUpdatedListener
import at.bitfire.ical4android.TaskProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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

        model.finished.observe(this, Observer {
            this@RenameAccountFragment.requireActivity().finish()
        })

        return MaterialAlertDialogBuilder(requireActivity())
                .setTitle(R.string.account_rename)
                .setMessage(R.string.account_rename_new_name)
                .setView(layout)
                .setPositiveButton(R.string.account_rename_rename, DialogInterface.OnClickListener { _, _ ->
                    val newName = editText.text.toString()
                    if (newName == oldAccount.name)
                        return@OnClickListener

                    model.renameAccount(oldAccount, newName)

                    requireActivity().finish()
                })
                .setNegativeButton(android.R.string.cancel) { _, _ -> }
                .create()
    }


    @HiltViewModel
    class Model @Inject constructor(
        @ApplicationContext val context: Context,
        val accountsUpdatedListener: AccountsUpdatedListener,
        val db: AppDatabase
    ): ViewModel() {

        val finished = MutableLiveData<Boolean>()

        fun renameAccount(oldAccount: Account, newName: String) {
            // remember sync intervals
            val oldSettings = try {
                AccountSettings(context, oldAccount)
            } catch (e: InvalidAccountException) {
                Toast.makeText(context, R.string.account_invalid, Toast.LENGTH_LONG).show()
                finished.value = true
                return
            }

            val authorities = arrayOf(
                    context.getString(R.string.address_books_authority),
                    CalendarContract.AUTHORITY,
                    TaskProvider.ProviderName.OpenTasks.authority
            )
            val syncIntervals = authorities.map { Pair(it, oldSettings.getSyncInterval(it)) }

            val accountManager = AccountManager.get(context)
            try {
                /* https://github.com/bitfireAT/davx5/issues/135
                Take AccountsUpdatedListenerLock so that the AccountsUpdateListener doesn't run while we rename the account
                because this can cause problems when:
                1. The account is renamed.
                2. The AccountsUpdateListener is called BEFORE the services table is updated.
                   → AccountsUpdateListener removes the "orphaned" services because they belong to the old account which doesn't exist anymore
                3. Now the services would be renamed, but they're not here anymore. */
                accountsUpdatedListener.mutex.acquire()

                accountManager.renameAccount(oldAccount, newName, {
                    if (it.result?.name == newName /* success */)
                        viewModelScope.launch(Dispatchers.Default + NonCancellable) {
                            onAccountRenamed(accountManager, oldAccount, newName, syncIntervals)

                            // release AccountsUpdatedListener mutex at the end of this async coroutine
                            accountsUpdatedListener.mutex.release()
                        } else
                            // release AccountsUpdatedListener mutex now
                            accountsUpdatedListener.mutex.release()


                }, null)
            } catch (e: Exception) {
                Logger.log.log(Level.WARNING, "Couldn't rename account", e)
                Toast.makeText(context, R.string.account_rename_couldnt_rename, Toast.LENGTH_LONG).show()
            }
        }

        @SuppressLint("Recycle")
        @WorkerThread
        fun onAccountRenamed(accountManager: AccountManager, oldAccount: Account, newName: String, syncIntervals: List<Pair<String, Long?>>) {
            // account has now been renamed
            Logger.log.info("Updating account name references")

            // cancel maybe running synchronization
            ContentResolver.cancelSync(oldAccount, null)
            for (addrBookAccount in accountManager.getAccountsByType(context.getString(R.string.account_type_address_book)))
                ContentResolver.cancelSync(addrBookAccount, null)

            // update account name references in database
            try {
                db.serviceDao().renameAccount(oldAccount.name, newName)
            } catch (e: Exception) {
                Toast.makeText(context, R.string.account_rename_couldnt_rename, Toast.LENGTH_LONG).show()
                Logger.log.log(Level.SEVERE, "Couldn't update service DB", e)
                return
            }

            // update main account of address book accounts
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CONTACTS) == PackageManager.PERMISSION_GRANTED)
                try {
                    context.contentResolver.acquireContentProviderClient(ContactsContract.AUTHORITY)?.let { provider ->
                        try {
                            for (addrBookAccount in accountManager.getAccountsByType(context.getString(R.string.account_type_address_book))) {
                                val addressBook = LocalAddressBook(context, addrBookAccount, provider)
                                if (oldAccount == addressBook.mainAccount)
                                    addressBook.mainAccount = Account(newName, oldAccount.type)
                            }
                        } finally {
                            provider.closeCompat()
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
            DavUtils.requestSync(context, newAccount)
        }

    }

}