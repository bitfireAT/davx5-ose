/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.ui.widget

import android.content.Context
import android.support.v7.preference.EditTextPreference
import android.util.AttributeSet

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