/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.model;

import android.content.ContentValues;

import org.apache.commons.lang3.StringUtils;

import java.io.Serializable;

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
import at.bitfire.davdroid.model.ServiceDB.Collections;
import lombok.ToString;

@ToString
public class CollectionInfo implements Serializable {
    public long id;
    public Long serviceID;

    public enum Type {
        ADDRESS_BOOK,
        CALENDAR
    }
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
        info.url = dav.getLocation().toString();

        ResourceType type = (ResourceType)dav.getProperties().get(ResourceType.NAME);
        if (type != null) {
            if (type.getTypes().contains(ResourceType.ADDRESSBOOK))
                info.type = Type.ADDRESS_BOOK;
            else if (type.getTypes().contains(ResourceType.CALENDAR))
                info.type = Type.CALENDAR;
        }

        info.readOnly = false;
        CurrentUserPrivilegeSet privilegeSet = (CurrentUserPrivilegeSet)dav.getProperties().get(CurrentUserPrivilegeSet.NAME);
        if (privilegeSet != null)
            info.readOnly = !privilegeSet.getMayWriteContent();

        DisplayName displayName = (DisplayName)dav.getProperties().get(DisplayName.NAME);
        if (displayName != null && !StringUtils.isEmpty(displayName.getDisplayName()))
            info.displayName = displayName.getDisplayName();

        if (info.type == Type.ADDRESS_BOOK) {
            AddressbookDescription addressbookDescription = (AddressbookDescription)dav.getProperties().get(AddressbookDescription.NAME);
            if (addressbookDescription != null)
                info.description = addressbookDescription.getDescription();

        } else if (info.type == Type.CALENDAR) {
            CalendarDescription calendarDescription = (CalendarDescription)dav.getProperties().get(CalendarDescription.NAME);
            if (calendarDescription != null)
                info.description = calendarDescription.getDescription();

            CalendarColor calendarColor = (CalendarColor)dav.getProperties().get(CalendarColor.NAME);
            if (calendarColor != null)
                info.color = calendarColor.getColor();

            CalendarTimezone timeZone = (CalendarTimezone)dav.getProperties().get(CalendarTimezone.NAME);
            if (timeZone != null)
                info.timeZone = timeZone.getVTimeZone();

            info.supportsVEVENT = info.supportsVTODO = true;
            SupportedCalendarComponentSet supportedCalendarComponentSet = (SupportedCalendarComponentSet)dav.getProperties().get(SupportedCalendarComponentSet.NAME);
            if (supportedCalendarComponentSet != null) {
                info.supportsVEVENT = supportedCalendarComponentSet.getSupportsEvents();
                info.supportsVTODO = supportedCalendarComponentSet.getSupportsTasks();
            }
        }

        return info;
    }


    public static CollectionInfo fromDB(ContentValues values) {
        CollectionInfo info = new CollectionInfo();
        info.id = values.getAsLong(Collections.ID);
        info.serviceID = values.getAsLong(Collections.SERVICE_ID);

        info.url = values.getAsString(Collections.URL);
        info.readOnly = values.getAsInteger(Collections.READ_ONLY) != 0;
        info.displayName = values.getAsString(Collections.DISPLAY_NAME);
        info.description = values.getAsString(Collections.DESCRIPTION);

        info.color = values.getAsInteger(Collections.COLOR);

        info.timeZone = values.getAsString(Collections.TIME_ZONE);
        info.supportsVEVENT = getAsBooleanOrNull(values, Collections.SUPPORTS_VEVENT);
        info.supportsVTODO = getAsBooleanOrNull(values, Collections.SUPPORTS_VTODO);

        info.selected = values.getAsInteger(Collections.SYNC) != 0;
        return info;
    }

    public ContentValues toDB() {
        ContentValues values = new ContentValues();
        // Collections.SERVICE_ID is never changed

        values.put(Collections.URL, url);
        values.put(Collections.READ_ONLY, readOnly ? 1 : 0);
        values.put(Collections.DISPLAY_NAME, displayName);
        values.put(Collections.DESCRIPTION, description);
        values.put(Collections.COLOR, color);

        values.put(Collections.TIME_ZONE, timeZone);
        if (supportsVEVENT != null)
            values.put(Collections.SUPPORTS_VEVENT, supportsVEVENT ? 1 : 0);
        if (supportsVTODO != null)
            values.put(Collections.SUPPORTS_VTODO, supportsVTODO ? 1 : 0);

        values.put(Collections.SYNC, selected ? 1 : 0);
        return values;
    }


    private static Boolean getAsBooleanOrNull(ContentValues values, String field) {
        Integer i = values.getAsInteger(field);
        return (i == null) ? null : (i != 0);
    }

}
