package at.bitfire.davdroid.ui

import android.content.Context
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import at.bitfire.davdroid.model.HomeSet

class HomeSetAdapter(
        context: Context
): ArrayAdapter<HomeSet>(context, android.R.layout.simple_list_item_1, android.R.id.text1) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val data = getItem(position)!!
        val v = convertView ?: LayoutInflater.from(context).inflate(android.R.layout.simple_list_item_1, null, false)
        v.findViewById<TextView>(android.R.id.text1).apply {
            text = data.url.toString()
            setSingleLine()
            ellipsize = TextUtils.TruncateAt.START
        }
        return v
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        val data = getItem(position)!!
        val v = convertView ?: LayoutInflater.from(context).inflate(android.R.layout.simple_list_item_1, null, false)
        v.findViewById<TextView>(android.R.id.text1).apply {
            text = data.url.toString()
            ellipsize = TextUtils.TruncateAt.START
        }
        return v
    }

}