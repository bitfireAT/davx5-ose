package at.bitfire.davdroid.ui.setup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.RadioButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import at.bitfire.davdroid.R
import at.bitfire.davdroid.ui.composable.Assistant

@Composable
fun StandardLoginTypePage(
    genericLoginTypes: Iterable<LoginType>,
    specificLoginTypes: Iterable<LoginType>,
    selectedLoginType: LoginType,
    onSelectLoginType: (LoginType) -> Unit,
    onContinue: () -> Unit = {}
) {
    Assistant(
        nextLabel = stringResource(R.string.login_continue),
        nextEnabled = true,
        onNext = onContinue
    ) {
        Column(Modifier.padding(8.dp)) {
            Text(
                stringResource(R.string.login_generic_login),
                style = MaterialTheme.typography.h6,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            for (type in genericLoginTypes)
                LoginTypeSelector(
                    title = stringResource(type.title),
                    selected = type == selectedLoginType,
                    onSelect = { onSelectLoginType(type) },
                )

            Text(
                stringResource(R.string.login_provider_login),
                style = MaterialTheme.typography.h6,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
            )
            for (type in specificLoginTypes)
                LoginTypeSelector(
                    title = stringResource(type.title),
                    selected = type == selectedLoginType,
                    onSelect = { onSelectLoginType(type) },
                )
        }
    }
}

@Composable
fun LoginTypeSelector(
    title: String,
    selected: Boolean,
    onSelect: () -> Unit = {}
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable(onClick = onSelect)
        ) {
            RadioButton(
                selected = selected,
                onClick = onSelect
            )
            Text(
                title,
                style = MaterialTheme.typography.body1,
                modifier = Modifier.weight(1f)
            )
        }
    }
}