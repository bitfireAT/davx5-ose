/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.db

import android.content.Context
import androidx.room.Room
import androidx.room.migration.AutoMigrationSpec
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
    fun inMemoryDatabase(
        autoMigrations: Set<@JvmSuppressWildcards AutoMigrationSpec>,
        @ApplicationContext context: Context
    ): AppDatabase =
        Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            // auto-migration specs that need to be specified explicitly
            .apply {
                for (spec in autoMigrations) {
                    addAutoMigrationSpec(spec)
                }
            }
            .build()

}