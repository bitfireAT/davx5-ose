package at.bitfire.davdroid.ui.widget

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import at.bitfire.davdroid.R

@Composable
fun PasswordTextField(
    password: String,
    labelText: String,
    enabled: Boolean,
    isError: Boolean,
    onPasswordChange: (String) -> Unit
) {
    var passwordVisible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = password,
        onValueChange = onPasswordChange,
        label = { Text(labelText) },
        isError = isError,
        singleLine = true,
        enabled = enabled,
        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                if (passwordVisible)
                    Icon(Icons.Default.VisibilityOff, stringResource(R.string.login_password_hide))
                else
                    Icon(Icons.Default.Visibility, stringResource(R.string.login_password_show))
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
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