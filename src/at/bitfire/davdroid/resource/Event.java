/*******************************************************************************
 * Copyright (c) 2013 Richard Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 ******************************************************************************/
package at.bitfire.davdroid.resource;

import java.io.IOException;
import java.io.InputStream;

import lombok.Getter;
import lombok.Setter;
import android.util.Log;
import biweekly.Biweekly;
import biweekly.ICalendar;
import biweekly.component.VEvent;
import biweekly.property.DateEnd;
import biweekly.property.DateStart;


public class Event extends Resource {
	private final static String TAG = "davdroid.Event";
	
	@Getter @Setter private String uid, summary, location, description;
	@Getter @Setter private DateStart dtStart;
	@Getter @Setter private DateEnd dtEnd;
	

	public Event(String name, String ETag) {
		super(name, ETag);
	}
	
	public Event(long localID, String name, String ETag) {
		this(name, ETag);
		this.localID = localID;
	}


	@Override
	public void parseEntity(InputStream entity) throws IOException {
		if (entity == null)
			return;
		
		ICalendar ical = Biweekly.parse(entity).first();
		if (ical == null)
			return;
		
		// event		
		VEvent event = ical.getEvents().get(0);		// DAV .ics may contain only one entry
		if (event == null)
			return;
		
		if (event.getUid() != null)
			uid = event.getUid().toString();
		
		if (event.getSummary() != null)
			summary = event.getSummary().getValue();
		if (event.getLocation() != null)
			location = event.getLocation().getValue();
		if (event.getDescription() != null)
			description = event.getDescription().getValue();
		
		dtStart = event.getDateStart();
		dtEnd = event.getDateEnd();
		
		Log.i(TAG, "Parsed iCal: " + ical.write());
	}

	@Override
	public String toEntity() {
		ICalendar ical = new ICalendar();
		VEvent event = new VEvent();
		
		if (uid != null)
			event.setUid(uid);
		
		if (summary != null)
			event.setSummary(summary);
		if (location != null)
			event.setLocation(location);
		if (description != null)
			event.setDescription(description);
		
		event.setDateStart(dtStart);
		event.setDateEnd(dtEnd);
		
		ical.addEvent(event);
		Log.i(TAG, "Generated iCAL:" + ical.write());
		return ical.write();
	}

}
