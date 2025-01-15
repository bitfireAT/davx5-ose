package at.bitfire.davdroid.ui.setup

import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Test

class LoginActivityTest {

    @Test
    fun loginInfoFromIntent() {
        val intent = Intent().apply {
            data = Uri.parse("https://example.com:444/nextcloud")
            putExtra(LoginActivity.EXTRA_USERNAME, "user")
            putExtra(LoginActivity.EXTRA_PASSWORD, "password")
        }
        val loginInfo = LoginActivity.loginInfoFromIntent(intent)
        assertEquals("https://example.com:444/nextcloud", loginInfo.baseUri.toString())
        assertEquals("user", loginInfo.credentials!!.username)
        assertEquals("password", loginInfo.credentials.password)
    }
}