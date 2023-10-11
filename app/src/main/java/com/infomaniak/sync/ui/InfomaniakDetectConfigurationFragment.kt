/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.infomaniak.sync.ui

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import at.bitfire.davdroid.BuildConfig.APPLICATION_ID
import at.bitfire.davdroid.BuildConfig.CLIENT_ID
import at.bitfire.davdroid.R
import at.bitfire.davdroid.databinding.InfomaniakLoadingViewBinding
import at.bitfire.davdroid.db.Credentials
import at.bitfire.davdroid.servicedetection.DavResourceFinder
import at.bitfire.davdroid.ui.setup.AccountDetailsFragment
import at.bitfire.davdroid.ui.setup.DetectConfigurationFragment.DetectConfigurationModel
import at.bitfire.davdroid.ui.setup.DetectConfigurationFragment.NothingDetectedFragment
import at.bitfire.davdroid.ui.setup.LoginModel
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.infomaniak.lib.login.ApiToken
import com.infomaniak.lib.login.InfomaniakLogin
import com.infomaniak.sync.GlobalConstants.PASSWORD_API_URL
import com.infomaniak.sync.GlobalConstants.PROFILE_API_URL
import com.infomaniak.sync.GlobalConstants.SYNC_INFOMANIAK
import com.infomaniak.sync.GlobalConstants.TOKEN_LOGIN_URL
import com.infomaniak.sync.model.InfomaniakPassword
import com.infomaniak.sync.model.InfomaniakUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class InfomaniakDetectConfigurationFragment : Fragment() {

    private val loginModel by activityViewModels<LoginModel>()
    private val detectConfigurationModel by viewModels<DetectConfigurationModel>()
    private lateinit var binding: InfomaniakLoadingViewBinding

    var externalArgumentCode: String? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return InfomaniakLoadingViewBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch(Dispatchers.IO) {
            setupLogin()
            withContext(Dispatchers.Main) { detectConfiguration() }
        }
    }

    private suspend fun setupLogin() {
        loginModel.apply {
            baseURI = URI(SYNC_INFOMANIAK)
            credentials = externalArgumentCode?.let { getCredentials(it) }
        }
    }

    private fun detectConfiguration() = with(loginModel) {
        credentials?.let {
            detectConfigurationModel
                .detectConfiguration(baseURI!!, credentials = it)
                .observe(this@InfomaniakDetectConfigurationFragment, ::navigateToAccountDetails)
        } ?: run {
            parentFragmentManager.beginTransaction()
                .add(NothingDetectedFragment(), null)
                .commit()
        }
    }

    private fun navigateToAccountDetails(result: DavResourceFinder.Configuration) {

        publishProgress(getString(R.string.infomaniak_login_finalising))

        // Save result for next step
        loginModel.configuration = result

        // Remove "Detecting configuration" fragment, it shouldn't come back
        parentFragmentManager.popBackStack()

        if (result.calDAV != null || result.cardDAV != null) {
            parentFragmentManager.beginTransaction()
                .replace(android.R.id.content, AccountDetailsFragment())
                .addToBackStack(null)
                .commit()
        } else {
            parentFragmentManager.beginTransaction()
                .add(NothingDetectedFragment(), null)
                .commit()
        }
    }

    private suspend fun getCredentials(code: String): Credentials? {
        try {

            val infomaniakLogin = requireContext().getInfomaniakLogin()
            val okHttpClient = OkHttpClient.Builder().build()
            val gson = Gson()

            val apiToken = getApiToken(code, infomaniakLogin, okHttpClient, gson) ?: return null
            val infomaniakUser = getInfomaniakUser(apiToken, okHttpClient, gson) ?: return null
            val infomaniakPassword = getInfomaniakPassword(apiToken, okHttpClient, gson) ?: return null

            publishProgress(getText(R.string.login_querying_server))
            val credentials = Credentials(infomaniakUser.login, infomaniakPassword.password)

            infomaniakLogin.deleteToken(
                okHttpClient,
                apiToken,
                onError = { Log.e("deleteTokenError", "API response error: $it}") },
            )

            return credentials

        } catch (exception: Exception) {
            exception.printStackTrace()
            return null
        }
    }

    private fun getApiToken(code: String, infomaniakLogin: InfomaniakLogin, okHttpClient: OkHttpClient, gson: Gson): ApiToken? {

        val formBuilder: MultipartBody.Builder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("grant_type", "authorization_code")
            .addFormDataPart("client_id", CLIENT_ID)
            .addFormDataPart("code", code)
            .addFormDataPart("code_verifier", infomaniakLogin.getCodeVerifier())
            .addFormDataPart("redirect_uri", infomaniakLogin.getRedirectURI())

        val request = Request.Builder()
            .url(TOKEN_LOGIN_URL)
            .post(formBuilder.build())
            .build()

        val response = okHttpClient.newCall(request).execute()

        return if (response.isSuccessful) {
            val body = response.body?.string() ?: return null
            val jsonObject = JsonParser.parseString(body).asJsonObject
            gson.fromJson(jsonObject, ApiToken::class.java)
        } else {
            null
        }
    }

    private fun getInfomaniakUser(apiToken: ApiToken, okHttpClient: OkHttpClient, gson: Gson): InfomaniakUser? {

        publishProgress(getString(R.string.infomaniak_login_retrieving_account_information))

        val request = Request.Builder()
            .url(PROFILE_API_URL)
            .header("Authorization", "Bearer ${apiToken.accessToken}")
            .get()
            .build()


        val response = okHttpClient.newCall(request).execute()

        return if (response.isSuccessful) {
            val body = response.body?.string() ?: return null
            val jsonObject = JsonParser.parseString(body).asJsonObject.getAsJsonObject("data")
            gson.fromJson(jsonObject, InfomaniakUser::class.java)
        } else {
            null
        }
    }

    private fun getInfomaniakPassword(apiToken: ApiToken, okHttpClient: OkHttpClient, gson: Gson): InfomaniakPassword? {

        publishProgress(getString(R.string.infomaniak_login_generating_an_application_password))

        val formatter = SimpleDateFormat("EEEE MMM d yyyy HH:mm:ss", Locale.getDefault())

        val formBuilder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("name", "Infomaniak Sync - ${formatter.format(Date())}")

        val request = Request.Builder()
            .url(PASSWORD_API_URL)
            .header("Authorization", "Bearer ${apiToken.accessToken}")
            .post(formBuilder.build())
            .build()

        val response = okHttpClient.newCall(request).execute()

        return if (response.isSuccessful) {
            val body = response.body?.string() ?: return null
            val jsonObject = JsonParser.parseString(body).asJsonObject.getAsJsonObject("data")
            gson.fromJson(jsonObject, InfomaniakPassword::class.java)
        } else {
            null
        }
    }

    private fun publishProgress(text: CharSequence) {
        activity?.runOnUiThread { binding.messageStatus.text = text }
    }

    companion object {

        fun newInstance(code: String? = null) = InfomaniakDetectConfigurationFragment().apply {
            externalArgumentCode = code
        }

        fun Context.getInfomaniakLogin() = InfomaniakLogin(context = this, appUID = APPLICATION_ID, clientID = CLIENT_ID)
    }
}
