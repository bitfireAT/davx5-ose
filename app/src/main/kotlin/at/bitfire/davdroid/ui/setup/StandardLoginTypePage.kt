package at.bitfire.davdroid.ui.setup

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.RadioButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    selectedLoginType: LoginType?,
    onSelectLoginType: (LoginType) -> Unit,
    loginInfo: LoginInfo,
    onUpdateLoginInfo: (LoginInfo) -> Unit,
    onContinue: () -> Unit = {},
    onLogin: () -> Unit = {}
) {
    var readyToLogin by remember { mutableStateOf(false) }

    Assistant(
        nextLabel = selectedLoginType?.let { type ->
            if (type.isGeneric)
                stringResource(R.string.login_login)
            else
                stringResource(R.string.login_continue)
        },
        nextEnabled =
            selectedLoginType?.isGeneric == false ||   // specific login; "Continue" button always enabled
            readyToLogin,                              // generic login; "Login" button enabled when ready
        onNext = {
            selectedLoginType?.let { type ->
                if (type.isGeneric)
                    onLogin()
                else
                    onContinue()
            }
        }
    ) {
        Text(
            "Generic login",
            style = MaterialTheme.typography.h6,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
        )

        for (type in genericLoginTypes)
            LoginTypeSelector(
                title = type.title,
                selected = type == selectedLoginType,
                onSelect = { onSelectLoginType(type) },
            ) {
                type.Content(
                    loginInfo = loginInfo,
                    onUpdateLoginInfo = { newLoginInfo, _readyToLogin ->
                        onUpdateLoginInfo(newLoginInfo)
                        readyToLogin = _readyToLogin ?: false
                    }
                )
            }

        Text(
            "Provider-specific login",
            style = MaterialTheme.typography.h6,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
        )

        for (type in specificLoginTypes)
            LoginTypeSelector(
                title = type.title,
                selected = type == selectedLoginType,
                onSelect = { onSelectLoginType(type) },
            )
    }
}

@Composable
fun LoginTypeSelector(
    title: String,
    selected: Boolean,
    onSelect: () -> Unit = {},
    content: @Composable () -> Unit = {}
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable(onClick = onSelect)
                .padding(end = 8.dp)
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

        AnimatedVisibility(visible = selected) {
            Column(Modifier.padding(vertical = 8.dp)) {
                content()
            }
        }
    }
}