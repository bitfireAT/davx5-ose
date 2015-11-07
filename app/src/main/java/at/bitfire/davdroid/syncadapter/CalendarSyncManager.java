/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.syncadapter;

import android.accounts.Account;
import android.content.ContentValues;
import android.content.Context;
import android.content.SyncResult;
import android.os.Bundle;
import android.provider.CalendarContract.Calendars;
import android.text.TextUtils;

import com.squareup.okhttp.HttpUrl;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.ResponseBody;

import org.apache.commons.codec.Charsets;
import org.apache.commons.lang3.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import at.bitfire.dav4android.DavCalendar;
import at.bitfire.dav4android.DavResource;
import at.bitfire.dav4android.exception.DavException;
import at.bitfire.dav4android.exception.HttpException;
import at.bitfire.dav4android.property.CalendarColor;
import at.bitfire.dav4android.property.CalendarData;
import at.bitfire.dav4android.property.DisplayName;
import at.bitfire.dav4android.property.GetCTag;
import at.bitfire.dav4android.property.GetContentType;
import at.bitfire.dav4android.property.GetETag;
import at.bitfire.davdroid.ArrayUtils;
import at.bitfire.davdroid.Constants;
import at.bitfire.davdroid.R;
import at.bitfire.davdroid.resource.LocalCalendar;
import at.bitfire.davdroid.resource.LocalEvent;
import at.bitfire.davdroid.resource.LocalResource;
import at.bitfire.ical4android.CalendarStorageException;
import at.bitfire.ical4android.Event;
import at.bitfire.ical4android.InvalidCalendarException;
import at.bitfire.vcard4android.ContactsStorageException;
import lombok.Cleanup;

public class CalendarSyncManager extends SyncManager {

    protected static final int MAX_MULTIGET = 20;


    public CalendarSyncManager(Context context, Account account, Bundle extras, String authority, SyncResult result, LocalCalendar calendar) {
        super(Constants.NOTIFICATION_CALENDAR_SYNC, context, account, extras, authority, result);
        localCollection = calendar;
    }

    @Override
    protected String getSyncErrorTitle() {
        return context.getString(R.string.sync_error_calendar, account.name);
    }

    @Override
    protected void prepare() {
        collectionURL = HttpUrl.parse(localCalendar().getName());
        davCollection = new DavCalendar(log, httpClient, collectionURL);
    }

    @Override
    protected void queryCapabilities() throws DavException, IOException, HttpException, CalendarStorageException {
        davCollection.propfind(0, DisplayName.NAME, CalendarColor.NAME, GetCTag.NAME);

        // update name and color
        log.info("Setting calendar name and color (if available)");
        ContentValues values = new ContentValues(2);

        DisplayName pDisplayName = (DisplayName)davCollection.properties.get(DisplayName.NAME);
        if (pDisplayName != null && !TextUtils.isEmpty(pDisplayName.displayName))
            values.put(Calendars.CALENDAR_DISPLAY_NAME, pDisplayName.displayName);

        CalendarColor pColor = (CalendarColor)davCollection.properties.get(CalendarColor.NAME);
        if (pColor != null && pColor.color != null)
            values.put(Calendars.CALENDAR_COLOR, pColor.color);

        if (values.size() > 0)
            localCalendar().update(values);
    }

    @Override
    protected void prepareDirty() throws CalendarStorageException, ContactsStorageException {
        super.prepareDirty();

        localCalendar().processDirtyExceptions();
    }

    @Override
    protected RequestBody prepareUpload(LocalResource resource) throws IOException, CalendarStorageException {
        LocalEvent local = (LocalEvent)resource;
        return RequestBody.create(
                DavCalendar.MIME_ICALENDAR,
                local.getEvent().toStream().toByteArray()
        );
    }

