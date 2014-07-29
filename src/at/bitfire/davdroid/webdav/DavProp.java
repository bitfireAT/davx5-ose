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
	ResourceType resourcetype;
	
	@Element(required=false)
	DisplayName displayname;
	
	@Element(required=false)
	GetCTag getctag;
	
	@Element(required=false)
	GetETag getetag;
	
	@Root(strict=false)
	public static class ResourceType {
		@Element(required=false)
		@Getter private Addressbook addressbook;
		@Element(required=false)
		@Getter private Calendar calendar;
		
		@Namespace(prefix="CD",reference="urn:ietf:params:xml:ns:carddav")
		public static class Addressbook { }
		
		@Namespace(prefix="C",reference="urn:ietf:params:xml:ns:caldav")
		public static class Calendar { }
	}
	
	public static class DisplayName {
		@Text(required=false)
		@Getter private String displayName;
	}
	
	@Namespace(prefix="CS",reference="http://calendarserver.org/ns/")
	public static class GetCTag {
		@Text(required=false)
		@Getter private String CTag;
	}
	
	public static class GetETag {
		@Text(required=false)
		@Getter private String ETag;
	}
	
	
	/* RFC 5397 WebDAV Current Principal Extension */
	
	@Element(required=false,name="current-user-principal")
	CurrentUserPrincipal currentUserPrincipal;

	public static class CurrentUserPrincipal {
		@Element(required=false)
		@Getter private DavHref href;
	}
	
	
	/* RFC 3744 WebDAV Access Control Protocol */
	
	@ElementList(required=false,name="current-user-privilege-set",entry="privilege")
	List<Privilege> currentUserPrivilegeSet;
	
	public static class Privilege {
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
	AddressbookHomeSet addressbookHomeSet;
	
	@Element(required=false,name="calendar-home-set")
	CalendarHomeSet calendarHomeSet;

	@Element(required=false,name="addressbook-description")
	AddressbookDescription addressbookDescription;

	@Namespace(prefix="CD",reference="urn:ietf:params:xml:ns:carddav")
	@ElementList(required=false,name="supported-address-data",entry="address-data-type")
	List<AddressDataType> supportedAddressData;
	
	@Element(required=false,name="calendar-description")
	CalendarDescription calendarDescription;
	
	@Element(required=false,name="calendar-color")
	CalendarColor calendarColor;
	
	@Element(required=false,name="calendar-timezone")
	CalendarTimezone calendarTimezone;
	
	@Namespace(prefix="C",reference="urn:ietf:params:xml:ns:caldav")
	@ElementList(required=false,name="supported-calendar-component-set",entry="comp")
	List<Comp> supportedCalendarComponentSet;
	
	@Element(name="address-data",required=false)
	AddressData addressData;

	@Element(name="calendar-data",required=false)
	CalendarData calendarData;

	
	@Namespace(prefix="CD",reference="urn:ietf:params:xml:ns:carddav")
	public static class AddressbookHomeSet {
		@Element(required=false)
		@Getter private DavHref href;
	}
	
	@Namespace(prefix="C",reference="urn:ietf:params:xml:ns:caldav")
	public static class CalendarHomeSet {
		@Element(required=false)
		@Getter private DavHref href;
	}
	
	@Namespace(prefix="CD",reference="urn:ietf:params:xml:ns:carddav")
	public static class AddressbookDescription {
		@Text(required=false)
		@Getter private String description;
	}
	
	@Namespace(prefix="CD",reference="urn:ietf:params:xml:ns:carddav")
	public static class AddressDataType {
		@Attribute(name="content-type")
		@Getter private String contentType;
		
		@Attribute
		@Getter private String version;
	}
	
	@Namespace(prefix="C",reference="urn:ietf:params:xml:ns:caldav")
	public static class CalendarDescription {
		@Text(required=false)
		@Getter private String description;
	}
	
	@Namespace(prefix="A",reference="http://apple.com/ns/ical/")
	public static class CalendarColor {
		@Text(required=false)
		@Getter private String color;
	}
	
	@Namespace(prefix="C",reference="urn:ietf:params:xml:ns:caldav")
	public static class CalendarTimezone {
		@Text(required=false)
		@Getter private String timezone;
	}
	
	@Namespace(prefix="C",reference="urn:ietf:params:xml:ns:caldav")
	public static class Comp {
		@Attribute
		@Getter String name;
	}

	@Namespace(prefix="CD",reference="urn:ietf:params:xml:ns:carddav")
	public static class AddressData {
		@Text(required=false)
		@Getter String vcard;
	}
	
	@Namespace(prefix="C",reference="urn:ietf:params:xml:ns:caldav")
	public static class CalendarData {
		@Text(required=false)
		@Getter String ical;
	}
}
