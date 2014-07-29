/*******************************************************************************
 * Copyright (c) 2014 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 ******************************************************************************/
package at.bitfire.davdroid.webdav;

import java.util.ArrayList;
import java.util.List;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Order;

@Order(elements={"prop","href"})
public class DavMultiget {
	public enum Type {
		ADDRESS_BOOK,
		CALENDAR
	}
	
	@Element
	DavProp prop;
	
	@ElementList(inline=true)
	List<DavHref> hrefs;
	
	
	public static DavMultiget newRequest(Type type, String names[]) {
		DavMultiget multiget = (type == Type.ADDRESS_BOOK) ? new DavAddressbookMultiget() : new DavCalendarMultiget(); 
		
		multiget.prop = new DavProp();
		multiget.prop.getetag = new DavProp.GetETag();
		
		if (type == Type.ADDRESS_BOOK)
			multiget.prop.addressData = new DavProp.AddressData();
		else if (type == Type.CALENDAR)
			multiget.prop.calendarData = new DavProp.CalendarData();

		multiget.hrefs = new ArrayList<DavHref>(names.length);
		for (String name : names)
			multiget.hrefs.add(new DavHref(name));
		
		return multiget;
	}
}
