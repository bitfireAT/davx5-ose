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
import android.view.View
import android.widget.ListView

class MaximizedListView(
        context: Context,
        attrs: AttributeSet
): ListView(context, attrs) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)

        var width = 0
        var height = 0

        if (widthMode == MeasureSpec.EXACTLY || widthMode == MeasureSpec.AT_MOST)
            width = widthSize

        if (heightMode == MeasureSpec.EXACTLY)
            height = heightSize
        else {
            adapter?.let { listAdapter ->
                val widthSpec = View.MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY)
                for (i in 0 until listAdapter.count) {
                    val listItem = listAdapter.getView(i, null, this)
                    listItem.measure(widthSpec, View.MeasureSpec.UNSPECIFIED)
                    height += listItem.measuredHeight
                }
                height += dividerHeight * (listAdapter.count - 1)
            }
        }

        setMeasuredDimension(width, height)
    }

}
