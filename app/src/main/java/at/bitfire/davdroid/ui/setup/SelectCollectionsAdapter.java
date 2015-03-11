/*
 * Copyright (c) 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */
package at.bitfire.davdroid.ui.setup;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckedTextView;
import android.widget.ListAdapter;

import at.bitfire.davdroid.R;
import at.bitfire.davdroid.resource.ServerInfo;
import lombok.Getter;

public class SelectCollectionsAdapter extends BaseAdapter implements ListAdapter {
	final static int TYPE_ADDRESS_BOOKS_HEADING = 0,
		TYPE_ADDRESS_BOOKS_ROW = 1,
		TYPE_CALENDARS_HEADING = 2,
		TYPE_CALENDARS_ROW = 3;
	
	protected Context context;
	protected ServerInfo serverInfo;
	@Getter protected int nAddressBooks, nAddressbookHeadings, nCalendars, nCalendarHeadings;
	
	
	public SelectCollectionsAdapter(Context context, ServerInfo serverInfo) {
		this.context = context;
		
		this.serverInfo = serverInfo;
		nAddressBooks = (serverInfo.getAddressBooks() == null) ? 0 : serverInfo.getAddressBooks().size();
		nAddressbookHeadings = (nAddressBooks == 0) ? 0 : 1;
		nCalendars = (serverInfo.getCalendars() == null) ? 0 : serverInfo.getCalendars().size();
		nCalendarHeadings = (nCalendars == 0) ? 0 : 1;
	}
	
	
	// item data
	
	@Override
	public int getCount() {
		return nAddressbookHeadings + nAddressBooks + nCalendarHeadings + nCalendars;
	}

	@Override
	public Object getItem(int position) {
		if (position >= nAddressbookHeadings &&
			position < (nAddressbookHeadings + nAddressBooks))
			return serverInfo.getAddressBooks().get(position - nAddressbookHeadings);
		else if (position >= (nAddressbookHeadings + nAddressBooks + nCalendarHeadings) &&
				(position < (nAddressbookHeadings + nAddressBooks + nCalendarHeadings + nCalendars)))
			return serverInfo.getCalendars().get(position - (nAddressbookHeadings + nAddressBooks + nCalendarHeadings));
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
		if ((nAddressbookHeadings != 0) && (position == 0))
			return TYPE_ADDRESS_BOOKS_HEADING;
		else if ((nAddressbookHeadings != 0) && (position > 0) && (position < nAddressbookHeadings + nAddressBooks))
			return TYPE_ADDRESS_BOOKS_ROW;
		else if ((nCalendars != 0) && (position == nAddressbookHeadings + nAddressBooks))
			return TYPE_CALENDARS_HEADING;
		else if ((nCalendars != 0) && (position > nAddressbookHeadings + nAddressBooks) && (position < nAddressbookHeadings + nAddressBooks + nCalendarHeadings + nCalendars))
			return TYPE_CALENDARS_ROW;
		else
			return IGNORE_ITEM_VIEW_TYPE;
	}

	@Override
	@SuppressLint("InflateParams")
	public View getView(int position, View convertView, ViewGroup parent) {
		View v = convertView;
		
		// step 1: get view (either by creating or recycling)
		if (v == null) {
			LayoutInflater inflater = LayoutInflater.from(parent.getContext());
			switch (getItemViewType(position)) {
			case TYPE_ADDRESS_BOOKS_HEADING:
				v = inflater.inflate(R.layout.setup_address_books_heading, parent, false);
				break;
			case TYPE_ADDRESS_BOOKS_ROW:
				v = inflater.inflate(android.R.layout.simple_list_item_single_choice, null);
				v.setPadding(0, 8, 0, 8);
				break;
			case TYPE_CALENDARS_HEADING:
				v = inflater.inflate(R.layout.setup_calendars_heading, parent, false);
				break;
			case TYPE_CALENDARS_ROW:
				v = inflater.inflate(android.R.layout.simple_list_item_multiple_choice, null);
				v.setPadding(0, 8, 0, 8);
			}
		}
		
		// step 2: fill view with content
		switch (getItemViewType(position)) {
		case TYPE_ADDRESS_BOOKS_ROW:
			setContent((CheckedTextView)v, R.drawable.addressbook, (ServerInfo.ResourceInfo)getItem(position));
			break;
		case TYPE_CALENDARS_ROW:
			setContent((CheckedTextView)v, R.drawable.calendar, (ServerInfo.ResourceInfo)getItem(position));
		}
		
		return v;
	}
	
	protected void setContent(CheckedTextView view, int collectionIcon, ServerInfo.ResourceInfo info) {
		// set layout and icons
		view.setCompoundDrawablesWithIntrinsicBounds(collectionIcon, 0, info.isReadOnly() ? R.drawable.ic_read_only : 0, 0);
		view.setCompoundDrawablePadding(10);
		
		// set text		
		String title = "<b>" + info.getTitle() + "</b>";
		if (info.isReadOnly())
			title = title + " (" + context.getString(R.string.setup_read_only) + ")";
		
		String description = info.getDescription();
		if (description == null)
			description = info.getURL();
		
		// FIXME escape HTML
		view.setText(Html.fromHtml(title + "<br/>" + description));
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
