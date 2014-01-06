/*******************************************************************************
 * Copyright (c) 2014 Richard Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 * 
 * Contributors:
 *     Richard Hirner (bitfire web engineering) - initial API and implementation
 ******************************************************************************/
package at.bitfire.davdroid.syncadapter;

import java.util.List;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import at.bitfire.davdroid.webdav.WebDavResource;

public class WebDavResourceAdapter extends BaseAdapter {
	protected int viewId;
	protected LayoutInflater inflater;
	WebDavResource[] items;

	public WebDavResourceAdapter(Context context, int textViewResourceId, List<WebDavResource> objects) {
		viewId = textViewResourceId;
		inflater = LayoutInflater.from(context);
		items = objects.toArray(new WebDavResource[0]);
	}

	@Override
	public View getView(int position, View view, ViewGroup parent) {
		WebDavResource item = items[position];
		View itemView = (View)inflater.inflate(viewId, null);
		
		TextView textName = (TextView) itemView.findViewById(android.R.id.text1);
		textName.setText(item.getDisplayName());
		
		TextView textDescription = (TextView) itemView.findViewById(android.R.id.text2);
		String description = item.getDescription();
		if (description == null)
			description = item.getLocation().getPath();
		textDescription.setText(description);
		
		return itemView;
	}

	@Override
	public int getCount() {
		return items.length;
	}

	@Override
	public Object getItem(int position) {
		return items[position];
	}

	@Override
	public long getItemId(int position) {
		return position;
	}
}
