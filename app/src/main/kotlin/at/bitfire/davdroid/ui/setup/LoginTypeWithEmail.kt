package at.bitfire.davdroid.ui.setup

import android.app.Application
import android.net.MailTo
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
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
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.Credentials
import at.bitfire.davdroid.ui.composable.PasswordTextField
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import java.net.URI
import javax.inject.Inject

@Suppress("unused")
class LoginTypeWithEmail @Inject constructor(
    val context: Application
) : LoginType {

    @Module
    @InstallIn(SingletonComponent::class)
    abstract class DefaultLoginTypeModule {
        @Binds
        @IntoSet
        abstract fun type(impl: LoginTypeWithEmail): LoginType
    }

    override fun isGeneric() = true

    override fun getOrder() = 100

    override fun getName() = context.getString(R.string.login_type_email)

    @Composable
    override fun LoginForm(
        initialLoginInfo: LoginInfo?,
        updateCanContinue: (Boolean) -> Unit,
        updateLoginInfo: (LoginInfo) -> Unit,
        onLogin: () -> Unit
    ) {
        var email by remember { mutableStateOf(initialLoginInfo?.credentials?.username ?: "") }
        var password by remember { mutableStateOf(initialLoginInfo?.credentials?.password ?: "") }

        if (email.isNotEmpty() && password.isNotEmpty()) {
            updateCanContinue(true)
            updateLoginInfo(LoginInfo(
                baseUri = URI(MailTo.MAILTO_SCHEME, email, null),
                credentials = Credentials(
                    username = email,
                    password = password
                )
            ))
        } else
            updateCanContinue(false)

        LoginFormWithEmail(
            email = email,
            onEmailChange = { email = it },
            password = password,
            onPasswordChange = { password = it },
            onContinue = onLogin
        )
    }
}

@Composable
fun LoginFormWithEmail(
    email: String,
    onEmailChange: (String) -> Unit = {},
    password: String,
    onPasswordChange: (String) -> Unit = {},
    onContinue: () -> Unit = {}
) {
    val focusRequester = remember { FocusRequester() }

    Column {
        OutlinedTextField(
            value = email,
            onValueChange = onEmailChange,
            label = { Text(stringResource(R.string.login_email_address)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            ),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
        )

        PasswordTextField(
            password = password,
            onPasswordChange = onPasswordChange,
            labelText = stringResource(R.string.login_password),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { onContinue() }
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

@Composable
@Preview
fun LoginFormWithEmail_Preview() {
    LoginFormWithEmail(
        email = "some@example.com",
        password = ""
    )
}