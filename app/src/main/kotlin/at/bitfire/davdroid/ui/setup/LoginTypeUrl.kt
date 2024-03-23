package at.bitfire.davdroid.ui.setup

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.Credentials
import at.bitfire.davdroid.ui.composable.PasswordTextField
import java.net.URI

class LoginTypeUrl(
    val context: Context
) : LoginType {

    override val title
        get() = context.getString(R.string.login_type_url)

    override val isGeneric: Boolean
        get() = true

    override val helpUrl: Uri?
        get() = null

    @Composable
    override fun Content(
        loginInfo: LoginInfo,
        onUpdateLoginInfo: (newLoginInfo: LoginInfo, readyToLogin: Boolean?) -> Unit
    ) {
        var baseUrl by remember { mutableStateOf(loginInfo.baseUri?.toString() ?: "") }
        OutlinedTextField(
            value = baseUrl,
            onValueChange = { baseUrl = it },
            label = { Text(stringResource(R.string.login_base_url)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Next
            ),
            modifier = Modifier.fillMaxWidth()
        )

        var username by remember { mutableStateOf(loginInfo.credentials?.username ?: "") }
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text(stringResource(R.string.login_user_name)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email
            ),
            modifier = Modifier.fillMaxWidth()
        )

        var password by remember { mutableStateOf(loginInfo.credentials?.password ?: "") }
        PasswordTextField(
            password = password,
            onPasswordChange = { password = it },
            labelText = stringResource(R.string.login_password),
            modifier = Modifier.fillMaxWidth()
        )

        // validate
        val newLoginInfo = LoginInfo(
            baseUri = try {
                URI(
                    if (baseUrl.matches(Regex("^https?://"))) baseUrl else "https://$baseUrl"
                )
            } catch (_: Exception) {
                null
            },
            credentials = Credentials(
                username = username,
                password = password
            )
        )
        val ok =
            newLoginInfo.baseUri != null &&
            (newLoginInfo.baseUri.scheme.equals("http", ignoreCase = true) || newLoginInfo.baseUri.scheme.equals("https", ignoreCase = true)) &&
            newLoginInfo.credentials != null &&
            newLoginInfo.credentials.username?.isBlank() == false &&
            newLoginInfo.credentials.password?.isBlank() == false
        onUpdateLoginInfo(newLoginInfo, ok)
    }

}