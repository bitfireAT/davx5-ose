/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.ui

import android.accounts.Account
import android.app.LoaderManager
import android.content.AsyncTaskLoader
import android.content.Context
import android.content.Intent
import android.content.Loader
import android.os.Bundle
import android.support.v4.app.NavUtils
import android.support.v7.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem
import android.widget.ArrayAdapter
import at.bitfire.davdroid.R
import at.bitfire.davdroid.model.CollectionInfo
import at.bitfire.davdroid.model.ServiceDB
import kotlinx.android.synthetic.main.activity_create_address_book.*
import okhttp3.HttpUrl
import org.apache.commons.lang3.StringUtils
import java.util.*

class CreateAddressBookActivity: AppCompatActivity(), LoaderManager.LoaderCallbacks<CreateAddressBookActivity.AccountInfo> {

    companion object {
        const val EXTRA_ACCOUNT = "account"
    }

    private lateinit var account: Account


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        account = intent.getParcelableExtra(EXTRA_ACCOUNT)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        setContentView(R.layout.activity_create_address_book)

        loaderManager.initLoader(0, intent.extras, this)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.activity_create_collection, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem) =
            if (item.itemId == android.R.id.home) {
                val intent = Intent(this, AccountActivity::class.java)
                intent.putExtra(AccountActivity.EXTRA_ACCOUNT, account)
                NavUtils.navigateUpTo(this, intent)
                true
            } else
                false

    fun onCreateCollection(item: MenuItem) {
        val homeSet = home_sets.selectedItem as String

        var ok = true
        HttpUrl.parse(homeSet)?.let {
            val info = CollectionInfo(it.resolve(UUID.randomUUID().toString() + "/").toString())
            info.displayName = display_name.text.toString()
            if (info.displayName.isNullOrBlank()) {
                display_name.error = getString(R.string.create_collection_display_name_required)
                ok = false
            }

            info.description = StringUtils.trimToNull(description.text.toString())

            if (ok) {
                info.type = CollectionInfo.Type.ADDRESS_BOOK
                CreateCollectionFragment.newInstance(account, info).show(supportFragmentManager, null)
            }
        }
    }


    override fun onCreateLoader(id: Int, args: Bundle?) = AccountInfoLoader(this, account)

    override fun onLoadFinished(loader: Loader<AccountInfo>, info: AccountInfo?) {
        info?.let {
            home_sets.adapter = ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, it.homeSets)
        }
    }

    override fun onLoaderReset(loader: Loader<AccountInfo>) {
    }

    class AccountInfo {
        val homeSets = LinkedList<String>()
    }

    class AccountInfoLoader(
            context: Context,
            val account: Account
    ): AsyncTaskLoader<AccountInfo>(context) {

        override fun onStartLoading() = forceLoad()

        override fun loadInBackground(): AccountInfo? {
            val info = AccountInfo()
            ServiceDB.OpenHelper(context).use { dbHelper ->
                // find DAV service and home sets
                val db = dbHelper.readableDatabase
                db.query(ServiceDB.Services._TABLE, arrayOf(ServiceDB.Services.ID),
                        "${ServiceDB.Services.ACCOUNT_NAME}=? AND ${ServiceDB.Services.SERVICE}=?",
                        arrayOf(account.name, ServiceDB.Services.SERVICE_CARDDAV), null, null, null).use { cursor ->
                    if (!cursor.moveToNext())
                        return null
                    val strServiceID = cursor.getString(0)

                    db.query(ServiceDB.HomeSets._TABLE, arrayOf(ServiceDB.HomeSets.URL),
                            "${ServiceDB.HomeSets.SERVICE_ID}=?", arrayOf(strServiceID), null, null, null).use { c ->
                        while (c.moveToNext())
                            info.homeSets += c.getString(0)
                    }
                }
            }
            return info
        }
    }

}
