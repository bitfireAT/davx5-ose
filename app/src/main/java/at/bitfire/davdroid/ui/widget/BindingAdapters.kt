/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui.widget

import android.text.method.LinkMovementMethod
import android.widget.AutoCompleteTextView
import android.widget.TextView
import androidx.core.text.HtmlCompat
import androidx.databinding.BindingAdapter
import androidx.databinding.InverseBindingAdapter
import org.apache.commons.lang3.StringUtils

object BindingAdapters {

    @BindingAdapter("error")
    @JvmStatic
    fun setError(textView: TextView, error: String?) {
        textView.error = StringUtils.trimToNull(error)
    }

    @BindingAdapter("html")
    @JvmStatic
    fun setHtml(textView: TextView, html: String?) {
        if (html != null) {
            textView.text = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY)
            textView.movementMethod = LinkMovementMethod.getInstance()
        } else
            textView.text = null
    }


    @BindingAdapter("unfilteredText")
    @JvmStatic
    fun setUnfilteredText(textView: AutoCompleteTextView, text: String?) {
        if (textView.text.toString() != text)
            textView.setText(text, false)
    }

    @InverseBindingAdapter(attribute = "unfilteredText", event = "android:textAttrChanged")
    @JvmStatic
    fun getUnfilteredText(textView: AutoCompleteTextView) = textView.text.toString()

}
