/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
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
import at.bitfire.davdroid.resource.LocalTaskList;
import at.bitfire.davdroid.resource.ServerInfo;
import lombok.Getter;

/**
 * Order of display:
 *
 * number of rows                   type
 * nAddressBookHeadings (0 or 1)    heading: "address books"
 * nAddressBooks                    address book info
 * nCalendarHeadings (0 or 1)       heading: "calendars"
 * nCalendars                       calendar info
 * nNotebookHeadings (0 or 1)       heading: "notebooks"
 * nNotebooks                       notebook info
 * nTaskListHeadings (0 or 1)       heading: "task lists"
 * nTaskLists                       task list info
 */
public class SelectCollectionsAdapter extends BaseAdapter implements ListAdapter {
	final static int
			TYPE_ADDRESS_BOOKS_HEADING = 0,
			TYPE_ADDRESS_BOOKS_ROW = 1,
			TYPE_CALENDARS_HEADING = 2,
			TYPE_CALENDARS_ROW = 3,
			TYPE_TASK_LISTS_HEADING = 4,
			TYPE_TASK_LISTS_ROW = 5;
	
	final protected Context context;
	final protected ServerInfo serverInfo;
	@Getter protected int
			nAddressBooks, nAddressBookHeadings,
			nCalendars, nCalendarHeadings,
			nTaskLists, nTaskListHeadings;
	
	
	public SelectCollectionsAdapter(Context context, ServerInfo serverInfo) {
		this.context = context;
		
		this.serverInfo = serverInfo;
		nAddressBooks = serverInfo.getAddressBooks().length;
		nAddressBookHeadings = nAddressBooks == 0 ? 0 : 1;
		nCalendars = serverInfo.getCalendars().length;
		nCalendarHeadings = nCalendars == 0 ? 0 : 1;
		nTaskLists = serverInfo.getTaskLists().length;
		nTaskListHeadings = nTaskLists == 0 ? 0 : 1;
	}
	
	
	// item data
	
	@Override
	public int getCount() {
		return nAddressBookHeadings + nAddressBooks + nCalendarHeadings + nCalendars + nTaskListHeadings + nTaskLists;
	}

	@Override
	public Object getItem(int position) {
		if (position >= nAddressBookHeadings &&
			position < (nAddressBookHeadings + nAddressBooks))
			return serverInfo.getAddressBooks()[position - nAddressBookHeadings];

		else if (position >= (nAddressBookHeadings + nAddressBooks + nCalendarHeadings) &&
				(position < (nAddressBookHeadings + nAddressBooks + nCalendarHeadings + nCalendars)))
			return serverInfo.getCalendars()[position - (nAddressBookHeadings + nAddressBooks + nCalendarHeadings)];

		else if (position >= (nAddressBookHeadings + nAddressBooks + nCalendarHeadings + nCalendars + nTaskListHeadings) &&
				(position < (nAddressBookHeadings + nAddressBooks + nCalendarHeadings + nCalendars + nTaskListHeadings + nTaskLists)))
			return serverInfo.getTaskLists()[position - (nAddressBookHeadings + nAddressBooks + nCalendarHeadings + nCalendars + nTaskListHeadings)];

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
		return TYPE_TASK_LISTS_ROW + 1;
	}

	@Override
	public int getItemViewType(int position) {
		if ((nAddressBookHeadings != 0) && (position == 0))
			return TYPE_ADDRESS_BOOKS_HEADING;
		else if ((nAddressBooks != 0) && (position > 0) && (position < nAddressBookHeadings + nAddressBooks))
			return TYPE_ADDRESS_BOOKS_ROW;

		else if ((nCalendarHeadings != 0) && (position == nAddressBookHeadings + nAddressBooks))
			return TYPE_CALENDARS_HEADING;
		else if ((nCalendars != 0) && (position > nAddressBookHeadings + nAddressBooks) && (position < nAddressBookHeadings + nAddressBooks + nCalendarHeadings + nCalendars))
			return TYPE_CALENDARS_ROW;

		else if ((nTaskListHeadings != 0) && (position == nAddressBookHeadings + nAddressBooks + nCalendarHeadings + nCalendars))
			return TYPE_TASK_LISTS_HEADING;
		else if ((nTaskLists != 0) && (position > nAddressBookHeadings + nAddressBooks + nCalendarHeadings + nCalendars) && (position < nAddressBookHeadings + nAddressBooks + nCalendarHeadings + nCalendars + nTaskListHeadings + nTaskLists))
			return TYPE_TASK_LISTS_ROW;

		else
			return IGNORE_ITEM_VIEW_TYPE;
	}

	@Override
	@SuppressLint("InflateParams")
	public View getView(int position, View convertView, ViewGroup parent) {
		View v = convertView;

		int viewType = getItemViewType(position);
		
		// step 1: get view (either by creating or recycling)
		if (v == null) {
			LayoutInflater inflater = LayoutInflater.from(parent.getContext());
			switch (viewType) {
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
			case TYPE_TASK_LISTS_HEADING:
				v = inflater.inflate(R.layout.setup_task_lists_heading, parent, false);
				break;
			case TYPE_CALENDARS_ROW:
			case TYPE_TASK_LISTS_ROW:
				v = inflater.inflate(android.R.layout.simple_list_item_multiple_choice, null);
				v.setPadding(0, 8, 0, 8);
			}
		}
		
		// step 2: fill view with content
		switch (viewType) {
		case TYPE_ADDRESS_BOOKS_ROW:
			setContent((CheckedTextView)v, R.drawable.addressbook, (ServerInfo.ResourceInfo)getItem(position));
			break;
		case TYPE_CALENDARS_ROW:
			setContent((CheckedTextView)v, R.drawable.calendar, (ServerInfo.ResourceInfo)getItem(position));
			break;
		case TYPE_TASK_LISTS_ROW:
			setContent((CheckedTextView)v, R.drawable.tasks, (ServerInfo.ResourceInfo)getItem(position));
		}

		// disable task list selection if there's no local task provider
		if (viewType == TYPE_TASK_LISTS_ROW && !LocalTaskList.tasksProviderAvailable(context.getContentResolver())) {
			final CheckedTextView check = (CheckedTextView)v;
			check.setEnabled(false);
			check.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					check.setChecked(false);
				}
			});
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
			description = info.getUrl();
		
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
		return (type == TYPE_ADDRESS_BOOKS_ROW || type == TYPE_CALENDARS_ROW || type == TYPE_TASK_LISTS_ROW);
	}
}
