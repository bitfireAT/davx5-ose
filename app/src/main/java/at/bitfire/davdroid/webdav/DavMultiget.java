/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */
package at.bitfire.davdroid.webdav;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Order;

import java.util.ArrayList;
import java.util.List;

@Order(elements={"prop","href"})
public class DavMultiget {
	public enum Type {
		ADDRESS_BOOK,
		ADDRESS_BOOK_V4,
		CALENDAR
	}
	
	@Element
	DavProp prop;
	
	@ElementList(inline=true)
	List<DavHref> hrefs;
	
	
	public static DavMultiget newRequest(Type type, String names[]) {
		DavMultiget multiget = (type == Type.CALENDAR) ? new DavCalendarMultiget() : new DavAddressbookMultiget();
		
		multiget.prop = new DavProp();
		multiget.prop.getetag = new DavProp.GetETag();

		switch (type) {
			case ADDRESS_BOOK:
				multiget.prop.addressData = new DavProp.AddressData();
				break;
			case ADDRESS_BOOK_V4:
				DavProp.AddressData addressData = new DavProp.AddressData();
				addressData.setContentType("text/vcard");
				addressData.setVersion("4.0");
				multiget.prop.addressData = addressData;
				break;
			case CALENDAR:
				multiget.prop.calendarData = new DavProp.CalendarData();
		}

		multiget.hrefs = new ArrayList<>(names.length);
		for (String name : names)
			multiget.hrefs.add(new DavHref(name));
		
		return multiget;
	}
}
