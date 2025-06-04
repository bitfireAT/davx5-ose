/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.composable

import android.net.Uri
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import at.bitfire.davdroid.Constants
import at.bitfire.davdroid.R
import at.bitfire.davdroid.ui.UiUtils.toAnnotatedString

@Composable
fun PasswordTextField(
    password: String,
    labelText: String?,
    onPasswordChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    isError: Boolean = false
) {
    var passwordVisible by remember { mutableStateOf(false) }

    Column {
        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = labelText?.let { { Text(it) } },
            leadingIcon = leadingIcon,
            isError = isError,
            singleLine = true,
            enabled = enabled,
            readOnly = readOnly,
            modifier = modifier.focusGroup(),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(
                    enabled = enabled,
                    onClick = { passwordVisible = !passwordVisible }
                ) {
                    if (passwordVisible)
                        Icon(Icons.Default.VisibilityOff, stringResource(R.string.login_password_hide))
                    else
                        Icon(Icons.Default.Visibility, stringResource(R.string.login_password_show))
                }
            }
        )
        Text(
            modifier = Modifier.padding(vertical = 8.dp),
            text = HtmlCompat.fromHtml(
                stringResource(
                    R.string.settings_app_password_hint,
                    appPasswordHelpUrl().toString()
                ),
                0
            ).toAnnotatedString()
        )
    }
}

fun appPasswordHelpUrl(): Uri = Constants.MANUAL_URL.buildUpon()
    .appendPath(Constants.MANUAL_PATH_INTRODUCTION)
    .fragment(Constants.MANUAL_FRAGMENT_AUTHENTICATION_METHODS)
    .build()


@Composable
@Preview
fun PasswordTextField_Sample() {
    PasswordTextField(
        password = "",
        labelText = "labelText",
        enabled = true,
        isError = false,
        onPasswordChange = {},
    )
}

@Composable
@Preview
fun PasswordTextField_Sample_Filled() {
    PasswordTextField(
        password = "password",
        labelText = "labelText",
        enabled = true,
        isError = false,
        onPasswordChange = {},
    )
}

@Composable
@Preview
fun PasswordTextField_Sample_Error() {
    PasswordTextField(
        password = "password",
        labelText = "labelText",
        enabled = true,
        isError = true,
        onPasswordChange = {},
    )
}

@Composable
@Preview
fun PasswordTextField_Sample_Disabled() {
    PasswordTextField(
        password = "password",
        labelText = "labelText",
        enabled = false,
        isError = false,
        onPasswordChange = {},
    )
}