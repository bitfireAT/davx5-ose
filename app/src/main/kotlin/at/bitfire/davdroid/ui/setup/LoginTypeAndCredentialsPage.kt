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
import androidx.compose.ui.unit.dp
import at.bitfire.davdroid.R
import at.bitfire.davdroid.ui.composable.Assistant

@Composable
fun StandardLoginTypeAndCredentialsPage(
    initialLoginInfo: LoginInfo? = null,
    onLogin: (LoginInfo) -> Unit = {}
) {
    var loginInfo by remember { mutableStateOf<LoginInfo?>(null) }
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

        Divider(Modifier.padding(vertical = 16.dp))
        Text(
            "Provider-specific login",
            style = MaterialTheme.typography.h6
        )

        LoginTypeSelector(
            title = stringResource(R.string.login_type_google),
            selected = false
        )

        LoginTypeSelector(
            title = stringResource(R.string.login_type_nextcloud),
            selected = false
        )
    }
}

@Composable
fun LoginTypeSelector(
    title: String,
    subtitle: String? = null,
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
            Column(Modifier
                .clickable(onClick = onSelect)
                .padding(vertical = 4.dp)
                .weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.body1
                )
                if (subtitle != null)
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.body2,
                    )
            }
        }

        AnimatedVisibility(visible = selected) {
            content()
        }
    }
}