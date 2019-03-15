package at.bitfire.davdroid.ui.widget

import android.widget.TextView
import androidx.databinding.BindingAdapter

object BindingAdapters {

    @BindingAdapter("error")
    fun setError(textView: TextView, text: String) {
        textView.error = text
    }

}