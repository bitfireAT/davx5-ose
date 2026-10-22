/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.davx5.feature.preconfiguredsettings.externalscanner

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract

/**
 * An [ActivityResultContract] to scan a barcode and receive the result.
 */
class ScanQrCode : ActivityResultContract<Unit, String?>() {
    override fun createIntent(context: Context, input: Unit): Intent {
        return Intent("com.google.zxing.client.android.SCAN")
    }

    override fun parseResult(resultCode: Int, intent: Intent?): String? {
        return intent?.getStringExtra("SCAN_RESULT")?.takeIf { resultCode == Activity.RESULT_OK }
    }
}
