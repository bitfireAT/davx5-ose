/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.ui.setup

import android.accounts.Account
import android.accounts.AccountManager
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.os.AsyncTask
import android.os.Bundle
import android.provider.CalendarContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import at.bitfire.davdroid.Constants
import at.bitfire.davdroid.DavService
import at.bitfire.davdroid.InvalidAccountException
import at.bitfire.davdroid.R
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.model.ServiceDB.*
import at.bitfire.davdroid.resource.LocalTaskList
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.settings.Settings
import at.bitfire.ical4android.TaskProvider
import at.bitfire.vcard4android.GroupMethod
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.login_account_details.*
import kotlinx.android.synthetic.main.login_account_details.view.*
import java.lang.ref.WeakReference
import java.util.logging.Level

class AccountDetailsFragment: Fragment() {

    companion object {
        const val KEY_CONFIG = "config"

        fun newInstance(config: DavResourceFinder.Configuration): AccountDetailsFragment {
            val frag = AccountDetailsFragment()
            val args = Bundle(1)
            args.putParcelable(KEY_CONFIG, config)
            frag.arguments = args
            return frag
        }
    }


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val v = inflater.inflate(R.layout.login_account_details, container, false)

        v.back.setOnClickListener { _ ->
            requireFragmentManager().popBackStack()
        }

        val args = requireNotNull(arguments)
        val config = args.getParcelable(KEY_CONFIG) as DavResourceFinder.Configuration

        v.account_name.setText(config.calDAV?.email ?:
                config.credentials.userName ?:
                config.credentials.certificateAlias)

        val settings = Settings.getInstance(requireActivity())

        // CardDAV-specific
        v.carddav.visibility = if (config.cardDAV != null) View.VISIBLE else View.GONE
        if (settings.has(AccountSettings.KEY_CONTACT_GROUP_METHOD))
            v.contact_group_method.isEnabled = false

        v.create_account.setOnClickListener { _ ->
            val name = v.account_name.text.toString()
            if (name.isEmpty())
                v.account_name.error = getString(R.string.login_account_name_required)
            else {
                val idx = view!!.contact_group_method.selectedItemPosition
                val groupMethodName = resources.getStringArray(R.array.settings_contact_group_method_values)[idx]

                v.create_account.visibility = View.GONE
                v.create_account_progress.visibility = View.VISIBLE

                CreateAccountTask(requireActivity().applicationContext, WeakReference(requireActivity()),
                        name,
                        args.getParcelable(KEY_CONFIG) as DavResourceFinder.Configuration,
                        GroupMethod.valueOf(groupMethodName)).execute()
            }
        }

        val forcedGroupMethod = settings.getString(AccountSettings.KEY_CONTACT_GROUP_METHOD)?.let { GroupMethod.valueOf(it) }
        if (forcedGroupMethod != null) {
            v.contact_group_method.isEnabled = false
            for ((i, method) in resources.getStringArray(R.array.settings_contact_group_method_values).withIndex()) {
                if (method == forcedGroupMethod.name) {
                    v.contact_group_method.setSelection(i)
                    break
                }
            }
        } else
            v.contact_group_method.isEnabled = true

