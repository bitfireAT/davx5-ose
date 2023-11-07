package at.bitfire.davdroid.ui

import android.app.Application
import android.text.Spanned
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.text.HtmlCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import at.bitfire.davdroid.ui.UiUtils.toAnnotatedString
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.apache.commons.io.IOUtils
import javax.inject.Inject

class OpenSourceLicenseInfoProvider @Inject constructor(): AboutActivity.AppLicenseInfoProvider {

    @Composable
    override fun LicenseInfo() {
        LicenseInfoGpl()
    }

    @Composable
    fun LicenseInfoGpl(
        model: Model = viewModel()
    ) {
        model.gpl.observeAsState().value?.let { gpl ->
            OpenSourceLicenseInfo(gpl.toAnnotatedString())
        }
    }


    @HiltViewModel
    class Model @Inject constructor(app: Application): AndroidViewModel(app) {

        val gpl = MutableLiveData<Spanned>()

        init {
            viewModelScope.launch(Dispatchers.IO) {
                app.resources.assets.open("gplv3.html").use { inputStream ->
                    val raw = IOUtils.toString(inputStream, Charsets.UTF_8)
                    val html = HtmlCompat.fromHtml(raw, HtmlCompat.FROM_HTML_MODE_LEGACY)
                    gpl.postValue(html)
                }
            }
        }

    }

}


@Composable
fun OpenSourceLicenseInfo(license: AnnotatedString) {
    Text(license)
}

@Composable
@Preview
fun OpenSourceLicenseInfo_Preview() {
    OpenSourceLicenseInfo(AnnotatedString("It's open-source."))
}