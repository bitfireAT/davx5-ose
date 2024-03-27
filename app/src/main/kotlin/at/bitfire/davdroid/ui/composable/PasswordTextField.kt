package at.bitfire.davdroid.ui.composable

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import at.bitfire.davdroid.R

@Composable
fun PasswordTextField(
    password: String,
    labelText: String,
    onPasswordChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    enabled: Boolean = true,
    isError: Boolean = false
) {
    var passwordVisible by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = password,
        onValueChange = onPasswordChange,
        label = { Text(labelText) },
        leadingIcon = leadingIcon,
        isError = isError,
        singleLine = true,
        enabled = enabled,
        modifier = modifier.focusGroup(),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { passwordVisible = !passwordVisible }) {
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