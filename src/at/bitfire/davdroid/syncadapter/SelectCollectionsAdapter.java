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

import lombok.Getter;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckedTextView;
import android.widget.ListAdapter;
import at.bitfire.davdroid.R;

public class SelectCollectionsAdapter extends BaseAdapter implements ListAdapter {
	final static int TYPE_ADDRESS_BOOKS_HEADING = 0,
		TYPE_ADDRESS_BOOKS_ROW = 1,
		TYPE_CALENDARS_HEADING = 2,
		TYPE_CALENDARS_ROW = 3;
	
	protected ServerInfo serverInfo;
	@Getter protected int nAddressBooks, nCalendars;
	
	
	public SelectCollectionsAdapter(ServerInfo serverInfo) {
		this.serverInfo = serverInfo;
		nAddressBooks = (serverInfo.getAddressBooks() == null) ? 0 : serverInfo.getAddressBooks().size();
		nCalendars = (serverInfo.getCalendars() == null) ? 0 : serverInfo.getCalendars().size();
	}
	
	
	// item data
	
	@Override
	public int getCount() {
		return nAddressBooks + nCalendars + 2;
	}

	@Override
	public Object getItem(int position) {
		if (position > 0 && position <= nAddressBooks)
			return serverInfo.getAddressBooks().get(position - 1);
		else if (position > nAddressBooks + 1)
			return serverInfo.getCalendars().get(position - nAddressBooks - 2);
		return null;
	}
	
	@Override
	public boolean hasStableIds() {
		return true;
	}

	@Override
	public long getItemId(int position) {
		return position;
	}
	
	
	// item views

	@Override
	public int getViewTypeCount() {
		return 4;
	}

	@Override
	public int getItemViewType(int position) {
		if (position == 0)
			return TYPE_ADDRESS_BOOKS_HEADING;
		else if (position <= nAddressBooks)
			return TYPE_ADDRESS_BOOKS_ROW;
		else if (position == nAddressBooks + 1)
			return TYPE_CALENDARS_HEADING;
		else if (position <= nAddressBooks + nCalendars + 1)
			return TYPE_CALENDARS_ROW;
		else
			return IGNORE_ITEM_VIEW_TYPE;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		// step 1: get view (either by creating or recycling)
		if (convertView == null) {
			LayoutInflater inflater = LayoutInflater.from(parent.getContext());
			switch (getItemViewType(position)) {
			case TYPE_ADDRESS_BOOKS_HEADING:
				convertView = inflater.inflate(R.layout.address_books_heading, parent, false);
				break;
			case TYPE_ADDRESS_BOOKS_ROW:
				convertView = inflater.inflate(android.R.layout.simple_list_item_single_choice, null);
				prepareRowView((CheckedTextView)convertView, R.drawable.addressbook);
				break;
			case TYPE_CALENDARS_HEADING:
				convertView = inflater.inflate(R.layout.calendars_heading, parent, false);
				break;
			case TYPE_CALENDARS_ROW:
				convertView = inflater.inflate(android.R.layout.simple_list_item_multiple_choice, null);
				prepareRowView((CheckedTextView)convertView, R.drawable.calendar);
			}
		}
		
		// step 2: fill view with content
		switch (getItemViewType(position)) {
		case TYPE_ADDRESS_BOOKS_ROW:
			setContent((CheckedTextView)convertView, (ServerInfo.ResourceInfo)getItem(position));
			break;
		case TYPE_CALENDARS_ROW:
			setContent((CheckedTextView)convertView, (ServerInfo.ResourceInfo)getItem(position));
		}
		
		return convertView;
	}
	
	protected void prepareRowView(CheckedTextView view, int resIcon) {
		view.setPadding(10, 10, 10, 10);
		view.setCompoundDrawablesWithIntrinsicBounds(resIcon, 0, 0, 0);
		view.setCompoundDrawablePadding(10);
	}
	
	protected void setContent(CheckedTextView view, ServerInfo.ResourceInfo info) {
		String description = info.getDescription();
		if (description == null)
			description = info.getPath();
		
		// FIXME escape HTML
		view.setText(Html.fromHtml("<b>" + info.getTitle() + "</b><br/>" + description));
	}

	@Override
	public boolean areAllItemsEnabled() {
		return false;
	}

	@Override
	public boolean isEnabled(int position) {
		int type = getItemViewType(position);
		return (type == TYPE_ADDRESS_BOOKS_ROW || type == TYPE_CALENDARS_ROW);
	}
}
