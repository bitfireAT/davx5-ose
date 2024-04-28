/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.account

import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.components.ActivityComponent

@AndroidEntryPoint
abstract class CreateCollectionActivity: AppCompatActivity() {

    @EntryPoint
    @InstallIn(ActivityComponent::class)
    interface CreateCollectionEntryPoint {
        fun createCollectionModelFactory(): CreateCollectionModel.Factory
    }

}