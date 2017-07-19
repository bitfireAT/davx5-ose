/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.ui.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ListAdapter;
import android.widget.ListView;

public class MaximizedListView extends ListView {

    public MaximizedListView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        int widthMode = MeasureSpec.getMode(widthMeasureSpec),
            widthSize = MeasureSpec.getSize(widthMeasureSpec),
            heightMode = MeasureSpec.getMode(heightMeasureSpec),
            heightSize = MeasureSpec.getSize(heightMeasureSpec);

        int width = 0, height = 0;

        if (widthMode == MeasureSpec.EXACTLY || widthMode == MeasureSpec.AT_MOST)
            width = widthSize;

        if (heightMode == MeasureSpec.EXACTLY)
            height = heightSize;
        else {
            ListAdapter listAdapter = getAdapter();
            if (listAdapter != null) {
                int widthSpec = View.MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY);
                for (int i = 0; i < listAdapter.getCount(); i++) {
                    View listItem = listAdapter.getView(i, null, this);
                    listItem.measure(widthSpec, View.MeasureSpec.UNSPECIFIED);
                    height += listItem.getMeasuredHeight();
                }
                height += getDividerHeight() * (listAdapter.getCount() - 1);
            }
        }

        setMeasuredDimension(width, height);
    }

}
