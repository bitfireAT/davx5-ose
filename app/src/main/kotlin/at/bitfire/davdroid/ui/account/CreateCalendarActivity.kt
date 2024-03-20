/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui.account

import android.accounts.Account
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Checkbox
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.TaskStackBuilder
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import at.bitfire.davdroid.Constants
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.HomeSet
import at.bitfire.davdroid.ui.AppTheme
import at.bitfire.davdroid.ui.widget.CalendarColorPickerDialog
import at.bitfire.davdroid.ui.widget.ExceptionInfoDialog
import at.bitfire.davdroid.ui.widget.MultipleChoiceInputDialog
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.apache.commons.lang3.StringUtils
import java.text.Collator
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class CreateCalendarActivity: AppCompatActivity() {

    companion object {
        const val EXTRA_ACCOUNT = "account"
    }

    val account by lazy { intent.getParcelableExtra<Account>(EXTRA_ACCOUNT) ?: throw IllegalArgumentException("EXTRA_ACCOUNT must be set") }

    @Inject
    lateinit var modelFactory: AccountModel.Factory
    val accountModel by viewModels<AccountModel> {
        object: ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                modelFactory.create(account) as T
        }
    }

    val model: Model by viewModels()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AppTheme {
                var displayName by remember { mutableStateOf("") }
                var color by remember { mutableIntStateOf(Constants.DAVDROID_GREEN_RGBA) }
                var description by remember { mutableStateOf("") }
                var homeSet by remember { mutableStateOf<HomeSet?>(null) }
                var timeZoneId by remember { mutableStateOf<String>(ZoneId.systemDefault().id) }
                var supportVEVENT by remember { mutableStateOf(true) }
                var supportVTODO by remember { mutableStateOf(false) }
                var supportVJOURNAL by remember { mutableStateOf(false) }

                var isCreating by remember { mutableStateOf(false) }
                accountModel.createCollectionResult.observeAsState().value?.let { result ->
                    if (result.isEmpty)
                        finish()
                    else
                        ExceptionInfoDialog(
                            exception = result.get(),
                            onDismiss = {
                                isCreating = false
                                accountModel.createCollectionResult.value = null
                            }
                        )
                }

                val onCreateCollection = {
                    if (!isCreating) {
                        isCreating = true
                        homeSet?.let { homeSet ->
                            accountModel.createCollection(
                                homeSet = homeSet,
                                addressBook = false,
                                name = UUID.randomUUID().toString(),
                                displayName = StringUtils.trimToNull(displayName),
                                description = StringUtils.trimToNull(description),
                                timeZoneId = timeZoneId,
                                supportsVEVENT = supportVEVENT,
                                supportsVTODO = supportVTODO,
                                supportsVJOURNAL = supportVJOURNAL
                            )
                        }
                    }
                }

                val homeSets by accountModel.bindableCalendarHomesets.observeAsState()

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(stringResource(R.string.create_calendar)) },
                            navigationIcon = {
                                IconButton(onClick = { onSupportNavigateUp() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_up))
                                }
                            },
                            actions = {
                                val isCreateEnabled = !isCreating && displayName.isNotBlank() && homeSet != null
                                IconButton(
                                    enabled = isCreateEnabled,
                                    onClick = { onCreateCollection() }
                                ) {
                                    Text(stringResource(R.string.create_collection_create).uppercase())
                                }
                            }
                        )
                    }
                ) { padding ->
                    Column(Modifier
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                    ) {
                        if (isCreating)
                            LinearProgressIndicator(
                                color = MaterialTheme.colors.secondary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp)
                            )

                        homeSets?.let { homeSets ->
                            CalendarForm(
                                displayName = displayName,
                                onDisplayNameChange = { displayName = it },
                                color = color,
                                onColorChange = { color = it },
                                description = description,
                                onDescriptionChange = { description = it },
                                timeZoneId = timeZoneId,
                                onTimeZoneSelected = { timeZoneId = it },
                                supportVEVENT = supportVEVENT,
                                onSupportVEVENTChange = { supportVEVENT = it },
                                supportVTODO = supportVTODO,
                                onSupportVTODOChange = { supportVTODO = it },
                                supportVJOURNAL = supportVJOURNAL,
                                onSupportVJOURNALChange = { supportVJOURNAL = it },
                                homeSet = homeSet,
                                homeSets = homeSets,
                                onHomeSetSelected = { homeSet = it }
                            )
                        }
                    }
                }

                /*
                val typeError by model.typeError.observeAsState(false)
                val supportVEVENT by model.supportVEVENT.observeAsState(initial = true)
                val supportVTODO by model.supportVTODO.observeAsState(initial = true)
                val supportVJOURNAL by model.supportVJOURNAL.observeAsState(initial = true)
                */
            }
        }
    }

    override fun supportShouldUpRecreateTask(targetIntent: Intent) = true

    override fun onPrepareSupportNavigateUpTaskStack(builder: TaskStackBuilder) {
        builder.editIntentAt(builder.intentCount - 1)?.putExtra(AccountActivity.EXTRA_ACCOUNT, accountModel.account)
    }


    @Composable
    fun CalendarForm(
        displayName: String,
        onDisplayNameChange: (String) -> Unit = {},
        color: Int,
        onColorChange: (Int) -> Unit = {},
        description: String,
        onDescriptionChange: (String) -> Unit = {},
        timeZoneId: String,
        onTimeZoneSelected: (String) -> Unit = {},
        supportVEVENT: Boolean,
        onSupportVEVENTChange: (Boolean) -> Unit = {},
        supportVTODO: Boolean,
        onSupportVTODOChange: (Boolean) -> Unit = {},
        supportVJOURNAL: Boolean,
        onSupportVJOURNALChange: (Boolean) -> Unit = {},
        homeSet: HomeSet?,
        homeSets: List<HomeSet>,
        onHomeSetSelected: (HomeSet) -> Unit = {}
    ) {
        Column(Modifier
            .fillMaxWidth()
            .padding(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val focusRequester = remember { FocusRequester() }
                OutlinedTextField(
                    value = displayName,
                    onValueChange = onDisplayNameChange,
                    label = { Text(stringResource(R.string.create_collection_display_name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                        .focusRequester(focusRequester)
                )
                LaunchedEffect(Unit) {
                    focusRequester.requestFocus()
                }

                var showColorPicker by remember { mutableStateOf(false) }
                Box(Modifier
                    .background(color = Color(color), shape = CircleShape)
                    .clickable {
                        showColorPicker = true
                    }
                    .size(32.dp)
                )
                if (showColorPicker) {
                    CalendarColorPickerDialog(
                        onSelectColor = {
                            onColorChange(it)
                            showColorPicker = false
                        },
                        onDismiss = { showColorPicker = false }
                    )
                }
            }

            OutlinedTextField(
                value = description,
                onValueChange = onDescriptionChange,
                label = { Text(stringResource(R.string.create_collection_description_optional)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.create_calendar_time_zone),
                        style = MaterialTheme.typography.body1
                    )
                    Text(
                        ZoneId.of(timeZoneId).getDisplayName(TextStyle.FULL_STANDALONE, Locale.getDefault()),
                        style = MaterialTheme.typography.body2
                    )
                }

                var showTimeZoneDialog by remember { mutableStateOf(false) }
                TextButton(
                    enabled =
                        if (LocalInspectionMode.current)
                            true
                        else
                            model.timeZoneDefs.observeAsState().value != null,
                    onClick = { showTimeZoneDialog = true }
                ) {
                    Text("Select timezone".uppercase())
                }
                if (showTimeZoneDialog) {
                    model.timeZoneDefs.observeAsState().value?.let { timeZoneDefs ->
                        MultipleChoiceInputDialog(
                            title = "Select timezone",
                            namesAndValues = timeZoneDefs,
                            initialValue = timeZoneId,
                            onValueSelected = {
                                onTimeZoneSelected(it)
                                showTimeZoneDialog = false
                            },
                            onDismiss = { showTimeZoneDialog = false }
                        )
                    }
                }
            }

            Text(
                stringResource(R.string.create_calendar_type),
                style = MaterialTheme.typography.body1,
                modifier = Modifier.padding(top = 16.dp)
            )
            CheckboxRow(
                labelId = R.string.create_calendar_type_vevent,
                checked = supportVEVENT,
                onCheckedChange = onSupportVEVENTChange
            )
            CheckboxRow(
                labelId = R.string.create_calendar_type_vtodo,
                checked = supportVTODO,
                onCheckedChange = onSupportVTODOChange
            )
            CheckboxRow(
                labelId = R.string.create_calendar_type_vjournal,
                checked = supportVJOURNAL,
                onCheckedChange = onSupportVJOURNALChange
            )

            HomeSetSelection(
                homeSet = homeSet,
                homeSets = homeSets,
                onHomeSetSelected = onHomeSetSelected
            )
        }
    }

    @Composable
    fun CheckboxRow(
        @StringRes labelId: Int,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
            Text(
                text = stringResource(labelId),
                style = MaterialTheme.typography.body1,
                modifier = Modifier
                    .clickable { onCheckedChange(!checked) }
                    .weight(1f)
            )
        }
    }

    @Composable
    @Preview
    fun CalendarForm_Preview() {
        CalendarForm(
            displayName = "My Calendar",
            color = Color.Magenta.toArgb(),
            description = "This is my calendar",
            timeZoneId = "Europe/Vienna",
            supportVEVENT = true,
            supportVTODO = false,
            supportVJOURNAL = false,
            homeSet = null,
            homeSets = emptyList()
        )
    }


    /*private fun onCreateCollection(
        displayName: String
    ) {
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
            model.typeError.value = true
        } else
            model.typeError.value = false

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

                Text(
                    text = stringResource(R.string.create_calendar_type),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    color = if (typeError) MaterialTheme.colors.error else Color.Unspecified
                )
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

    @Preview
    @Composable
    fun CreateCalendarLayout_Preview() {
        AppTheme {
            CreateCalendarLayout(
                typeError = false,
                supportVEVENT = false,
                onSupportVEVENTChange = {},
                supportVTODO = false,
                onSupportVTODOChange = {},
                supportVJOURNAL = false,
                onSupportVJOURNALChange = {},
                onCreateCollectionRequested = {}
            )
        }
    }*/


    @HiltViewModel
    class Model @Inject constructor() : ViewModel() {

        /**
         * List of available time zones as <display name, ID> pairs.
         */
        val timeZoneDefs = MutableLiveData<List<Pair<String, String>>>()

        init {
            viewModelScope.launch(Dispatchers.IO) {
                val timeZones = mutableListOf<Pair<String, String>>()

                // iterate over Android time zones and take those with ical4j VTIMEZONE into consideration
                val locale = Locale.getDefault()
                for (id in ZoneId.getAvailableZoneIds()) {
                    timeZones += Pair(
                        ZoneId.of(id).getDisplayName(TextStyle.FULL, locale),
                        id
                    )
                }

                val collator = Collator.getInstance()
                timeZoneDefs.postValue(timeZones.sortedBy { collator.getCollationKey(it.first) })
            }
        }

    }

}