/*******************************************************************************
 * Copyright (c) 2013 Richard Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 ******************************************************************************/
package at.bitfire.davdroid.webdav;

import lombok.Getter;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.Namespace;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.Text;

@Namespace(prefix="D",reference="DAV:")
@Root(strict=false)
public class DavProp {
	@Element(required=false,name="current-user-principal")
	DavCurrentUserPrincipal currentUserPrincipal;
	
	@Element(required=false,name="addressbook-home-set")
	DavAddressbookHomeSet addressbookHomeSet;
	
	@Element(required=false,name="calendar-home-set")
	DavCalendarHomeSet calendarHomeSet;
	
	@Element(required=false)
	DavPropResourceType resourcetype;
	
	@Element(required=false)
	DavPropDisplayName displayname;
	
	@Element(required=false,name="addressbook-description")
	DavPropAddressbookDescription addressbookDescription;
	
	@Element(required=false,name="calendar-description")
	DavPropCalendarDescription calendarDescription;
	
	@Element(required=false)
	DavPropGetCTag getctag;
	
	@Element(required=false)
	DavPropGetETag getetag;
	
	@Element(name="address-data",required=false)
	DavPropAddressData addressData;

	@Element(name="calendar-data",required=false)
	DavPropCalendarData calendarData;


	public static class DavCurrentUserPrincipal {
		@Element(required=false)
		@Getter private DavHref href;
	}
	
	@Namespace(prefix="CD",reference="urn:ietf:params:xml:ns:carddav")
	public static class DavAddressbookHomeSet {
		@Element(required=false)
		@Getter private DavHref href;
	}
	
	@Namespace(prefix="C",reference="urn:ietf:params:xml:ns:caldav")
	public static class DavCalendarHomeSet {
		@Element(required=false)
		@Getter private DavHref href;
	}

	@Root(strict=false)
	public static class DavPropResourceType {
		@Element(required=false)
		@Getter private DavAddressbook addressbook;
		@Element(required=false)
		@Getter private DavCalendar calendar;
		
		@Namespace(prefix="CD",reference="urn:ietf:params:xml:ns:carddav")
		public static class DavAddressbook { }
		
		@Namespace(prefix="C",reference="urn:ietf:params:xml:ns:caldav")
		public static class DavCalendar { }
	}
	
	public static class DavPropDisplayName {
		@Text(required=false)
		@Getter private String displayName;
	}
	
	@Namespace(prefix="CD",reference="urn:ietf:params:xml:ns:carddav")
	public static class DavPropAddressbookDescription {
		@Text(required=false)
		@Getter private String description;
	}
	
	@Namespace(prefix="C",reference="urn:ietf:params:xml:ns:caldav")
	public static class DavPropCalendarDescription {
		@Text(required=false)
		@Getter private String description;
	}
	
	@Namespace(prefix="CS",reference="http://calendarserver.org/ns/")
	public static class DavPropGetCTag {
		@Text(required=false)
		@Getter private String CTag;
	}
	
	public static class DavPropGetETag {
		@Text(required=false)
		@Getter private String ETag;
	}

	@Namespace(prefix="CD",reference="urn:ietf:params:xml:ns:carddav")
	public static class DavPropAddressData {
		@Text(required=false)
		@Getter String vcard;
	}
	
	@Namespace(prefix="C",reference="urn:ietf:params:xml:ns:caldav")
	public static class DavPropCalendarData {
		@Text(required=false)
		@Getter String ical;
	}
}
