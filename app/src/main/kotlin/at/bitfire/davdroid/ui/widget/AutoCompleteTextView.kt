package at.bitfire.davdroid.ui.widget

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Card
import androidx.compose.material.Divider
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.ListItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.primarySurface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp

/**
 * Provides a read-only text view which shows some options when tapped.
 * @param value The currently selected value.
 * @param label The label to display on top of the text field.
 * @param items The options to display that can be chosen.
 * @param onItemClick Will be called whenever to user selects one of [items].
 * @param isError If true, the text field will be displayed red.
 * @param toStringConverter Converts [T] to a [String], to display in the UI. Defaults to [Any.toString].
 * @param lazyListState The [LazyListState] of the list that displays all the items.
 */
@Composable
@OptIn(ExperimentalMaterialApi::class)
fun <T> AutoCompleteTextView(
    value: T?,
    label: String,
    modifier: Modifier = Modifier,
    items: List<T> = emptyList(),
    onItemClick: (T) -> Unit = {},
    isError: Boolean = false,
    toStringConverter: (T) -> String = { it.toString() },
    lazyListState: LazyListState = rememberLazyListState()
) {
    val focusManager = LocalFocusManager.current

    var isTextFieldFocused by remember { mutableStateOf(false) }

    BackHandler(enabled = isTextFieldFocused) { focusManager.clearFocus() }

    Column(
        modifier = modifier
    ) {
        OutlinedTextField(
            modifier = modifier
                .fillMaxWidth()
                .onFocusChanged { isTextFieldFocused = it.isFocused },
            value = value?.let(toStringConverter) ?: "",
            onValueChange = { },
            label = { Text(label) },
            singleLine = true,
            readOnly = true,
            isError = isError,
            trailingIcon = {
                Icon(
                    imageVector = if (isTextFieldFocused)
                        Icons.Filled.ArrowDropUp
                    else
                        Icons.Filled.ArrowDropDown,
                    contentDescription = null
                )
            }
        )
        AnimatedVisibility(
            visible = isTextFieldFocused,
            enter = scaleIn(transformOrigin = TransformOrigin(.5f, 0f)) + fadeIn(),
            exit = scaleOut(transformOrigin = TransformOrigin(.5f, 0f)) + fadeOut()
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = TextFieldDefaults.MinHeight * 6),
                elevation = 5.dp,
                backgroundColor = MaterialTheme.colors.surface
            ) {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(items) { item ->
                        ListItem(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    // Highlight selected value
                                    if (item == value) {
                                        MaterialTheme.colors.primarySurface
                                    } else {
                                        MaterialTheme.colors.surface
                                    }
                                )
                                .clickable { onItemClick(item); focusManager.clearFocus() }
                        ) {
                            Text(
                                text = item.let(toStringConverter),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                            )
                        }
                        // Do not show divider on the last item
                        if (item != items.lastOrNull()) Divider()
                    }
                }
            }
        }
    }
}
