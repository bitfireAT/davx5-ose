/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.db

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

@Module
@TestInstallIn(
    components = [ SingletonComponent::class ],
    replaces = [
        AppDatabase.AppDatabaseModule::class
    ]
)
class MemoryDbModule {

    @Provides
    @Singleton
    fun inMemoryDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            // auto-migrations that need to be specified explicitly
            .addAutoMigrationSpec(AppDatabase.AutoMigration11_12(context))
            .build()

}