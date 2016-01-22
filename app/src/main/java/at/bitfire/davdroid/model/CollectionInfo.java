/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.model;

import android.content.ContentValues;

import at.bitfire.dav4android.DavResource;
import at.bitfire.dav4android.Property;
import at.bitfire.dav4android.property.AddressbookDescription;
import at.bitfire.dav4android.property.CalendarColor;
import at.bitfire.dav4android.property.CalendarDescription;
import at.bitfire.dav4android.property.CalendarTimezone;
import at.bitfire.dav4android.property.CurrentUserPrivilegeSet;
import at.bitfire.dav4android.property.DisplayName;
import at.bitfire.dav4android.property.ResourceType;
import at.bitfire.dav4android.property.SupportedAddressData;
import at.bitfire.dav4android.property.SupportedCalendarComponentSet;
import lombok.ToString;
import at.bitfire.davdroid.model.ServiceDB.*;

@ToString
public class CollectionInfo {
    public long id;
    public Long serviceID;

    public enum Type {
        ADDRESS_BOOK,
        CALENDAR
    };
    public Type type;

    public String url;

    public boolean readOnly;
    public String displayName, description;
    public Integer color;

    public String timeZone;
    public Boolean supportsVEVENT;
    public Boolean supportsVTODO;

    public boolean selected;

    // non-persistent properties
    public boolean confirmed;


    public static final Property.Name[] DAV_PROPERTIES = {
            ResourceType.NAME,
            CurrentUserPrivilegeSet.NAME,
            DisplayName.NAME,
            AddressbookDescription.NAME, SupportedAddressData.NAME,
            CalendarDescription.NAME, CalendarColor.NAME, SupportedCalendarComponentSet.NAME
    };

    public static CollectionInfo fromDavResource(DavResource dav) {
        CollectionInfo info = new CollectionInfo();
        info.url = dav.location.toString();

        ResourceType type = (ResourceType)dav.properties.get(ResourceType.NAME);
        if (type != null) {
            if (type.types.contains(ResourceType.ADDRESSBOOK))
                info.type = Type.ADDRESS_BOOK;
            else if (type.types.contains(ResourceType.CALENDAR))
                info.type = Type.CALENDAR;
        }

        boolean readOnly = false;
        CurrentUserPrivilegeSet privilegeSet = (CurrentUserPrivilegeSet)dav.properties.get(CurrentUserPrivilegeSet.NAME);
        if (privilegeSet != null)
            readOnly = !privilegeSet.mayWriteContent;

        DisplayName displayName = (DisplayName)dav.properties.get(DisplayName.NAME);
        if (displayName != null && !displayName.displayName.isEmpty())
            info.displayName = displayName.displayName;

        if (info.type == Type.ADDRESS_BOOK) {
            AddressbookDescription addressbookDescription = (AddressbookDescription)dav.properties.get(AddressbookDescription.NAME);
            if (addressbookDescription != null)
                info.description = addressbookDescription.description;

        } else if (info.type == Type.CALENDAR) {
            CalendarDescription calendarDescription = (CalendarDescription)dav.properties.get(CalendarDescription.NAME);
            if (calendarDescription != null)
                info.description = calendarDescription.description;

            CalendarColor calendarColor = (CalendarColor)dav.properties.get(CalendarColor.NAME);
            if (calendarColor != null)
                info.color = calendarColor.color;

            CalendarTimezone timeZone = (CalendarTimezone)dav.properties.get(CalendarTimezone.NAME);
            if (timeZone != null)
                info.timeZone = timeZone.vTimeZone;

            info.supportsVEVENT = info.supportsVTODO = true;
            SupportedCalendarComponentSet supportedCalendarComponentSet = (SupportedCalendarComponentSet)dav.properties.get(SupportedCalendarComponentSet.NAME);
            if (supportedCalendarComponentSet != null) {
                info.supportsVEVENT = supportedCalendarComponentSet.supportsEvents;
                info.supportsVTODO = supportedCalendarComponentSet.supportsTasks;
            }
        }

        return info;
    }


    public static CollectionInfo fromDB(ContentValues values) {
        CollectionInfo info = new CollectionInfo();
        info.id = values.getAsLong(Collections.ID);
        info.serviceID = values.getAsLong(Collections.SERVICE_ID);

        info.url = values.getAsString(Collections.URL);
        info.displayName = values.getAsString(Collections.DISPLAY_NAME);
        info.description = values.getAsString(Collections.DESCRIPTION);

        info.color = values.getAsInteger(Collections.COLOR);

        info.timeZone = values.getAsString(Collections.TIME_ZONE);
        info.supportsVEVENT = booleanField(values, Collections.SUPPORTS_VEVENT);
        info.supportsVTODO = booleanField(values, Collections.SUPPORTS_VTODO);

        info.selected = booleanField(values, Collections.SELECTED);
        return info;
    }

    public ContentValues toDB() {
        ContentValues values = new ContentValues();
        // Collections.SERVICE_ID is never changed

        values.put(Collections.URL, url);
        values.put(Collections.DISPLAY_NAME, displayName);
        values.put(Collections.DESCRIPTION, description);
        values.put(Collections.COLOR, color);

        values.put(Collections.TIME_ZONE, timeZone);
        if (supportsVEVENT != null)
            values.put(Collections.SUPPORTS_VEVENT, supportsVEVENT ? 1 : 0);
        if (supportsVTODO != null)
            values.put(Collections.SUPPORTS_VTODO, supportsVTODO ? 1 : 0);

        values.put(Collections.SELECTED, selected ? 1 : 0);
        return values;
    }


    private static Boolean booleanField(ContentValues values, String field) {
        Integer i = values.getAsInteger(field);
        if (i == null)
            return null;
        return i != 0;
    }

}
