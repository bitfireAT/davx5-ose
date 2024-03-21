package at.bitfire.davdroid.ui.setup

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Divider
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import at.bitfire.davdroid.R
import at.bitfire.davdroid.ui.composable.Assistant

@Composable
fun LoginTypeAndCredentialsPage(
    genericLoginTypes: List<LoginType> = emptyList(),
    providerLoginTypes: List<LoginType> = emptyList(),
    initialLoginInfo: LoginInfo? = null,
    onLogin: (LoginInfo) -> Unit = {}
) {
    var loginInfo by remember { mutableStateOf<LoginInfo?>(null) }
    var selected by remember { mutableStateOf<LoginType?>(null) }
    var canContinue by remember { mutableStateOf(false) }

    Assistant(
        nextLabel = stringResource(R.string.login_login),
        nextEnabled = canContinue && loginInfo != null,
        onNext = { loginInfo?.let(onLogin) }
    ) {
        Text(
            "Generic login",
            style = MaterialTheme.typography.h6
        )
        for (type in genericLoginTypes)
            LoginTypeSelector(
                title = type.getName(),
                selected = selected == type,
                onSelect = {
                    selected = type
                },
                content = {
                    type.LoginForm(
                        initialLoginInfo = initialLoginInfo,
                        updateCanContinue = { canContinue = it },
                        updateLoginInfo = { loginInfo = it },
                        onLogin = {
                            loginInfo?.let(onLogin)
                        }
                    )
                }
            )

        Divider(Modifier.padding(vertical = 16.dp))
        Text(
            "Provider-specific login",
            style = MaterialTheme.typography.h6
        )
    }
}

@Composable
@Preview
fun LoginTypeAndCredentials_Preview() {
    LoginTypeAndCredentialsPage(
        genericLoginTypes = listOf(
            object : LoginType {
                override fun isGeneric() = true
                override fun getOrder() = 100
                override fun getName() = "Some Login"

                @Composable
                override fun LoginForm(
                    initialLoginInfo: LoginInfo?,
                    updateCanContinue: (Boolean) -> Unit,
                    updateLoginInfo: (LoginInfo) -> Unit,
                    onLogin: () -> Unit
                ) {
                    Text("Some login details")
                }
            }
        )
    )
}

@Composable
fun LoginTypeSelector(
    title: String,
    selected: Boolean,
    onSelect: () -> Unit = {},
    content: @Composable () -> Unit = {}
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = selected,
                onClick = onSelect
            )
            Text(
                title,
                style = MaterialTheme.typography.body1,
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .clickable(onClick = onSelect)
                    .weight(1f)
            )
        }

        AnimatedVisibility(visible = selected) {
            content()
        }
    }
}