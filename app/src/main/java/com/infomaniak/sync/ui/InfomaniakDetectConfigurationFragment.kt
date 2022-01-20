/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.infomaniak.sync.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.*
import androidx.lifecycle.Observer
import at.bitfire.davdroid.BuildConfig.APPLICATION_ID
import at.bitfire.davdroid.BuildConfig.CLIENT_ID
import at.bitfire.davdroid.R
import at.bitfire.davdroid.databinding.InfomaniakLoadingViewBinding
import at.bitfire.davdroid.model.Credentials
import at.bitfire.davdroid.ui.setup.AccountDetailsFragment
import at.bitfire.davdroid.ui.setup.DetectConfigurationFragment
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
import java.net.URI
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody


class InfomaniakDetectConfigurationFragment : Fragment() {

    private val loginModel by activityViewModels<LoginModel>()
    private val detectConfigurationModel by viewModels<DetectConfigurationFragment.DetectConfigurationModel>()
    lateinit var code: String
    private lateinit var binding: InfomaniakLoadingViewBinding

    companion object {
        fun newInstance(code: String) = InfomaniakDetectConfigurationFragment().apply {
            this.code = code
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch(Dispatchers.IO) {
            loginModel.baseURI = URI(SYNC_INFOMANIAK)
            loginModel.credentials = getCredential()

            withContext(Dispatchers.Main) {
                if (loginModel.credentials == null) {
                    parentFragmentManager.beginTransaction()
                        .add(DetectConfigurationFragment.NothingDetectedFragment(), null)
                        .commit()
                } else {
                    detectConfigurationModel.detectConfiguration(loginModel).observe(
                        this@InfomaniakDetectConfigurationFragment,
                        Observer { result ->

                            publishProgress(getString(R.string.infomaniak_login_finalising))

                            // save result for next step
                            loginModel.configuration = result

                            // remove "Detecting configuration" fragment, it shouldn't come back
                            parentFragmentManager.popBackStack()

                            if (result.calDAV != null || result.cardDAV != null)
                                parentFragmentManager.beginTransaction()
                                    .replace(android.R.id.content, AccountDetailsFragment())
                                    .addToBackStack(null)
                                    .commit()
                            else
                                parentFragmentManager.beginTransaction()
                                    .add(
                                        DetectConfigurationFragment.NothingDetectedFragment(),
                                        null
                                    )
                                    .commit()
                        })
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = InfomaniakLoadingViewBinding.inflate(inflater, container, false)
        return binding.root
    }

    private fun publishProgress(text: CharSequence) {
        activity?.runOnUiThread {
            binding.messageStatus.text = text
        }
    }

    private fun getCredential(): Credentials? {

        try {

            val okHttpClient = OkHttpClient.Builder()
                .build()

            val gson = Gson()

            val infomaniakLogin = InfomaniakLogin(
                context = requireContext(),
                appUID = APPLICATION_ID,
                clientID = CLIENT_ID
            )
            var formBuilder: MultipartBody.Builder = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("grant_type", "authorization_code")
                .addFormDataPart("client_id", CLIENT_ID)
                .addFormDataPart("code", code)
                .addFormDataPart("code_verifier", infomaniakLogin.getCodeVerifier())
                .addFormDataPart("redirect_uri", infomaniakLogin.getRedirectURI())

            var request = Request.Builder()
                .url(TOKEN_LOGIN_URL)
                .post(formBuilder.build())
                .build()

            var response = okHttpClient.newCall(request).execute()

            var responseBody: ResponseBody = response.body ?: return null
            var bodyResult = responseBody.string()

            val apiToken: ApiToken
            var jsonResult = JsonParser.parseString(bodyResult)
            if (response.isSuccessful) {
                apiToken = gson.fromJson(jsonResult.asJsonObject, ApiToken::class.java)
            } else {
                return null
            }

            request = Request.Builder()
                .url(PROFILE_API_URL)
                .header("Authorization", "Bearer " + apiToken.accessToken)
                .get()
                .build()

            publishProgress(getString(R.string.infomaniak_login_retrieving_account_information))

            response = okHttpClient.newCall(request).execute()

            responseBody = response.body ?: return null

            bodyResult = responseBody.string()

            if (response.isSuccessful) {
                jsonResult = JsonParser.parseString(bodyResult)
                val infomaniakUser = gson.fromJson(
                    jsonResult.asJsonObject.getAsJsonObject("data"),
                    InfomaniakUser::class.java
                )

                val formater = SimpleDateFormat("EEEE MMM d yyyy HH:mm:ss", Locale.getDefault())

                formBuilder = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("name", "Infomaniak Sync - " + formater.format(Date()))

                request = Request.Builder()
                    .url(PASSWORD_API_URL)
                    .header("Authorization", "Bearer " + apiToken.accessToken)
                    .post(formBuilder.build())
                    .build()

                publishProgress(getString(R.string.infomaniak_login_generating_an_application_password))

                response = okHttpClient.newCall(request).execute()

                responseBody = response.body ?: return null

                bodyResult = responseBody.string()
                if (response.isSuccessful) {
                    jsonResult = JsonParser.parseString(bodyResult)
                    val infomaniakPassword = gson.fromJson(
                        jsonResult.asJsonObject.getAsJsonObject("data"),
                        InfomaniakPassword::class.java
                    )

                    publishProgress(getText(R.string.login_querying_server))

                    return Credentials(infomaniakUser.login, infomaniakPassword.password, null)
                }
            }
            return null
        } catch (exception: Exception) {
            exception.printStackTrace()
            return null
        }
    }
}



