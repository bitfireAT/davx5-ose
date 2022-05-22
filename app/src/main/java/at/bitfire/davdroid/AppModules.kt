/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid

import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.settings.SettingsManager
import at.bitfire.davdroid.syncadapter.AccountsUpdatedListener
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

object AppModules {

    val accountsUpdatedListenerModule = module {
        single {
            AccountsUpdatedListener(androidContext())
        }
    }

    val settingsModule = module {
        single {
            AppDatabase.createInstance(androidContext())
        }
        single {
            SettingsManager(androidContext())
        }
    }

    val storageLowReceiverModule = module {
        single {
            StorageLowReceiver(androidContext())
        }
    }


    val appModule = module {
        includes(accountsUpdatedListenerModule)
        includes(settingsModule)
        includes(storageLowReceiverModule)
    }

}