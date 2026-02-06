/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.davx5.ose.ui.about

import android.content.Context
import android.text.Spanned
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.text.HtmlCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import at.bitfire.davdroid.di.qualifier.IoDispatcher
import at.bitfire.davdroid.ui.UiUtils.toAnnotatedString
import at.bitfire.davdroid.ui.about.AboutActivity
import com.google.common.io.CharStreams
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
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
        model.gpl?.let { OpenSourceLicenseInfo(it.toAnnotatedString()) }
    }


    @HiltViewModel
    class Model @Inject constructor(
        @ApplicationContext private val context: Context,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher
    ): ViewModel() {

        var gpl by mutableStateOf<Spanned?>(null)

        init {
            viewModelScope.launch(ioDispatcher) {
                context.resources.assets.open("gplv3.html").use { inputStream ->
                    val raw = CharStreams.toString(inputStream.bufferedReader())
                    gpl = HtmlCompat.fromHtml(raw, HtmlCompat.FROM_HTML_MODE_LEGACY)
                }
            }
        }

    }

}


@Composable
fun OpenSourceLicenseInfo(license: AnnotatedString) {
    Text(text = license)
}

@Composable
@Preview
fun OpenSourceLicenseInfo_Preview() {
    OpenSourceLicenseInfo(AnnotatedString("It's open-source."))
}