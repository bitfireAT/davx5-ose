package at.bitfire.davdroid.ui.widget

import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.core.text.HtmlCompat
import androidx.databinding.BindingAdapter

object BindingAdapters {

    @BindingAdapter("error")
    @JvmStatic
    fun setError(textView: TextView, error: String?) {
        textView.error = error
        textView.requestFocus()
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

}