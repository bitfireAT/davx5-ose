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
import androidx.annotation.MainThread
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NavUtils
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModelProviders
import at.bitfire.davdroid.Constants
import at.bitfire.davdroid.R
import at.bitfire.davdroid.databinding.ActivityCreateCalendarBinding
import at.bitfire.davdroid.model.AppDatabase
import at.bitfire.davdroid.model.Collection
import at.bitfire.davdroid.model.Service
import at.bitfire.ical4android.DateUtils
import com.jaredrummler.android.colorpicker.ColorPickerDialog
import com.jaredrummler.android.colorpicker.ColorPickerDialogListener
import kotlinx.android.synthetic.main.activity_create_calendar.*
import net.fortuna.ical4j.model.Calendar
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
        (intent?.getParcelableExtra(EXTRA_ACCOUNT) as? Account)?.let {
            model.initialize(it)
        }

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

        val args = Bundle()

        val parent = model.homeSets.value?.getItem(model.idxHomeSet.value!!) ?: return
        args.putString(CreateCollectionFragment.ARG_URL, parent.url.resolve(UUID.randomUUID().toString() + "/").toString())

        val displayName = model.displayName.value
        if (displayName.isNullOrBlank()) {
            model.displayNameError.value = getString(R.string.create_collection_display_name_required)
            ok = false
        } else {
            args.putString(CreateCollectionFragment.ARG_DISPLAY_NAME, displayName)
            model.displayNameError.value = null
        }

        StringUtils.trimToNull(model.description.value)?.let {
            args.putString(CreateCollectionFragment.ARG_DESCRIPTION, it)
        }

        model.color.value?.let {
            args.putInt(CreateCollectionFragment.ARG_COLOR, it)
        }

        val tzId = model.timezone.value
        if (tzId.isNullOrBlank()) {
            model.timezoneError.value = getString(R.string.create_calendar_time_zone_required)
            ok = false
        } else {
            DateUtils.tzRegistry.getTimeZone(tzId)?.let { tz ->
                val cal = Calendar()
                cal.components += tz.vTimeZone
                args.putString(CreateCollectionFragment.ARG_TIMEZONE, cal.toString())
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

        if (!supportsVEVENT || !supportsVTODO || !supportsVJOURNAL) {
            // only if there's at least one component set not supported; don't include
            // information about supported components otherwise (means: everything supported)
            args.putBoolean(CreateCollectionFragment.ARG_SUPPORTS_VEVENT, supportsVEVENT)
            args.putBoolean(CreateCollectionFragment.ARG_SUPPORTS_VTODO, supportsVTODO)
            args.putBoolean(CreateCollectionFragment.ARG_SUPPORTS_VJOURNAL, supportsVJOURNAL)
        }

        if (ok) {
            val frag = CreateCollectionFragment()
            args.putParcelable(CreateCollectionFragment.ARG_ACCOUNT, model.account)
            args.putString(CreateCollectionFragment.ARG_TYPE, Collection.TYPE_CALENDAR)
            frag.show(supportFragmentManager, null)
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

        var account: Account? = null

        val displayName = MutableLiveData<String>()
        val displayNameError = MutableLiveData<String>()

        val description = MutableLiveData<String>()
        val color = MutableLiveData<Int>()

        val homeSets = MutableLiveData<HomeSetAdapter>()
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
                val adapter = HomeSetAdapter(getApplication())

                val db = AppDatabase.getInstance(getApplication())
                db.serviceDao().getByAccountAndType(account.name, Service.TYPE_CALDAV)?.let { service ->
                    val homeSets = db.homeSetDao().getByService(service.id)
                    adapter.addAll(homeSets)
                }

                if (!adapter.isEmpty) {
                    homeSets.postValue(adapter)
                    idxHomeSet.postValue(0)
                }
            }
        }

    }

}