    @Override
    protected void listRemote() throws IOException, HttpException, DavException {
        // fetch list of remote VEVENTs and build hash table to index file name
        davCalendar().calendarQuery("VEVENT");
        remoteResources = new HashMap<>(davCollection.members.size());
        for (DavResource iCal : davCollection.members) {
            String fileName = iCal.fileName();
            log.debug("Found remote VEVENT: " + fileName);
            remoteResources.put(fileName, iCal);
        }
    }

    @Override
    protected void downloadRemote() throws IOException, HttpException, DavException, CalendarStorageException {
        log.info("Downloading " + toDownload.size() + " events (" + MAX_MULTIGET + " at once)");

        // download new/updated iCalendars from server
        for (DavResource[] bunch : ArrayUtils.partition(toDownload.toArray(new DavResource[toDownload.size()]), MAX_MULTIGET)) {
            if (Thread.interrupted())
                return;
            log.info("Downloading " + StringUtils.join(bunch, ", "));

            if (bunch.length == 1) {
                // only one contact, use GET
                DavResource remote = bunch[0];

                ResponseBody body = remote.get("text/calendar");
                String eTag = ((GetETag)remote.properties.get(GetETag.NAME)).eTag;

                Charset charset = Charsets.UTF_8;
                MediaType contentType = body.contentType();
                if (contentType != null)
                    charset = contentType.charset(Charsets.UTF_8);

                @Cleanup InputStream stream = body.byteStream();
                processVEvent(remote.fileName(), eTag, stream, charset);

            } else {
                // multiple contacts, use multi-get
                List<HttpUrl> urls = new LinkedList<>();
                for (DavResource remote : bunch)
                    urls.add(remote.location);
                davCalendar().multiget(urls.toArray(new HttpUrl[urls.size()]));

                // process multiget results
                for (DavResource remote : davCollection.members) {
                    String eTag;
                    GetETag getETag = (GetETag)remote.properties.get(GetETag.NAME);
                    if (getETag != null)
                        eTag = getETag.eTag;
                    else
                        throw new DavException("Received multi-get response without ETag");

                    Charset charset = Charsets.UTF_8;
                    GetContentType getContentType = (GetContentType)remote.properties.get(GetContentType.NAME);
                    if (getContentType != null && getContentType.type != null) {
                        MediaType type = MediaType.parse(getContentType.type);
                        if (type != null)
                            charset = type.charset(Charsets.UTF_8);
                    }

                    CalendarData calendarData = (CalendarData)remote.properties.get(CalendarData.NAME);
                    if (calendarData == null || calendarData.iCalendar == null)
                        throw new DavException("Received multi-get response without address data");

                    @Cleanup InputStream stream = new ByteArrayInputStream(calendarData.iCalendar.getBytes());
                    processVEvent(remote.fileName(), eTag, stream, charset);
                }
            }
        }
    }


    // helpers

    private LocalCalendar localCalendar() { return ((LocalCalendar)localCollection); }
    private DavCalendar davCalendar() { return (DavCalendar)davCollection; }

    private void processVEvent(String fileName, String eTag, InputStream stream, Charset charset) throws IOException, CalendarStorageException {
        Event[] events;
        try {
            events = Event.fromStream(stream, charset);
        } catch (InvalidCalendarException e) {
            log.error("Received invalid iCalendar, ignoring");
            return;
        }

        if (events != null && events.length == 1) {
            Event newData = events[0];

            // delete local event, if it exists
            LocalEvent localEvent = (LocalEvent)localResources.get(fileName);
            if (localEvent != null) {
                log.info("Updating " + fileName + " in local calendar");
                localEvent.setETag(eTag);
                localEvent.update(newData);
                syncResult.stats.numUpdates++;
            } else {
                log.info("Adding " + fileName + " to local calendar");
                localEvent = new LocalEvent(localCalendar(), newData, fileName, eTag);
                localEvent.add();
                syncResult.stats.numInserts++;
            }
        } else
            log.error("Received VCALENDAR with not exactly one VEVENT with UID, but without RECURRENCE-ID; ignoring " + fileName);
    }

}
