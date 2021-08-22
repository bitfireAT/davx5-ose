package at.bitfire.davdroid.resource

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.Context
import at.bitfire.vcard4android.GroupMethod

class LocalTestAddressBook(
    context: Context,
    provider: ContentProviderClient,
    override val groupMethod: GroupMethod
): LocalAddressBook(context, ACCOUNT, provider) {

    override var mainAccount: Account = ACCOUNT
        get() = throw NotImplementedError()

    companion object {
        val ACCOUNT = Account("LocalTestAddressBook", "at.bitfire.davdroid.test")
    }

}