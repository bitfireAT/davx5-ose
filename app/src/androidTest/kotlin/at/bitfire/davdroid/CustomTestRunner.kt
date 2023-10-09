/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid

import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.testing.HiltTestApplication

class CustomTestRunner : AndroidJUnitRunner() {

    override fun newApplication(cl: ClassLoader, name: String, context: Context) =
        super.newApplication(cl, HiltTestApplication::class.java.name, context)

}