/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.db.migration

import androidx.room.ProvidedAutoMigrationSpec
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Inject

/**
 * Moves WebDAV credentials from the deprecated EncryptedSharedPreferences to the database.
 */
@ProvidedAutoMigrationSpec
class AutoMigration19 @Inject constructor() : AutoMigrationSpec {

    override fun onPostMigrate(db: SupportSQLiteDatabase) {
        // TODO
    }


    @Module
    @InstallIn(SingletonComponent::class)
    abstract class AutoMigrationModule {
        @Binds
        @IntoSet
        abstract fun provide(impl: AutoMigration19): AutoMigrationSpec
    }

}