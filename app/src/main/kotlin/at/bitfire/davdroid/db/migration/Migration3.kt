/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.db.migration

import androidx.room.migration.Migration
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import java.util.logging.Logger

val Migration3 = Migration(2, 3) { db ->
    // We don't have access to the context in a Room migration now, so
    // we will just drop those settings from old DAVx5 versions.
    Logger.getGlobal().warning("Dropping settings distrustSystemCerts and overrideProxy*")
}

@Module
@InstallIn(SingletonComponent::class)
internal object Migration3Module {
    @Provides @IntoSet
    fun provide(): Migration = Migration3
}