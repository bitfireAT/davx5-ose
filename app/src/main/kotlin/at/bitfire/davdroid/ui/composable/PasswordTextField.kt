/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.composable

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import at.bitfire.davdroid.R

@Composable
fun PasswordTextField(
    password: TextFieldState,
    labelText: String?,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
    onKeyboardAction: KeyboardActionHandler? = null,
    enabled: Boolean = true,
    isError: Boolean = false
) {
    var passwordVisible by remember { mutableStateOf(false) }

    OutlinedSecureTextField(
        state = password,
        label = labelText?.let { { Text(it) } },
        leadingIcon = leadingIcon,
        isError = isError,
        enabled = enabled,
        modifier = modifier.focusGroup(),
        keyboardOptions = keyboardOptions,
        onKeyboardAction = onKeyboardAction,
        textObfuscationMode = if (passwordVisible) TextObfuscationMode.Visible else TextObfuscationMode.RevealLastTyped,
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
}

@Composable
@Preview
fun PasswordTextField_Sample() {
    PasswordTextField(
        password = rememberTextFieldState(""),
        labelText = "labelText",
        enabled = true,
        isError = false,
    )
}

@Composable
@Preview
fun PasswordTextField_Sample_Filled() {
    PasswordTextField(
        password = rememberTextFieldState("password"),
        labelText = "labelText",
        enabled = true,
        isError = false,
    )
}

@Composable
@Preview
fun PasswordTextField_Sample_Error() {
    PasswordTextField(
        password = rememberTextFieldState("password"),
        labelText = "labelText",
        enabled = true,
        isError = true,
    )
}

@Composable
@Preview
fun PasswordTextField_Sample_Disabled() {
    PasswordTextField(
        password = rememberTextFieldState("password"),
        labelText = "labelText",
        enabled = false,
        isError = false,
    )
}