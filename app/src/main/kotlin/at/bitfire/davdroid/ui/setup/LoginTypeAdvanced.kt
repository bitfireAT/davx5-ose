package at.bitfire.davdroid.ui.setup

import android.app.Activity
import android.net.Uri
import android.security.KeyChain
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.SnackbarResult
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Password
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
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
import kotlinx.coroutines.launch
import org.apache.commons.lang3.StringUtils
import java.net.URI

object LoginTypeAdvanced : LoginType {

    override val title: Int
        get() = R.string.login_type_advanced

    override val helpUrl: Uri?
        get() = null


    @Composable
    override fun Content(
        snackbarHostState: SnackbarHostState,
        loginInfo: LoginInfo,
        onUpdateLoginInfo: (newLoginInfo: LoginInfo) -> Unit,
        onDetectResources: () -> Unit
    ) {
        LoginTypeAdvanced_Content(
            snackbarHostState = snackbarHostState,
            loginInfo = loginInfo,
            onUpdateLoginInfo = onUpdateLoginInfo,
            onLogin = onDetectResources
        )
    }

}

@Composable
fun LoginTypeAdvanced_Content(
    snackbarHostState: SnackbarHostState,
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
    var certificateAlias by remember { mutableStateOf(loginInfo.credentials?.certificateAlias) }

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
            username = StringUtils.trimToNull(username),
            password = StringUtils.trimToNull(password)
        )
    )
    onUpdateLoginInfo(newLoginInfo)
    val ok =
        newLoginInfo.baseUri != null && (
            newLoginInfo.baseUri.scheme.equals("http", ignoreCase = true) ||
            newLoginInfo.baseUri.scheme.equals("https",ignoreCase = true)
        )

    val focusRequester = remember { FocusRequester() }
    Assistant(
        nextLabel = stringResource(R.string.login_login),
        nextEnabled = ok,
        onNext = onLogin
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                stringResource(R.string.login_type_advanced),
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
                label = { Text(stringResource(R.string.login_user_name_optional)) },
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
                labelText = stringResource(R.string.login_password_optional),
                leadingIcon = {
                    Icon(Icons.Default.Password, null)
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(8.dp)) {
                    Text(
                        certificateAlias?.let { alias ->
                            stringResource(R.string.login_client_certificate_selected, alias)
                        } ?: stringResource(R.string.login_no_client_certificate_optional),
                        style = MaterialTheme.typography.body1,
                        modifier = Modifier.padding(8.dp)
                    )

                    val activity = LocalContext.current as Activity
                    val scope = rememberCoroutineScope()
                    TextButton(
                        onClick = {
                            KeyChain.choosePrivateKeyAlias(activity, { alias ->
                                 if (alias != null)
                                     certificateAlias = alias
                                else {
                                    // Show a Snackbar to add a certificate if no certificate was found
                                    // API Versions < 29 does that itself
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q)
                                        scope.launch {
                                            if (snackbarHostState.showSnackbar(
                                                message = activity.getString(R.string.login_no_certificate_found),
                                                actionLabel = activity.getString(R.string.login_install_certificate).uppercase()
                                            ) == SnackbarResult.ActionPerformed)
                                                activity.startActivity(KeyChain.createInstallIntent())
                                        }
                                }
                            }, null, null, null, -1, certificateAlias)
                        }
                    ) {
                        Text(stringResource(R.string.login_select_certificate).uppercase())
                    }
                }
            }

            Text(
                stringResource(R.string.optional_label),
                style = MaterialTheme.typography.body2,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

@Composable
@Preview
fun LoginTypeAdvancedPreview_Empty() {
    LoginTypeAdvanced_Content(
        snackbarHostState = SnackbarHostState(),
        loginInfo = LoginInfo()
    )
}

@Composable
@Preview
fun LoginTypeAdvancedPreview_AllFilled() {
    LoginTypeAdvanced_Content(
        snackbarHostState = SnackbarHostState(),
        loginInfo = LoginInfo(
            baseUri = URI("https://some-dav.example.com"),
            credentials = Credentials(
                username = "some-user",
                password = "password",
                certificateAlias = "some-alias"
            )
        )
    )
}