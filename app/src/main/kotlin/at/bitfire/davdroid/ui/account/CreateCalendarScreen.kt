/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.account

import android.accounts.Account
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.HomeSet
import at.bitfire.davdroid.ui.AppTheme
import at.bitfire.davdroid.ui.composable.ExceptionInfoDialog
import at.bitfire.davdroid.ui.widget.CalendarColorPickerDialog
import at.bitfire.ical4android.Css3Color
import okhttp3.HttpUrl.Companion.toHttpUrl

@Composable
fun CreateCalendarScreen(
    account: Account,
    onFinish: () -> Unit,
    onNavUp: () -> Unit
) {
    val model: CreateCalendarModel = hiltViewModel(
        creationCallback = { factory: CreateCalendarModel.Factory ->
            factory.create(account)
        }
    )
    val uiState = model.uiState

    if (uiState.success)
        onFinish()

    CreateCalendarScreen(
        isCreating = uiState.isCreating,
        error = uiState.error,
        onResetError = model::resetError,
        color = uiState.color,
        onSetColor = model::setColor,
        displayName = uiState.displayName,
        onSetDisplayName = model::setDisplayName,
        description = uiState.description,
        onSetDescription = model::setDescription,
        timeZones = model.timeZones.collectAsStateWithLifecycle(emptyList()).value,
        timeZone = uiState.timeZoneId,
        onSelectTimeZone = model::setTimeZoneId,
        supportVEVENT = uiState.supportVEVENT,
        onSetSupportVEVENT = model::setSupportVEVENT,
        supportVTODO = uiState.supportVTODO,
        onSetSupportVTODO = model::setSupportVTODO,
        supportVJOURNAL = uiState.supportVJOURNAL,
        onSetSupportVJOURNAL = model::setSupportVJOURNAL,
        homeSets = model.calendarHomeSets.collectAsStateWithLifecycle(emptyList()).value,
        selectedHomeSet = uiState.homeSet,
        onSelectHomeSet = model::setHomeSet,
        canCreate = uiState.canCreate,
        onCreate = model::createCalendar,
        onNavUp = onNavUp
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateCalendarScreen(
    error: Exception? = null,
    onResetError: () -> Unit = {},
    color: Int = Css3Color.green.argb,
    onSetColor: (Int) -> Unit = {},
    displayName: String = "",
    onSetDisplayName: (String) -> Unit = {},
    description: String = "",
    onSetDescription: (String) -> Unit = {},
    timeZones: List<CreateCalendarModel.TimeZoneInfo>,
    timeZone: String? = null,
    onSelectTimeZone: (String?) -> Unit = {},
    supportVEVENT: Boolean = true,
    onSetSupportVEVENT: (Boolean) -> Unit = {},
    supportVTODO: Boolean = true,
    onSetSupportVTODO: (Boolean) -> Unit = {},
    supportVJOURNAL: Boolean = true,
    onSetSupportVJOURNAL: (Boolean) -> Unit = {},
    homeSets: List<HomeSet>,
    selectedHomeSet: HomeSet? = null,
    onSelectHomeSet: (HomeSet) -> Unit = {},
    canCreate: Boolean = false,
    isCreating: Boolean = false,
    onCreate: () -> Unit = {},
    onNavUp: () -> Unit = {}
) {
    val context = LocalContext.current

    AppTheme {
        if (error != null)
            ExceptionInfoDialog(
                exception = error,
                onDismiss = onResetError
            )

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.create_calendar)) },
                    navigationIcon = {
                        IconButton(onClick = onNavUp) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_up))
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                Modifier
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
            ) {
                if (isCreating)
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    )

                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.height(IntrinsicSize.Min)
                    ) {
                        val focusRequester = remember { FocusRequester() }
                        OutlinedTextField(
                            value = displayName,
                            onValueChange = onSetDisplayName,
                            label = { Text(stringResource(R.string.create_collection_display_name)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                imeAction = ImeAction.Next
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(focusRequester)
                                .padding(end = 8.dp)
                        )
                        LaunchedEffect(Unit) {
                            focusRequester.requestFocus()
                        }

                        var showColorPicker by remember { mutableStateOf(false) }
                        Box(Modifier
                            .padding(top = 8.dp)
                            .background(color = Color(color), shape = RoundedCornerShape(4.dp))
                            .clickable {
                                showColorPicker = true
                            }
                            .fillMaxHeight()
                            .aspectRatio(1f)
                            .semantics {
                                contentDescription =
                                    context.getString(R.string.create_collection_color)
                            }
                        )
                        if (showColorPicker) {
                            CalendarColorPickerDialog(
                                onSelectColor = { color ->
                                    onSetColor(color)
                                    showColorPicker = false
                                },
                                onDismiss = { showColorPicker = false }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = description,
                        onValueChange = onSetDescription,
                        label = { Text(stringResource(R.string.create_collection_description_optional)) },
                        supportingText = { Text(stringResource(R.string.create_collection_optional)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { onCreate() }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )

                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = it },
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        OutlinedTextField(
                            label = { Text(stringResource(R.string.create_calendar_time_zone_optional)) },
                            value = timeZone ?: stringResource(R.string.create_calendar_time_zone_none),
                            onValueChange = { /* read-only */ },
                            supportingText = { Text(stringResource(R.string.create_collection_optional)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            readOnly = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )

                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            modifier = Modifier.fillMaxHeight()
                        ) {
                            Text(
                                text = stringResource(R.string.create_calendar_time_zone_none),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier
                                    .clickable {
                                        onSelectTimeZone(null)
                                        expanded = false
                                    }
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )

                            for (tz in timeZones)
                                Text(
                                    text = tz.displayName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier
                                        .clickable {
                                            onSelectTimeZone(tz.id)
                                            expanded = false
                                        }
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                        }
                    }

                    Text(
                        stringResource(R.string.create_calendar_type),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                    CheckBoxRow(
                        label = stringResource(R.string.create_calendar_type_vevent),
                        value = supportVEVENT,
                        onValueChange = onSetSupportVEVENT
                    )
                    CheckBoxRow(
                        label = stringResource(R.string.create_calendar_type_vtodo),
                        value = supportVTODO,
                        onValueChange = onSetSupportVTODO
                    )
                    CheckBoxRow(
                        label = stringResource(R.string.create_calendar_type_vjournal),
                        value = supportVJOURNAL,
                        onValueChange = onSetSupportVJOURNAL
                    )

                    HomeSetSelection(
                        homeSet = selectedHomeSet,
                        homeSets = homeSets,
                        onSelectHomeSet = onSelectHomeSet,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    Text(
                        stringResource(R.string.create_calendar_maybe_not_supported),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    Button(
                        onClick = onCreate,
                        enabled = canCreate
                    ) {
                        Text(stringResource(R.string.create_calendar))
                    }
                }
            }
        }
    }
}

@Composable
fun CheckBoxRow(
    label: String,
    value: Boolean,
    onValueChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable { onValueChange(!value) }
    ) {
        Checkbox(
            checked = value,
            onCheckedChange = onValueChange
        )
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
@Preview
fun CreateCalendarScreenPreview() {
    CreateCalendarScreen(
        timeZones = listOf(
            CreateCalendarModel.TimeZoneInfo(
                id = "Europe/Vienna",
                displayName = "Vienna (Europe)"
            )
        ),
        timeZone = "Europe/Vienna",

        homeSets = listOf(
            HomeSet(
                id = 0,
                serviceId = 0,
                personal = true,
                url = "https://example.com/some/homeset".toHttpUrl()
            )
        )
    )
}