/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui.account

import android.accounts.Account
import android.content.Context
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.*
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NavUtils
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import at.bitfire.davdroid.Constants
import at.bitfire.davdroid.R
import at.bitfire.davdroid.databinding.ActivityCreateCalendarBinding
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.db.HomeSet
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.ui.HomeSetAdapter
import at.bitfire.ical4android.util.DateUtils
import com.jaredrummler.android.colorpicker.ColorPickerDialog
import com.jaredrummler.android.colorpicker.ColorPickerDialogListener
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.fortuna.ical4j.model.Calendar
import org.apache.commons.lang3.StringUtils
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class CreateCalendarActivity: AppCompatActivity(), ColorPickerDialogListener {

    companion object {
        const val EXTRA_ACCOUNT = "account"
    }

    @Inject lateinit var modelFactory: Model.Factory
    val model by viewModels<Model>() {
        object: ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val account = intent.getParcelableExtra<Account>(EXTRA_ACCOUNT) ?: throw IllegalArgumentException("EXTRA_ACCOUNT must be set")
                return modelFactory.create(account) as T
            }
        }
    }

    lateinit var binding: ActivityCreateCalendarBinding


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding = DataBindingUtil.setContentView(this, R.layout.activity_create_calendar)
        binding.lifecycleOwner = this
        binding.model = model

        binding.color.setOnClickListener {
            ColorPickerDialog.newBuilder()
                    .setShowAlphaSlider(false)
                    .setColor((binding.color.background as ColorDrawable).color)
                    .show(this)
        }

        val homeSetAdapter = HomeSetAdapter(this)
        model.homeSets.observe(this) { homeSets ->
            homeSetAdapter.clear()
            if (homeSets.isNotEmpty()) {
                homeSetAdapter.addAll(homeSets)
                val firstHomeSet = homeSets.first()
                binding.homeset.setText(firstHomeSet.url.toString(), false)
                model.homeSet = firstHomeSet
            }
        }
        binding.homeset.setAdapter(homeSetAdapter)
        binding.homeset.setOnItemClickListener { parent, _, position, _ ->
            model.homeSet = parent.getItemAtPosition(position) as HomeSet?
        }

        binding.timezone.setAdapter(TimeZoneAdapter(this))
        binding.timezone.setText(TimeZone.getDefault().id, false)
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
        args.putString(CreateCollectionFragment.ARG_SERVICE_TYPE, Service.TYPE_CALDAV)

        val parent = model.homeSet
        if (parent != null) {
            binding.homesetLayout.error = null
            args.putString(
                CreateCollectionFragment.ARG_URL,
                parent.url.resolve(UUID.randomUUID().toString() + "/").toString()
            )
        } else {
            binding.homesetLayout.error = getString(R.string.create_collection_home_set_required)
            ok = false
        }

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

        val tzId = binding.timezone.text?.toString()
        if (tzId.isNullOrBlank())
            ok = false
        else {
            DateUtils.ical4jTimeZone(tzId)?.let { tz ->
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

        if (supportsVEVENT || supportsVTODO || supportsVJOURNAL) {
            // only if there's at least one component set not supported; don't include
            // information about supported components otherwise (means: everything supported)
            args.putBoolean(CreateCollectionFragment.ARG_SUPPORTS_VEVENT, supportsVEVENT)
            args.putBoolean(CreateCollectionFragment.ARG_SUPPORTS_VTODO, supportsVTODO)
            args.putBoolean(CreateCollectionFragment.ARG_SUPPORTS_VJOURNAL, supportsVJOURNAL)
        }

        if (ok) {
            args.putParcelable(CreateCollectionFragment.ARG_ACCOUNT, model.account)
            args.putString(CreateCollectionFragment.ARG_TYPE, Collection.TYPE_CALENDAR)
            val frag = CreateCollectionFragment()
            frag.arguments = args
            frag.show(supportFragmentManager, null)
        }
    }


    class TimeZoneAdapter(context: Context): ArrayAdapter<String>(context, R.layout.text_list_item, android.R.id.text1)  {

        init {
            addAll(TimeZone.getAvailableIDs().toList())
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val tzId = getItem(position)!!
            val tz = ZoneId.of(tzId)

            val v: View = convertView ?: LayoutInflater.from(context).inflate(R.layout.text_list_item, parent, false)
            v.findViewById<TextView>(android.R.id.text1).text = tz.id
            v.findViewById<TextView>(android.R.id.text2).text = tz.getDisplayName(TextStyle.FULL, Locale.getDefault())

            return v
        }

        override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup) =
            getView(position, convertView, parent)

    }


    class Model @AssistedInject constructor(
        val db: AppDatabase,
        @Assisted val account: Account
    ): ViewModel() {

        @AssistedFactory
        interface Factory {
            fun create(account: Account): Model
        }

        val displayName = MutableLiveData<String>()
        val displayNameError = MutableLiveData<String>()

        val description = MutableLiveData<String>()
        val color = MutableLiveData<Int>()

        val homeSets = MutableLiveData<List<HomeSet>>()
        var homeSet: HomeSet? = null

        val timezoneError = MutableLiveData<String>()

        val typeError = MutableLiveData<String>()
        val supportVEVENT = MutableLiveData<Boolean>()
        val supportVTODO = MutableLiveData<Boolean>()
        val supportVJOURNAL = MutableLiveData<Boolean>()

        init {
            color.value = Constants.DAVDROID_GREEN_RGBA

            supportVEVENT.value = true
            supportVTODO.value = true
            supportVJOURNAL.value = true

            viewModelScope.launch(Dispatchers.IO) {
                // load account info
                db.serviceDao().getByAccountAndType(account.name, Service.TYPE_CALDAV)?.let { service ->
                    homeSets.postValue(db.homeSetDao().getBindableByService(service.id))
                }
            }
        }

    }

}