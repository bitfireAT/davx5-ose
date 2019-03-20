package at.bitfire.davdroid.ui

import android.content.Context
import android.text.TextUtils
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView

class HomesetAdapter(
        context: Context
): ArrayAdapter<String>(context, android.R.layout.simple_list_item_1, android.R.id.text1) {

    init {
        setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val data = getItem(position)!!
        val v = super.getView(position, convertView, parent)
        v.findViewById<TextView>(android.R.id.text1).apply {
            setSingleLine()
            ellipsize = TextUtils.TruncateAt.START
        }
        return v
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        val data = getItem(position)!!
        val v = super.getDropDownView(position, convertView, parent)
        v.findViewById<TextView>(android.R.id.text1).apply {
            ellipsize = TextUtils.TruncateAt.START
        }
        return v
    }

}