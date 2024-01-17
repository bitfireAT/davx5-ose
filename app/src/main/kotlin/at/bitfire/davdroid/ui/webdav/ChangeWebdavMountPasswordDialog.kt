package at.bitfire.davdroid.ui.webdav

import android.app.Application
import android.content.DialogInterface
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import at.bitfire.davdroid.R
import at.bitfire.davdroid.webdav.CredentialsStore
import com.google.accompanist.themeadapter.material.MdcTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.HttpUrl

class ChangeWebdavMountPasswordDialog : DialogFragment() {
    companion object {
        const val ARG_MOUNT_ID = "mount_id"

        fun build(mountId: Long) =
            ChangeWebdavMountPasswordDialog().apply {
                arguments = bundleOf(
                    ARG_MOUNT_ID to mountId
                )
            }
    }

    private var mountId: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mountId = arguments?.getLong(ARG_MOUNT_ID)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        if (mountId == null) {
            dismiss()
            return View(requireContext())
        }
        return ComposeView(requireContext()).apply {
            setContent {
                MdcTheme {
                    DialogView()
                }
            }
        }
    }

    @Composable
    fun DialogView(
        model: Model = viewModel(factory = Model.Factory(mountId!!))
    ) {
        val context = LocalContext.current

        val isLoading by model.isLoading.observeAsState(false)
        val error by model.error.observeAsState(null)
        val password by model.password.observeAsState()

        LaunchedEffect(isLoading) {
            dialog?.setCanceledOnTouchOutside(!isLoading)
        }

        DialogView(
            password = password,
            onPasswordChanged = model.password::postValue,
            isLoading = isLoading,
            onSave = {
                model.save().invokeOnCompletion {
                    if (error == null) {
                        Toast.makeText(context, R.string.webdav_change_password_toast, Toast.LENGTH_SHORT)
                            .show()
                        dismiss()
                    }
                }
            },
            error = error,
            onDismiss = ::dismiss
        )
    }

    @Composable
    fun DialogView(
        password: String?,
        onPasswordChanged: (String) -> Unit,
        isLoading: Boolean,
        error: Throwable?,
        onSave: () -> Unit,
        onDismiss: () -> Unit
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Text(
                text = stringResource(R.string.webdav_change_password_title),
                style = MaterialTheme.typography.h5
            )
            Text(
                text = stringResource(R.string.webdav_change_password_message),
                style = MaterialTheme.typography.body1,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )

            var hidePassword by remember { mutableStateOf(true) }

            TextField(
                value = password ?: "",
                onValueChange = onPasswordChanged,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                maxLines = 1,
                keyboardActions = KeyboardActions { onSave() },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Go
                ),
                visualTransformation = if (hidePassword)
                    PasswordVisualTransformation()
                else
                    VisualTransformation.None,
                trailingIcon = {
                    IconButton(
                        onClick = { hidePassword = !hidePassword }
                    ) {
                        Icon(
                            imageVector = if (hidePassword)
                                Icons.Filled.Visibility
                            else
                                Icons.Filled.VisibilityOff,
                            contentDescription = null
                        )
                    }
                },
                enabled = !isLoading
            )
            Text(
                text = stringResource(R.string.webdav_change_password_supporting),
                style = MaterialTheme.typography.caption
            )
            AnimatedContent(
                targetState = error,
                label = "error animation"
            ) { throwable ->
                throwable?.message?.let { message ->
                    Text(
                        text = message,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        color = Color.Red,
                        style = MaterialTheme.typography.body2
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onDismiss,
                    enabled = !isLoading
                ) {
                    Text(text = stringResource(R.string.webdav_change_password_dismiss).uppercase())
                }
                TextButton(
                    onClick = onSave,
                    enabled = !isLoading
                ) {
                    Text(text = stringResource(R.string.webdav_change_password_save).uppercase())
                }
            }
        }
    }

    @Preview(showBackground = true)
    @Composable
    fun DialogView_Preview() {
        DialogView(
            password = "sample-password",
            onPasswordChanged = {},
            isLoading = false,
            error = Exception("If any error occurs, it will show here"),
            onSave = {},
            onDismiss = {}
        )
    }

    class Model(
        application: Application,
        private val mountId: Long
    ) : AndroidViewModel(application) {
        private val credentialsStore = CredentialsStore(application)
        val credentials = credentialsStore.getCredentials(mountId)

        val password = MutableLiveData<String?>(null)

        val isLoading = MutableLiveData(false)

        val error = MutableLiveData<Throwable?>(null)

        init {
            password.postValue(credentials?.password)

            // TODO: If no credentials are available, dismiss dialog, we are missing data
        }

        fun save() = viewModelScope.launch(Dispatchers.IO) {
            try {
                isLoading.postValue(true)
                error.postValue(null)

                val newCredentials = credentials?.copy(
                    password = password.value
                )
                credentialsStore.setCredentials(mountId, newCredentials)
            } catch (e: Throwable) {
                Log.e("CWMPDVM", "Could not store new credentials.", e)
                error.postValue(e)
            } finally {
                isLoading.postValue(false)
            }
        }


        class Factory(
            private val mountId: Long
        ) : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(
                modelClass: Class<T>,
                extras: CreationExtras
            ): T {
                // Get the Application object from extras
                val application = checkNotNull(extras[APPLICATION_KEY])
                return Model(application, mountId) as T
            }
        }
    }
}
