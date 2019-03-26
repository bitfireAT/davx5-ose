package at.bitfire.davdroid.ui

import android.Manifest
import android.accounts.Account
import android.accounts.AccountManager
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ContentResolver
import android.content.DialogInterface
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import at.bitfire.davdroid.DavUtils
import at.bitfire.davdroid.R
import at.bitfire.davdroid.closeCompat
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.model.ServiceDB
import at.bitfire.davdroid.resource.LocalAddressBook
import at.bitfire.davdroid.resource.LocalTaskList
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.ical4android.TaskProvider
import java.util.logging.Level

class RenameAccountFragment: DialogFragment() {

    companion object {

        const val ARG_ACCOUNT = "account"

        fun newInstance(account: Account): RenameAccountFragment {
            val fragment = RenameAccountFragment()
            val args = Bundle(1)
            args.putParcelable(ARG_ACCOUNT, account)
            fragment.arguments = args
            return fragment
        }

    }

    @SuppressLint("Recycle")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val oldAccount: Account = arguments!!.getParcelable(ARG_ACCOUNT)!!

        val editText = EditText(requireActivity())
        editText.setText(oldAccount.name)

        return AlertDialog.Builder(requireActivity())
                .setTitle(R.string.account_rename)
                .setMessage(R.string.account_rename_new_name)
                .setView(editText)
                .setPositiveButton(R.string.account_rename_rename, DialogInterface.OnClickListener { _, _ ->
                    val newName = editText.text.toString()

                    if (newName == oldAccount.name)
                        return@OnClickListener

                    // remember sync intervals
                    val oldSettings = AccountSettings(requireActivity(), oldAccount)
                    val authorities = arrayOf(
                            getString(R.string.address_books_authority),
                            CalendarContract.AUTHORITY,
                            TaskProvider.ProviderName.OpenTasks.authority
                    )
                    val syncIntervals = authorities.map { Pair(it, oldSettings.getSyncInterval(it)) }

                    val accountManager = AccountManager.get(activity)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                        accountManager.renameAccount(oldAccount, newName, {
                            // account has now been renamed
                            Logger.log.info("Updating account name references")

                            // cancel maybe running synchronization
                            ContentResolver.cancelSync(oldAccount, null)
                            for (addrBookAccount in accountManager.getAccountsByType(getString(R.string.account_type_address_book)))
                                ContentResolver.cancelSync(addrBookAccount, null)

                            // update account name references in database
                            ServiceDB.OpenHelper(requireActivity()).use { dbHelper ->
                                ServiceDB.onRenameAccount(dbHelper.writableDatabase, oldAccount.name, newName)
                            }

                            // update main account of address book accounts
                            if (ContextCompat.checkSelfPermission(requireActivity(), Manifest.permission.WRITE_CONTACTS) == PackageManager.PERMISSION_GRANTED)
                                try {
                                    requireActivity().contentResolver.acquireContentProviderClient(ContactsContract.AUTHORITY)?.let { provider ->
                                        for (addrBookAccount in accountManager.getAccountsByType(getString(R.string.account_type_address_book)))
                                            try {
                                                val addressBook = LocalAddressBook(requireActivity(), addrBookAccount, provider)
                                                if (oldAccount == addressBook.mainAccount)
                                                    addressBook.mainAccount = Account(newName, oldAccount.type)
                                            } finally {
                                                provider.closeCompat()
                                            }
                                    }
                                } catch(e: Exception) {
                                    Logger.log.log(Level.SEVERE, "Couldn't update address book accounts", e)
                                }

                            // calendar provider doesn't allow changing account_name of Events
                            // (all events will have to be downloaded again)

                            // update account_name of local tasks
                            try {
                                LocalTaskList.onRenameAccount(activity!!.contentResolver, oldAccount.name, newName)
                            } catch(e: Exception) {
                                Logger.log.log(Level.SEVERE, "Couldn't propagate new account name to tasks provider", e)
                            }

                            // retain sync intervals
                            val newAccount = Account(newName, oldAccount.type)
                            val newSettings = AccountSettings(requireActivity(), newAccount)
                            for ((authority, interval) in syncIntervals) {
                                if (interval == null)
                                    ContentResolver.setIsSyncable(newAccount, authority, 0)
                                else {
                                    ContentResolver.setIsSyncable(newAccount, authority, 1)
                                    newSettings.setSyncInterval(authority, interval)
                                }
                            }

                            // synchronize again
                            DavUtils.requestSync(activity!!, newAccount)
                        }, null)
                    activity!!.finish()
                })
                .setNegativeButton(android.R.string.cancel) { _, _ -> }
                .create()
    }
}