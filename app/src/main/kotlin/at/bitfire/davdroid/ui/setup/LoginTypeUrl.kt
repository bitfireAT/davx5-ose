/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.setup

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Password
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import at.bitfire.davdroid.Constants
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.Credentials
import at.bitfire.davdroid.ui.UiUtils.toAnnotatedString
import at.bitfire.davdroid.ui.composable.Assistant
import at.bitfire.davdroid.ui.composable.PasswordTextField
import at.bitfire.davdroid.ui.widget.ClickableTextWithLink
import java.net.URI

object LoginTypeUrl : LoginType {

    override val title
        get() = R.string.login_type_url

    override val helpUrl: Uri?
        get() = null

    @Composable
    override fun Content(
        snackbarHostState: SnackbarHostState,
        loginInfo: LoginInfo,
        onUpdateLoginInfo: (newLoginInfo: LoginInfo) -> Unit,
        onDetectResources: () -> Unit,
        onFinish: () -> Unit
    ) {
        LoginTypeUrl_Content(
            loginInfo = loginInfo,
            onUpdateLoginInfo = onUpdateLoginInfo,
            onLogin = onDetectResources
        )
    }

}

@Composable
fun LoginTypeUrl_Content(
    loginInfo: LoginInfo,
    onUpdateLoginInfo: (newLoginInfo: LoginInfo) -> Unit = {},
    onLogin: () -> Unit = {}
) {
    var baseUrl by remember { mutableStateOf(
        loginInfo.baseUri?.takeIf {
            it.scheme.equals("http", ignoreCase = true) ||
            it.scheme.equals("https", ignoreCase = true)
        }?.toString() ?: ""
    ) }
    var username by remember { mutableStateOf(loginInfo.credentials?.username ?: "") }
    var password by remember { mutableStateOf(loginInfo.credentials?.password ?: "") }

    val newLoginInfo = LoginInfo(
        baseUri = try {
            URI(
                if (baseUrl.startsWith("http://", ignoreCase = true) || baseUrl.startsWith("https://", ignoreCase = true))
                    baseUrl
                else
                    "https://$baseUrl"
            )
        } catch (_: Exception) {
            null
        },
        credentials = Credentials(
            username = username,
            password = password
        )
    )
    onUpdateLoginInfo(newLoginInfo)

    val ok =
        newLoginInfo.baseUri != null && (
            newLoginInfo.baseUri.scheme.equals("http", ignoreCase = true) ||
            newLoginInfo.baseUri.scheme.equals("https",ignoreCase = true)
        ) && newLoginInfo.credentials != null &&
        newLoginInfo.credentials.username?.isNotEmpty() == true &&
        newLoginInfo.credentials.password?.isNotEmpty() == true

    val focusRequester = remember { FocusRequester() }
    Assistant(
        nextLabel = stringResource(R.string.login_login),
        nextEnabled = ok,
        onNext = onLogin
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                stringResource(R.string.login_type_url),
                style = MaterialTheme.typography.h5,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )

            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text(stringResource(R.string.login_base_url)) },
                placeholder = { Text("dav.example.com/path") },
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Default.Folder, null)
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
            )

            val manualUrl = Constants.MANUAL_URL.buildUpon()
                .appendPath(Constants.MANUAL_PATH_ACCOUNTS_COLLECTIONS)
                .fragment(Constants.MANUAL_FRAGMENT_SERVICE_DISCOVERY)
                .build()
            val urlInfo = HtmlCompat.fromHtml(stringResource(R.string.login_base_url_info, manualUrl), HtmlCompat.FROM_HTML_MODE_COMPACT)
            ClickableTextWithLink(
                urlInfo.toAnnotatedString(),
                style = MaterialTheme.typography.body1,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 16.dp)
            )

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text(stringResource(R.string.login_user_name)) },
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Default.AccountCircle, null)
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier.fillMaxWidth()
            )

            PasswordTextField(
                password = password,
                onPasswordChange = { password = it },
                labelText = stringResource(R.string.login_password),
                leadingIcon = {
                    Icon(Icons.Default.Password, null)
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = {
                    if (ok)
                        onLogin()
                }),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

@Composable
@Preview
fun LoginTypeUrl_Content_Preview() {
    LoginTypeUrl_Content(LoginInfo())
}