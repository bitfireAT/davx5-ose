/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.view.inputmethod.EditorInfo
import android.widget.CompoundButton
import android.widget.LinearLayout
import at.bitfire.davdroid.R
import kotlinx.android.synthetic.main.edit_password.view.*

class EditPassword(
        context: Context,
        attrs: AttributeSet?
): LinearLayout(context, attrs) {
    val NS_ANDROID = "http://schemas.android.com/apk/res/android"

    constructor(context: Context): this(context, null)

    init {
        inflate(context, R.layout.edit_password, this)

        attrs?.let {
            password.setHint(it.getAttributeResourceValue(NS_ANDROID, "hint", 0))
            password.setText(it.getAttributeValue(NS_ANDROID, "text"))
        }

        show_password.setOnCheckedChangeListener({ _: CompoundButton, isChecked: Boolean ->
            var inputType = password.inputType and EditorInfo.TYPE_MASK_VARIATION.inv()
            inputType = inputType or if (isChecked) EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD else EditorInfo.TYPE_TEXT_VARIATION_PASSWORD
            password.inputType = inputType
        })
    }

    fun getText() = password.text!!
    fun setText(text: CharSequence?) = password.setText(text)

    fun setError(error: CharSequence?) {
        password.error = error
    }

}
