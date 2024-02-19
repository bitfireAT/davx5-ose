/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.viewModels
import com.google.accompanist.themeadapter.material.MdcTheme

class PermissionsActivity: AppCompatActivity() {

    val model by viewModels<PermissionsFragment.Model>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MdcTheme {
                PermissionsFragmentContent()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        model.checkPermissions()
    }

}