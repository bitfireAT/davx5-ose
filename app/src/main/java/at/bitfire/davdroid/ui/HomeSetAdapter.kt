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
): ArrayAdapter<HomeSet>(context, android.R.layout.simple_list_item_2, android.R.id.text1) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val data = getItem(position)!!

        val v: View
        if (!data.displayName.isNullOrBlank()) {
            v = convertView ?: LayoutInflater.from(context).inflate(android.R.layout.simple_list_item_2, null, false)
            v.findViewById<TextView>(android.R.id.text1).text = data.displayName
            v.findViewById<TextView>(android.R.id.text2).apply {
                text = data.url.toString()
                setSingleLine()
                ellipsize = TextUtils.TruncateAt.START
            }
        } else {
            v = convertView ?: LayoutInflater.from(context).inflate(android.R.layout.simple_list_item_1, null, false)
            v.findViewById<TextView>(android.R.id.text1).apply {
                text = data.url.toString()
                setSingleLine()
                ellipsize = TextUtils.TruncateAt.START
            }
        }

        return v
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup) =
            getView(position, convertView, parent)

    /*: View {
        val data = getItem(position)!!
        val v = convertView ?: LayoutInflater.from(context).inflate(android.R.layout.simple_list_item_2, null, false)
        v.findViewById<TextView>(android.R.id.text1).apply {
            text = data.url.toString()
            setSingleLine()
            ellipsize = TextUtils.TruncateAt.START
        }
        v.findViewById<TextView>(android.R.id.text2).text = data.displayName
        return v
    }*/

}