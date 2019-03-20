/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.ui

import android.accounts.Account
import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.Filter
import android.widget.SpinnerAdapter
import androidx.annotation.MainThread
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NavUtils
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import at.bitfire.davdroid.Constants
import at.bitfire.davdroid.R
import at.bitfire.davdroid.databinding.ActivityCreateCalendarBinding
import at.bitfire.davdroid.model.CollectionInfo
import at.bitfire.davdroid.model.ServiceDB
import at.bitfire.ical4android.DateUtils
import com.jaredrummler.android.colorpicker.ColorPickerDialog
import com.jaredrummler.android.colorpicker.ColorPickerDialogListener
import kotlinx.android.synthetic.main.activity_create_calendar.*
import net.fortuna.ical4j.model.Calendar
import okhttp3.HttpUrl
import org.apache.commons.lang3.StringUtils
import java.util.*
import kotlin.concurrent.thread

class CreateCalendarActivity: AppCompatActivity(), ColorPickerDialogListener {

    companion object {
        const val EXTRA_ACCOUNT = "account"
    }

    private lateinit var model: Model

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        model = ViewModelProviders.of(this).get(Model::class.java)
        (intent?.extras?.getParcelable(EXTRA_ACCOUNT) as? Account)?.let {
            model.initialize(it)
        }
        model.homeSets.observe(this, Observer {
            if (it.isEmpty)
                // no known homesets, we don't know where to create the calendar
                finish()
        })

        val binding = DataBindingUtil.setContentView<ActivityCreateCalendarBinding>(this, R.layout.activity_create_calendar)
        binding.lifecycleOwner = this
        binding.model = model

        binding.color.setOnClickListener { _ ->
            ColorPickerDialog.newBuilder()
                    .setShowAlphaSlider(false)
                    .setColor((color.background as ColorDrawable).color)
                    .show(this)
        }

        binding.timezone.setAdapter(model.timezones)
    }

    override fun onColorSelected(dialogId: Int, rgb: Int) {
        model.color.value = rgb
    }

    override fun onDialogDismissed(dialogId: Int) {
        // color selection dismissed
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.activity_create_collection, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem) =
            if (item.itemId == android.R.id.home) {
                val intent = Intent(this, AccountActivity::class.java)
                intent.putExtra(AccountActivity.EXTRA_ACCOUNT, model.account)
                NavUtils.navigateUpTo(this, intent)
                true
            } else
                false

    fun onCreateCollection(item: MenuItem) {
        var ok = true

        val parent = model.homeSets.value?.getItem(model.idxHomeSet.value!!) as String? ?: return
        HttpUrl.parse(parent)?.let { parentUrl ->
            val info = CollectionInfo(parentUrl.resolve(UUID.randomUUID().toString() + "/")!!)

            val displayName = model.displayName.value
            if (displayName.isNullOrBlank()) {
                model.displayNameError.value = getString(R.string.create_collection_display_name_required)
                ok = false
            } else {
                info.displayName = displayName
                model.displayNameError.value = null
            }

            info.description = StringUtils.trimToNull(model.description.value)
            info.color = model.color.value

            val tzId = model.timezone.value
            if (tzId.isNullOrBlank()) {
                model.timezoneError.value = getString(R.string.create_calendar_time_zone_required)
                ok = false
            } else {
                DateUtils.tzRegistry.getTimeZone(tzId)?.let { tz ->
                    val cal = Calendar()
                    cal.components += tz.vTimeZone
                    info.timeZone = cal.toString()
                }
                model.timezoneError.value = null
            }

            val supportsVEVENT = model.supportVEVENT.value ?: false
            val supportsVTODO = model.supportVTODO.value ?: false
            val supportsVJOURNAL = model.supportVJOURNAL.value ?: false
            if (!supportsVEVENT && !supportsVTODO && !supportsVJOURNAL) {
                ok = false
                model.typeError.value = ""
            } else
                model.typeError.value = null

            info.type = CollectionInfo.Type.CALENDAR
            info.supportsVEVENT = supportsVEVENT
            info.supportsVTODO = supportsVTODO
            info.supportsVJOURNAL = supportsVJOURNAL

            if (ok)
                CreateCollectionFragment.newInstance(model.account!!, info).show(supportFragmentManager, null)
        }
    }

    class TimeZoneAdapter(
            context: Context
    ): ArrayAdapter<String>(context, android.R.layout.simple_list_item_1, android.R.id.text1) {

        val tz = TimeZone.getAvailableIDs()

        override fun getFilter(): Filter {
            return object: Filter() {
                override fun performFiltering(constraint: CharSequence?): FilterResults {
                    val filtered = constraint?.let {
                        tz.filter { it.contains(constraint, true) }
                    } ?: listOf()
                    val results = FilterResults()
                    results.values = filtered
                    results.count = filtered.size
                    return results
                }
                override fun publishResults(constraint: CharSequence?, results: FilterResults) {
                    clear()
                    @Suppress("UNCHECKED_CAST") addAll(results.values as List<String>)
                    if (results.count >= 0)
                        notifyDataSetChanged()
                    else
                        notifyDataSetInvalidated()
                }
            }
        }

    }


    class Model(
            application: Application
    ): AndroidViewModel(application) {

        class TimeZoneInfo(
                val id: String,
                val displayName: String
        ) {
            override fun toString() = id
        }

        var account: Account? = null

        val displayName = MutableLiveData<String>()
        val displayNameError = MutableLiveData<String>()

        val description = MutableLiveData<String>()
        val color = MutableLiveData<Int>()

        val homeSets = MutableLiveData<SpinnerAdapter>()
        val idxHomeSet = MutableLiveData<Int>()

        val timezones = TimeZoneAdapter(application)
        val timezone = MutableLiveData<String>()
        val timezoneError = MutableLiveData<String>()

        val typeError = MutableLiveData<String>()
        val supportVEVENT = MutableLiveData<Boolean>()
        val supportVTODO = MutableLiveData<Boolean>()
        val supportVJOURNAL = MutableLiveData<Boolean>()

        @MainThread
        fun initialize(account: Account) {
            if (this.account != null)
                return
            this.account = account

            color.value = Constants.DAVDROID_GREEN_RGBA

            timezone.value = TimeZone.getDefault().id

            supportVEVENT.value = true
            supportVTODO.value = true
            supportVJOURNAL.value = true

            thread {
                // load account info
                ServiceDB.OpenHelper(getApplication()).use { dbHelper ->
                    val adapter = HomesetAdapter(getApplication())
                    val db = dbHelper.readableDatabase
                    db.query(ServiceDB.Services._TABLE, arrayOf(ServiceDB.Services.ID),
                            "${ServiceDB.Services.ACCOUNT_NAME}=? AND ${ServiceDB.Services.SERVICE}=?",
                            arrayOf(account.name, ServiceDB.Services.SERVICE_CALDAV), null, null, null).use { cursor ->
                        if (cursor.moveToNext()) {
                            val strServiceID = cursor.getString(0)
                            db.query(ServiceDB.HomeSets._TABLE, arrayOf(ServiceDB.HomeSets.URL),
                                    "${ServiceDB.HomeSets.SERVICE_ID}=?", arrayOf(strServiceID), null, null, null).use { c ->
                                while (c.moveToNext())
                                    adapter.add(c.getString(0))
                            }
                        }
                    }
                    if (!adapter.isEmpty) {
                        homeSets.postValue(adapter)
                        idxHomeSet.postValue(0)
                    }
                }
            }
        }

    }

}
