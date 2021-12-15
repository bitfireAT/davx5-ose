/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui.widget

import android.content.Context
import android.util.AttributeSet
import androidx.preference.EditTextPreference

class IntEditTextPreference(
        context: Context,
        attrs: AttributeSet
): EditTextPreference(context, attrs) {

    override fun getPersistedString(defaultReturnValue: String?) =
            getPersistedInt(-1).toString()

    override fun persistString(value: String) =
            try {
                super.persistInt(value.toInt())
            } catch (e: NumberFormatException) {
                false
            }

}