        return v
    }


    @SuppressLint("StaticFieldLeak")    // we'll only keep the application Context
    class CreateAccountTask(
            private val appContext: Context,
            private val activityRef: WeakReference<Activity>,

            private val accountName: String,
            private val config: DavResourceFinder.Configuration,
            private val groupMethod: GroupMethod
    ): AsyncTask<Void, Void, Boolean>() {

        override fun doInBackground(vararg params: Void?): Boolean {
            val account = Account(accountName, appContext.getString(R.string.account_type))

            // create Android account
            val userData = AccountSettings.initialUserData(config.credentials)
            Logger.log.log(Level.INFO, "Creating Android account with initial config", arrayOf(account, userData))

            val accountManager = AccountManager.get(appContext)
            if (!accountManager.addAccountExplicitly(account, config.credentials.password, userData))
                return false

            // add entries for account to service DB
            Logger.log.log(Level.INFO, "Writing account configuration to database", config)
            OpenHelper(appContext).use { dbHelper ->
                val db = dbHelper.writableDatabase
                try {
                    val accountSettings = AccountSettings(appContext, account)

                    val refreshIntent = Intent(appContext, DavService::class.java)
                    refreshIntent.action = DavService.ACTION_REFRESH_COLLECTIONS

                    if (config.cardDAV != null) {
                        // insert CardDAV service
                        val id = insertService(db, accountName, Services.SERVICE_CARDDAV, config.cardDAV)

                        // start CardDAV service detection (refresh collections)
                        refreshIntent.putExtra(DavService.EXTRA_DAV_SERVICE_ID, id)
                        appContext.startService(refreshIntent)

                        // initial CardDAV account settings
                        accountSettings.setGroupMethod(groupMethod)

                        // contact sync is automatically enabled by isAlwaysSyncable="true" in res/xml/sync_address_books.xml
                        accountSettings.setSyncInterval(appContext.getString(R.string.address_books_authority), Constants.DEFAULT_SYNC_INTERVAL)
                    } else
                        ContentResolver.setIsSyncable(account, appContext.getString(R.string.address_books_authority), 0)

                    if (config.calDAV != null) {
                        // insert CalDAV service
                        val id = insertService(db, accountName, Services.SERVICE_CALDAV, config.calDAV)

                        // start CalDAV service detection (refresh collections)
                        refreshIntent.putExtra(DavService.EXTRA_DAV_SERVICE_ID, id)
                        appContext.startService(refreshIntent)

                        // calendar sync is automatically enabled by isAlwaysSyncable="true" in res/xml/sync_calendars.xml
                        accountSettings.setSyncInterval(CalendarContract.AUTHORITY, Constants.DEFAULT_SYNC_INTERVAL)

                        // enable task sync if OpenTasks is installed
                        // further changes will be handled by PackageChangedReceiver
                        if (LocalTaskList.tasksProviderAvailable(appContext)) {
                            ContentResolver.setIsSyncable(account, TaskProvider.ProviderName.OpenTasks.authority, 1)
                            accountSettings.setSyncInterval(TaskProvider.ProviderName.OpenTasks.authority, Constants.DEFAULT_SYNC_INTERVAL)
                        }
                    } else
                        ContentResolver.setIsSyncable(account, CalendarContract.AUTHORITY, 0)

                } catch(e: InvalidAccountException) {
                    Logger.log.log(Level.SEVERE, "Couldn't access account settings", e)
                    return false
                }
            }
            return true
        }

        override fun onPostExecute(result: Boolean) {
            activityRef.get()?.let { activity ->
                if (result) {
                    activity.setResult(Activity.RESULT_OK)
                    activity.finish()
                } else {
                    Snackbar.make(activity.findViewById(android.R.id.content), R.string.login_account_not_created, Snackbar.LENGTH_LONG).show()

                    activity.create_account.visibility = View.VISIBLE
                    activity.create_account_progress.visibility = View.GONE
                }
            }
        }

        private fun insertService(db: SQLiteDatabase, accountName: String, service: String, info: DavResourceFinder.Configuration.ServiceInfo): Long {
            // insert service
            val serviceValues = ContentValues(3)
            serviceValues.put(Services.ACCOUNT_NAME, accountName)
            serviceValues.put(Services.SERVICE, service)
            info.principal?.let {
                serviceValues.put(Services.PRINCIPAL, it.toString())
            }
            val serviceID = db.insertWithOnConflict(Services._TABLE, null, serviceValues, SQLiteDatabase.CONFLICT_REPLACE)

            // insert home sets
            for (homeSet in info.homeSets) {
                val homeSetValues = ContentValues(2)
                homeSetValues.put(HomeSets.SERVICE_ID, serviceID)
                homeSetValues.put(HomeSets.URL, homeSet.toString())
                db.insertWithOnConflict(HomeSets._TABLE, null, homeSetValues, SQLiteDatabase.CONFLICT_REPLACE)
            }

            // insert collections
            for (collection in info.collections.values) {
                val collectionValues = collection.toDB()
                collectionValues.put(Collections.SERVICE_ID, serviceID)
                db.insertWithOnConflict(Collections._TABLE, null, collectionValues, SQLiteDatabase.CONFLICT_REPLACE)
            }

            return serviceID
        }

    }

}