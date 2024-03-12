/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui.account

import android.accounts.Account
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Checkbox
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ExposedDropdownMenuBox
import androidx.compose.material.ExposedDropdownMenuDefaults
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Scaffold
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.app.NavUtils
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import at.bitfire.davdroid.Constants
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.db.HomeSet
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.ui.AppTheme
import at.bitfire.davdroid.ui.widget.ColorPickerDialog
import at.bitfire.davdroid.util.DavUtils
import at.bitfire.ical4android.util.DateUtils
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.AndroidEntryPoint
import java.util.TimeZone
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.fortuna.ical4j.model.Calendar
import org.apache.commons.lang3.StringUtils

@AndroidEntryPoint
class CreateCalendarActivity: AppCompatActivity() {

    companion object {
        const val EXTRA_ACCOUNT = "account"
    }

    @Inject lateinit var modelFactory: Model.Factory
    val model by viewModels<Model> {
        object: ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val account = intent.getParcelableExtra<Account>(EXTRA_ACCOUNT) ?: throw IllegalArgumentException("EXTRA_ACCOUNT must be set")
                return modelFactory.create(account) as T
            }
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AppTheme {
                val displayName by model.displayName.observeAsState(initial = "")
                val displayNameError by model.displayNameError.observeAsState()
                val color by model.color.observeAsState(initial = Constants.DAVDROID_GREEN_RGBA)
                val description by model.description.observeAsState()
                val homeSet by model.homeSet.observeAsState()
                val homeSets by model.homeSets.observeAsState(initial = emptyList())
                val timeZone by model.timeZone.observeAsState()
                val timeZones = remember { TimeZone.getAvailableIDs().map(TimeZone::getTimeZone) }
                val typeError by model.typeError.observeAsState()
                val supportVEVENT by model.supportVEVENT.observeAsState(initial = false)
                val supportVTODO by model.supportVTODO.observeAsState(initial = false)
                val supportVJOURNAL by model.supportVJOURNAL.observeAsState(initial = false)

                LaunchedEffect(timeZones) {
                    // Select default time zone
                    if (timeZone == null) model.timeZone.value = TimeZone.getDefault()
                }
                LaunchedEffect(homeSets) {
                    // Select default home set
                    if (homeSet == null) model.homeSet.value = homeSets.firstOrNull()
                }

                CreateCalendarLayout(
                    displayName = displayName,
                    onDisplayNameChange = model.displayName::setValue,
                    displayNameError = displayNameError,
                    color = color,
                    onColorChange = model.color::setValue,
                    description = description,
                    onDescriptionChange = model.description::setValue,
                    homeSet = homeSet,
                    homeSets = homeSets,
                    onHomeSetChange = model.homeSet::setValue,
                    timeZone = timeZone,
                    timeZones = timeZones,
                    onTimeZoneChange = model.timeZone::setValue,
                    typeError = typeError,
                    supportVEVENT = supportVEVENT,
                    onSupportVEVENTChange = model.supportVEVENT::setValue,
                    supportVTODO = supportVTODO,
                    onSupportVTODOChange = model.supportVTODO::setValue,
                    supportVJOURNAL = supportVJOURNAL,
                    onSupportVJOURNALChange = model.supportVJOURNAL::setValue
                ) {
                    onCreateCollection()
                }
            }
        }
    }

    private fun onCreateCollection() {
        var ok = true

        val args = Bundle()
        args.putString(CreateCollectionFragment.ARG_SERVICE_TYPE, Service.TYPE_CALDAV)

        val parent = model.homeSet.value
        if (parent != null) {
            model.homeSetError.value = null
            args.putString(
                CreateCollectionFragment.ARG_URL,
                parent.url.resolve(UUID.randomUUID().toString() + "/").toString()
            )
        } else {
            model.homeSetError.value = getString(R.string.create_collection_home_set_required)
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

        val tz = model.timeZone.value
        if (tz == null)
            ok = false
        else {
            DateUtils.ical4jTimeZone(tz.id)?.let {
                val cal = Calendar()
                cal.components += it.vTimeZone
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

    @OptIn(ExperimentalMaterialApi::class)
    @Composable
    fun CreateCalendarLayout(
        displayName: String,
        onDisplayNameChange: (String) -> Unit,
        displayNameError: String?,
        color: Int,
        onColorChange: (Int) -> Unit,
        description: String?,
        onDescriptionChange: (String) -> Unit,
        homeSet: HomeSet?,
        homeSets: List<HomeSet>,
        onHomeSetChange: (HomeSet) -> Unit,
        timeZone: TimeZone?,
        timeZones: List<TimeZone>,
        onTimeZoneChange: (TimeZone) -> Unit,
        typeError: String?,
        supportVEVENT: Boolean,
        onSupportVEVENTChange: (Boolean) -> Unit,
        supportVTODO: Boolean,
        onSupportVTODOChange: (Boolean) -> Unit,
        supportVJOURNAL: Boolean,
        onSupportVJOURNALChange: (Boolean) -> Unit,
        onCreateCollectionRequested: () -> Unit
    ) {
        fun onBackRequested() {
            val intent = Intent(this, AccountActivity::class.java)
            intent.putExtra(AccountActivity.EXTRA_ACCOUNT, model.account)
            NavUtils.navigateUpTo(this, intent)
        }

        BackHandler {
            onBackRequested()
        }

        var showingColorPicker by remember { mutableStateOf(false) }
        if (showingColorPicker) {
            ColorPickerDialog(
                initialColor = color,
                onSelectColor = {
                    onColorChange(it)
                    showingColorPicker = false
                },
                onDialogDismissed = { showingColorPicker = false }
            )
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.create_calendar)) },
                    navigationIcon = {
                        IconButton(
                            onClick = ::onBackRequested
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null
                            )
                        }
                    },
                    actions = {
                        TextButton(onClick = onCreateCollectionRequested) {
                            Text(stringResource(R.string.create_collection_create))
                        }
                    }
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = onDisplayNameChange,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                        label = { Text(stringResource(R.string.create_collection_display_name)) },
                        isError = displayNameError != null,
                        singleLine = true
                    )
                    Surface(
                        onClick = { showingColorPicker = true },
                        modifier = Modifier.size(32.dp),
                        color = Color(color)
                    ) {}
                }
                displayNameError?.let {
                    Text(
                        text = it,
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colors.error,
                        style = MaterialTheme.typography.caption
                    )
                }

                OutlinedTextField(
                    value = description ?: "",
                    onValueChange = onDescriptionChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    label = { Text(stringResource(R.string.create_collection_description)) },
                )
                Text(
                    text = stringResource(R.string.create_collection_optional),
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.caption
                )

                DropdownField(
                    value = homeSet,
                    options = homeSets,
                    label = stringResource(R.string.create_collection_home_set),
                    onOptionSelected = onHomeSetChange,
                    toString = { it.displayName ?: DavUtils.lastSegmentOfUrl(it.url) },
                    subtitle = { it.url.toString() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )

                DropdownField(
                    value = timeZone,
                    options = timeZones,
                    label = stringResource(R.string.create_calendar_time_zone),
                    onOptionSelected = onTimeZoneChange,
                    toString = { it.id },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )

                Text(
                    text = stringResource(R.string.create_calendar_type),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                )
                typeError?.let {
                    Text(
                        text = it,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        color = MaterialTheme.colors.error,
                        style = MaterialTheme.typography.caption
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    CheckboxWithText(
                        checked = supportVEVENT,
                        onCheckedChange = onSupportVEVENTChange,
                        text = stringResource(R.string.create_calendar_type_vevent)
                    )
                    CheckboxWithText(
                        checked = supportVTODO,
                        onCheckedChange = onSupportVTODOChange,
                        text = stringResource(R.string.create_calendar_type_vtodo)
                    )
                    CheckboxWithText(
                        checked = supportVJOURNAL,
                        onCheckedChange = onSupportVJOURNALChange,
                        text = stringResource(R.string.create_calendar_type_vjournal)
                    )
                }
            }
        }
    }

    @Composable
    @OptIn(ExperimentalMaterialApi::class)
    fun <Type: Any> DropdownField(
        value: Type?,
        options: List<Type>,
        label: String,
        modifier: Modifier = Modifier,
        toString: (Type) -> String = { it.toString() },
        subtitle: (Type) -> String? = { null },
        onOptionSelected: (Type) -> Unit
    ) {
        val density = LocalDensity.current

        var expanded by remember { mutableStateOf(false) }
        var itemHeight by remember { mutableStateOf<Dp?>(null) }
        val scrollState = rememberLazyListState()

        val displayItems = minOf(options.size, 4)

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            OutlinedTextField(
                value = value?.let(toString) ?: "",
                onValueChange = {},
                modifier = modifier,
                label = { Text(label) },
                readOnly = true,
                singleLine = true,
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded)
                }
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.size(
                    width = 300.dp,
                    height = itemHeight?.times(displayItems) ?: 300.dp
                )
            ) {
                LaunchedEffect(Unit) {
                    val index = options.indexOf(value).takeIf { it >= 0 } ?: return@LaunchedEffect
                    scrollState.scrollToItem(index)
                }

                Box(
                    modifier = Modifier.size(
                        width = 300.dp,
                        height = itemHeight?.times(displayItems) ?: 300.dp
                    )
                ) {
                    // since there are a lot of timezones, we have to use lazy lists
                    LazyColumn(
                        state = scrollState
                    ) {
                        items(options) { option ->
                            DropdownMenuItem(
                                onClick = { onOptionSelected(option) },
                                modifier = Modifier.onGloballyPositioned {
                                    if (itemHeight == null) {
                                        itemHeight = with(density) { it.size.height.toDp() }
                                    }
                                }
                            ) {
                                val textColor = if (option == value)
                                    MaterialTheme.colors.secondary
                                else
                                    LocalContentColor.current
                                Column {
                                    Text(
                                        text = option.let(toString),
                                        style = MaterialTheme.typography.body1,
                                        color = textColor
                                    )
                                    option.let(subtitle)?.let {
                                        Text(
                                            text = it,
                                            style = MaterialTheme.typography.body2,
                                            color = textColor.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun CheckboxWithText(
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
        text: String,
        modifier: Modifier = Modifier
    ) {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked, onCheckedChange)
            Text(
                text = text,
                style = MaterialTheme.typography.body2,
                modifier = Modifier.clickable { onCheckedChange(!checked) }
            )
        }
    }

    @Preview(
        showSystemUi = true,
        showBackground = true
    )
    @Composable
    fun CreateCalendarLayout_Preview() {
        AppTheme {
            CreateCalendarLayout(
                displayName = "My Calendar",
                onDisplayNameChange = {},
                displayNameError = null,
                color = Color.Blue.toArgb(),
                onColorChange = {},
                description = null,
                onDescriptionChange = {},
                homeSet = null,
                homeSets = emptyList(),
                onHomeSetChange = {},
                timeZone = null,
                timeZones = emptyList(),
                onTimeZoneChange = {},
                typeError = null,
                supportVEVENT = false,
                onSupportVEVENTChange = {},
                supportVTODO = false,
                onSupportVTODOChange = {},
                supportVJOURNAL = false,
                onSupportVJOURNALChange = {},
                onCreateCollectionRequested = {}
            )
        }
    }

    @Preview(showBackground = true)
    @Composable
    fun CheckboxWithText_Preview() {
        AppTheme {
            CheckboxWithText(
                checked = true,
                onCheckedChange = {},
                text = "Checkbox with Text"
            )
        }
    }


    class Model @AssistedInject constructor(
        val db: AppDatabase,
        @Assisted val account: Account
    ): ViewModel() {

        @AssistedFactory
        interface Factory {
            fun create(account: Account): Model
        }

        val displayName = MutableLiveData("")
        val displayNameError = MutableLiveData<String>()

        val description = MutableLiveData<String>()
        val color = MutableLiveData(Constants.DAVDROID_GREEN_RGBA)

        val homeSets = MutableLiveData<List<HomeSet>>()
        val homeSet = MutableLiveData<HomeSet>()
        val homeSetError = MutableLiveData<String>()

        val timeZone = MutableLiveData<TimeZone>()
        val timezoneError = MutableLiveData<String>()

        val typeError = MutableLiveData<String>()
        val supportVEVENT = MutableLiveData(false)
        val supportVTODO = MutableLiveData(false)
        val supportVJOURNAL = MutableLiveData(false)

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