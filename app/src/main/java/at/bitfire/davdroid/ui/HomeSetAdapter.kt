/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui

import android.app.Application
import android.content.Context
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Filter
import android.widget.TextView
import at.bitfire.davdroid.DavUtils
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.HomeSet

class HomeSetAdapter(
        context: Context
): ArrayAdapter<HomeSet>(context, R.layout.text_list_item, android.R.id.text1) {

    init {
        if (context is Application)
            throw IllegalArgumentException("Pass the Activity context, otherwise dark mode won't work")
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val data = getItem(position)!!

        val v: View = convertView ?: LayoutInflater.from(context).inflate(R.layout.text_list_item, parent, false)
        v.findViewById<TextView>(android.R.id.text1).apply {
            text = data.displayName ?: DavUtils.lastSegmentOfUrl(data.url)
        }
        v.findViewById<TextView>(android.R.id.text2).apply {
            text = data.url.toString()
            setSingleLine()
            ellipsize = TextUtils.TruncateAt.START
        }
        return v
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup) =
            getView(position, convertView, parent)


    override fun getFilter() = object: Filter() {
        override fun convertResultToString(resultValue: Any?): CharSequence {
            val homeSet = resultValue as HomeSet
            return homeSet.url.toString()
        }
        override fun performFiltering(constraint: CharSequence?) = FilterResults()
        override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
        }
    }

}