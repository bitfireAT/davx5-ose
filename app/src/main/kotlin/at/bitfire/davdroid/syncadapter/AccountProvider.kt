package at.bitfire.davdroid.syncadapter

import android.accounts.AccountManager
import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import at.bitfire.davdroid.R
import at.bitfire.davdroid.webdav.CredentialsStore

class AccountProvider : ContentProvider() {

    private val authority by lazy { context!!.getString(R.string.account_authority) }

    private val uriMatcher by lazy {
        UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(authority, "accounts", 1)
            addURI(authority, "account/*", 2)
        }
    }

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor {

        val accountManager = AccountManager.get(context)
        val accounts = accountManager.getAccountsByType(context!!.getString(R.string.account_type))
        val accountsNames = accounts.map { accountManager.getUserData(it, CredentialsStore.USER_NAME) }

        val cursor = MatrixCursor(arrayOf(CredentialsStore.USER_NAME))

        when (uriMatcher.match(uri)) {
            1 -> {
                accountsNames.forEach { accountName ->
                    cursor.addRow(arrayOf(accountName))
                }
            }
            2 -> {
                val accountName = uri.lastPathSegment
                if (accountsNames.contains(accountName)) {
                    cursor.addRow(arrayOf(accountName))
                }
            }
        }

        return cursor
    }

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
