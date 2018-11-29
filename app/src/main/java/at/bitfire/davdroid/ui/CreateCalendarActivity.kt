/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.ui

import android.accounts.Account
import android.content.Context
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.core.app.NavUtils
import androidx.appcompat.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem
import android.widget.ArrayAdapter
import androidx.loader.app.LoaderManager
import androidx.loader.content.AsyncTaskLoader
import androidx.loader.content.Loader
import at.bitfire.davdroid.R
import at.bitfire.davdroid.model.CollectionInfo
import at.bitfire.davdroid.model.ServiceDB
import at.bitfire.ical4android.DateUtils
import kotlinx.android.synthetic.main.activity_create_calendar.*
import net.fortuna.ical4j.model.Calendar
import okhttp3.HttpUrl
import org.apache.commons.lang3.StringUtils
import yuku.ambilwarna.AmbilWarnaDialog
import java.util.*

class CreateCalendarActivity: AppCompatActivity(), LoaderManager.LoaderCallbacks<CreateCalendarActivity.AccountInfo> {

    companion object {
        const val EXTRA_ACCOUNT = "account"
    }

    private lateinit var account: Account

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        account = intent.extras.getParcelable(EXTRA_ACCOUNT)!!

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        setContentView(R.layout.activity_create_calendar)
        color.setOnClickListener { _ ->
            AmbilWarnaDialog(this, (color.background as ColorDrawable).color, true, object: AmbilWarnaDialog.OnAmbilWarnaListener {
                override fun onCancel(dialog: AmbilWarnaDialog) {}
                override fun onOk(dialog: AmbilWarnaDialog, rgb: Int) =
                        color.setBackgroundColor(rgb)
            }).show()
        }

        LoaderManager.getInstance(this).initLoader(0, null, this)
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
            val info = CollectionInfo(it.resolve(UUID.randomUUID().toString() + "/")!!)
            info.displayName = display_name.text.toString()
            if (info.displayName.isNullOrBlank()) {
                display_name.error = getString(R.string.create_collection_display_name_required)
                ok = false
            }

            info.description = StringUtils.trimToNull(description.text.toString())
            info.color = (color.background as ColorDrawable).color

            DateUtils.tzRegistry.getTimeZone(time_zone.selectedItem as String)?.let { tz ->
                val cal = Calendar()
                cal.components += tz.vTimeZone
                info.timeZone = cal.toString()
            }

            when (type.checkedRadioButtonId) {
                R.id.type_events ->
                    info.supportsVEVENT = true
                R.id.type_tasks ->
                    info.supportsVTODO = true
                R.id.type_events_and_tasks -> {
                    info.supportsVEVENT = true
                    info.supportsVTODO = true
                }
            }

            if (ok) {
                info.type = CollectionInfo.Type.CALENDAR
                CreateCollectionFragment.newInstance(account, info).show(supportFragmentManager, null)
            }
        }
    }


    override fun onCreateLoader(id: Int, args: Bundle?) = AccountInfoLoader(this, account)

    override fun onLoadFinished(loader: Loader<AccountInfo>, info: AccountInfo?) {
        val timeZones = TimeZone.getAvailableIDs()
        time_zone.adapter = ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, timeZones)

        // select system time zone
        val defaultTimeZone = TimeZone.getDefault().id
        for (i in 0 until timeZones.size)
            if (timeZones[i] == defaultTimeZone) {
                time_zone.setSelection(i)
                break
            }

        info?.let {
            home_sets.adapter = ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, info.homeSets)
        }
    }

    override fun onLoaderReset(loader: Loader<AccountInfo>) {}


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
                val db = dbHelper.readableDatabase
                db.query(ServiceDB.Services._TABLE, arrayOf(ServiceDB.Services.ID),
                        "${ServiceDB.Services.ACCOUNT_NAME}=? AND ${ServiceDB.Services.SERVICE}=?",
                        arrayOf(account.name, ServiceDB.Services.SERVICE_CALDAV), null, null, null).use { cursor ->
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
