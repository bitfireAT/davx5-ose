package at.bitfire.davdroid.ui.setup

import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Test

class LoginActivityTest {

    @Test
    fun loginInfoFromIntent() {
        val intent = Intent().apply {
            data = Uri.parse("https://example.com/nextcloud")
            putExtra(LoginActivity.EXTRA_USERNAME, "user")
            putExtra(LoginActivity.EXTRA_PASSWORD, "password")
        }
        val loginInfo = LoginActivity.loginInfoFromIntent(intent)
        assertEquals("https://example.com/nextcloud", loginInfo.baseUri.toString())
        assertEquals("user", loginInfo.credentials!!.username)
        assertEquals("password", loginInfo.credentials.password)
    }

    @Test
    fun loginInfoFromIntent_withPort() {
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

    @Test
    fun loginInfoFromIntent_implicit() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("davx5://user:password@example.com/path"))
        val loginInfo = LoginActivity.loginInfoFromIntent(intent)
        assertEquals("https://example.com/path", loginInfo.baseUri.toString())
        assertEquals("user", loginInfo.credentials!!.username)
        assertEquals("password", loginInfo.credentials.password)
    }

    @Test
    fun loginInfoFromIntent_implicit_withPort() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("davx5://user:password@example.com:0/path"))
        val loginInfo = LoginActivity.loginInfoFromIntent(intent)
        assertEquals("https://example.com:0/path", loginInfo.baseUri.toString())
        assertEquals("user", loginInfo.credentials!!.username)
        assertEquals("password", loginInfo.credentials.password)
    }
}