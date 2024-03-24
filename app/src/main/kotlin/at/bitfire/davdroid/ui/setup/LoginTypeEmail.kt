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
import androidx.compose.material.icons.filled.Email
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
import androidx.core.net.MailTo
import androidx.core.text.HtmlCompat
import at.bitfire.davdroid.Constants
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.Credentials
import at.bitfire.davdroid.ui.UiUtils.toAnnotatedString
import at.bitfire.davdroid.ui.composable.Assistant
import at.bitfire.davdroid.ui.composable.PasswordTextField
import at.bitfire.davdroid.ui.widget.ClickableTextWithLink
import java.net.URI

object LoginTypeEmail : LoginType {

    override val title: Int
        get() = R.string.login_type_email

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
        LoginTypeEmail_Content(
            loginInfo = loginInfo,
            onUpdateLoginInfo = onUpdateLoginInfo,
            onLogin = onDetectResources
        )
    }

}


@Composable
fun LoginTypeEmail_Content(
    loginInfo: LoginInfo,
    onUpdateLoginInfo: (newLoginInfo: LoginInfo) -> Unit = {},
    onLogin: () -> Unit = {}
) {
    var email by remember { mutableStateOf(loginInfo.credentials?.username ?: "") }
    var password by remember { mutableStateOf(loginInfo.credentials?.password ?: "") }

    onUpdateLoginInfo(LoginInfo(
        baseUri = URI(MailTo.MAILTO_SCHEME, email, null),
        credentials = Credentials(username = email, password = password)
    ))
    val ok = email.contains('@') && password.isNotEmpty()

    Assistant(
        nextLabel = stringResource(R.string.login_login),
        nextEnabled = ok,
        onNext = {
            if (ok)
                onLogin()
        }
    ) {
        Column(Modifier.padding(8.dp)) {
            Text(
                stringResource(R.string.login_type_email),
                style = MaterialTheme.typography.h5,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )

            val focusRequester = remember { FocusRequester() }
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text(stringResource(R.string.login_email_address)) },
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Default.Email, null)
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
            )
            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }

            val manualUrl = Constants.MANUAL_URL.buildUpon()
                .appendPath(Constants.MANUAL_PATH_ACCOUNTS_COLLECTIONS)
                .fragment(Constants.MANUAL_FRAGMENT_SERVICE_DISCOVERY)
                .build()
            val emailInfo = HtmlCompat.fromHtml(stringResource(R.string.login_email_address_info, manualUrl), HtmlCompat.FROM_HTML_MODE_COMPACT)
            ClickableTextWithLink(
                emailInfo.toAnnotatedString(),
                style = MaterialTheme.typography.body1,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 16.dp)
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
}


@Composable
@Preview
fun LoginTypeEmail_Content_Preview() {
    LoginTypeEmail_Content(LoginInfo())
}