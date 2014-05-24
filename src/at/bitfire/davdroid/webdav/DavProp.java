/*******************************************************************************
 * Copyright (c) 2014 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 ******************************************************************************/
package at.bitfire.davdroid.webdav;

import java.util.List;

import lombok.Getter;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Namespace;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.Text;

@Namespace(prefix="D",reference="DAV:")
@Root(strict=false)
public class DavProp {
	
	/* RFC 4918 WebDAV */
	
	@Element(required=false)
	DavPropResourceType resourcetype;
	
	@Element(required=false)
	DavPropDisplayName displayname;
	
	@Element(required=false)
	DavPropGetCTag getctag;
	
	@Element(required=false)
	DavPropGetETag getetag;
	
	@Root(strict=false)
	public static class DavPropResourceType {
		@Element(required=false)
		@Getter private Addressbook addressbook;
		@Element(required=false)
		@Getter private Calendar calendar;
		
		@Namespace(prefix="CD",reference="urn:ietf:params:xml:ns:carddav")
		public static class Addressbook { }
		
		@Namespace(prefix="C",reference="urn:ietf:params:xml:ns:caldav")
		public static class Calendar { }
	}
	
	public static class DavPropDisplayName {
		@Text(required=false)
		@Getter private String displayName;
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
	
	
	/* RFC 5397 WebDAV Current Principal Extension */
	
	@Element(required=false,name="current-user-principal")
	DavCurrentUserPrincipal currentUserPrincipal;

	public static class DavCurrentUserPrincipal {
		@Element(required=false)
		@Getter private DavHref href;
	}
	
	
	/* RFC 3744 WebDAV Access Control Protocol */
	
	@ElementList(required=false,name="current-user-privilege-set",entry="privilege")
	List<DavPropPrivilege> currentUserPrivilegeSet;
	
	public static class DavPropPrivilege {
		@Element(required=false)
		@Getter private PrivAll all;
		
		@Element(required=false)
		@Getter private PrivBind bind;
		
		@Element(required=false)
		@Getter private PrivUnbind unbind;
		
		@Element(required=false)
		@Getter private PrivWrite write;
		
		@Element(required=false,name="write-content")
		@Getter private PrivWriteContent writeContent;
		
		public static class PrivAll { }
		public static class PrivBind { }
		public static class PrivUnbind { }
		public static class PrivWrite { }
		public static class PrivWriteContent { }
	}

	

	/* RFC 4791 CalDAV, RFC 6352 CardDAV */
	
	@Element(required=false,name="addressbook-home-set")
	DavAddressbookHomeSet addressbookHomeSet;
	
	@Element(required=false,name="calendar-home-set")
	DavCalendarHomeSet calendarHomeSet;

	@Element(required=false,name="addressbook-description")
	DavPropAddressbookDescription addressbookDescription;
	
	@Element(required=false,name="calendar-description")
	DavPropCalendarDescription calendarDescription;
	
	@Element(required=false,name="calendar-color")
	DavPropCalendarColor calendarColor;
	
	@Element(required=false,name="calendar-timezone")
	DavPropCalendarTimezone calendarTimezone;
	
	@Namespace(prefix="C",reference="urn:ietf:params:xml:ns:caldav")
	@ElementList(required=false,name="supported-calendar-component-set",entry="comp")
	List<DavPropComp> supportedCalendarComponentSet;
	
	@Element(name="address-data",required=false)
	DavPropAddressData addressData;

	@Element(name="calendar-data",required=false)
	DavPropCalendarData calendarData;

	
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
	
	@Namespace(prefix="A",reference="http://apple.com/ns/ical/")
	public static class DavPropCalendarColor {
		@Text(required=false)
		@Getter private String color;
	}
	
	@Namespace(prefix="C",reference="urn:ietf:params:xml:ns:caldav")
	public static class DavPropCalendarTimezone {
		@Text(required=false)
		@Getter private String timezone;
	}
	
	@Namespace(prefix="C",reference="urn:ietf:params:xml:ns:caldav")
	public static class DavPropComp {
		@Attribute
		@Getter String name;
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
