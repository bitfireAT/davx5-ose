/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui.composable

import android.app.Activity
import android.os.Build
import android.security.KeyChain
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import at.bitfire.davdroid.R
import kotlinx.coroutines.launch

@Composable
fun SelectClientCertificateCard(
    snackbarHostState: SnackbarHostState,
    suggestedAlias: String?,
    chosenAlias: String?,
    onAliasChosen: (String) -> Unit = {}
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(8.dp)) {
            Text(
                if (!chosenAlias.isNullOrEmpty())
                    stringResource(R.string.login_client_certificate_selected, chosenAlias)
                else
                    stringResource(R.string.login_no_client_certificate_optional),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(8.dp)
            )

            val activity = LocalContext.current as? Activity
            val scope = rememberCoroutineScope()
            OutlinedButton(
                onClick = {
                    if (activity != null)
                        KeyChain.choosePrivateKeyAlias(activity, { alias ->
                            if (alias != null)
                                onAliasChosen(alias)
                            else {
                                // Show a Snackbar to add a certificate if no certificate was found
                                // API Versions < 29 does that itself
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                                    scope.launch {
                                        if (snackbarHostState.showSnackbar(
                                                message = activity.getString(R.string.login_no_certificate_found),
                                                actionLabel = activity.getString(R.string.login_install_certificate),
                                                duration = SnackbarDuration.Long
                                            ) == SnackbarResult.ActionPerformed)
                                            activity.startActivity(KeyChain.createInstallIntent())
                                    }
                            }
                        }, null, null, null, -1, suggestedAlias)
                }
            ) {
                Text(stringResource(R.string.login_select_certificate))
            }
        }
    }
}

@Composable
@Preview
fun SelectClientCertificateCard_Preview_CertSelected() {
    SelectClientCertificateCard(
        snackbarHostState = SnackbarHostState(),
        suggestedAlias = "Test",
        chosenAlias = "Test"
    )
}

@Composable
@Preview
fun SelectClientCertificateCard_Preview_NothingSelected() {
    SelectClientCertificateCard(
        snackbarHostState = SnackbarHostState(),
        suggestedAlias = null,
        chosenAlias = null
    )
}