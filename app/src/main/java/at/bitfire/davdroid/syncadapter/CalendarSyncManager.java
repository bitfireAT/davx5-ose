/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.syncadapter;

import android.accounts.Account;
import android.content.ContentProviderClient;
import android.content.ContentValues;
import android.content.Context;
import android.content.SyncResult;
import android.os.Bundle;
import android.provider.CalendarContract.Calendars;
import android.text.TextUtils;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.squareup.okhttp.HttpUrl;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.ResponseBody;

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
import at.bitfire.dav4android.property.AddressData;
import at.bitfire.dav4android.property.CalendarColor;
import at.bitfire.dav4android.property.CalendarData;
import at.bitfire.dav4android.property.DisplayName;
import at.bitfire.dav4android.property.GetCTag;
import at.bitfire.dav4android.property.GetContentType;
import at.bitfire.dav4android.property.GetETag;
import at.bitfire.davdroid.ArrayUtils;
import at.bitfire.davdroid.Constants;
import at.bitfire.davdroid.resource.LocalCalendar;
import at.bitfire.davdroid.resource.LocalContact;
import at.bitfire.davdroid.resource.LocalEvent;
import at.bitfire.davdroid.resource.LocalResource;
import at.bitfire.ical4android.AndroidHostInfo;
import at.bitfire.ical4android.CalendarStorageException;
import at.bitfire.ical4android.Event;
import at.bitfire.ical4android.InvalidCalendarException;
import at.bitfire.vcard4android.Contact;
import at.bitfire.vcard4android.ContactsStorageException;
import lombok.Cleanup;

public class CalendarSyncManager extends SyncManager {

    protected static final int
            MAX_MULTIGET = 30,
            NOTIFICATION_ID = 2;

    protected AndroidHostInfo hostInfo;


    public CalendarSyncManager(Context context, Account account, Bundle extras, ContentProviderClient provider, SyncResult result, LocalCalendar calendar) {
        super(NOTIFICATION_ID, context, account, extras, provider, result);
        localCollection = calendar;
    }


    @Override
    protected void prepare() {
        Thread.currentThread().setContextClassLoader(context.getClassLoader());

        hostInfo = new AndroidHostInfo(context.getContentResolver());

        collectionURL = HttpUrl.parse(localCalendar().getName());
        davCollection = new DavCalendar(httpClient, collectionURL);
    }

    @Override
    protected void queryCapabilities() throws DavException, IOException, HttpException, CalendarStorageException {
        davCollection.propfind(0, DisplayName.NAME, CalendarColor.NAME, GetCTag.NAME);

        // update name and color
        DisplayName pDisplayName = (DisplayName)davCollection.properties.get(DisplayName.NAME);
        String displayName = (pDisplayName != null && !TextUtils.isEmpty(pDisplayName.displayName)) ?
                pDisplayName.displayName : collectionURL.toString();

        CalendarColor pColor = (CalendarColor)davCollection.properties.get(CalendarColor.NAME);
        int color = (pColor != null && pColor.color != null) ? pColor.color : LocalCalendar.defaultColor;

        ContentValues values = new ContentValues(2);
        Constants.log.info("Setting new calendar name \"" + displayName + "\" and color 0x" + Integer.toHexString(color));
        values.put(Calendars.CALENDAR_DISPLAY_NAME, displayName);
        values.put(Calendars.CALENDAR_COLOR, color);
        localCalendar().update(values);
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
        for (DavResource vCard : davCollection.members) {
            String fileName = vCard.fileName();
            Constants.log.debug("Found remote VEVENT: " + fileName);
            remoteResources.put(fileName, vCard);
        }
    }

    @Override
    protected void downloadRemote() throws IOException, HttpException, DavException, CalendarStorageException {
        Constants.log.info("Downloading " + toDownload.size() + " events (" + MAX_MULTIGET + " at once)");

        // download new/updated iCalendars from server
        for (DavResource[] bunch : ArrayUtils.partition(toDownload.toArray(new DavResource[toDownload.size()]), MAX_MULTIGET)) {
            Constants.log.info("Downloading " + Joiner.on(" + ").join(bunch));

            if (bunch.length == 1) {
                // only one contact, use GET
                DavResource remote = bunch[0];

                ResponseBody body = remote.get("text/calendar");
                String eTag = ((GetETag)remote.properties.get(GetETag.NAME)).eTag;

                @Cleanup InputStream stream = body.byteStream();
                processVEvent(remote.fileName(), eTag, stream, body.contentType().charset(Charsets.UTF_8));

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
            events = Event.fromStream(stream, charset, hostInfo);
        } catch (InvalidCalendarException e) {
            Constants.log.error("Received invalid iCalendar, ignoring");
            return;
        }

        if (events.length == 1) {
            Event newData = events[0];

            // delete local event, if it exists
            LocalEvent localEvent = (LocalEvent)localResources.get(fileName);
            if (localEvent != null) {
                Constants.log.info("Updating " + fileName + " in local calendar");
                localEvent.setETag(eTag);
                localEvent.update(newData);
                syncResult.stats.numUpdates++;
            } else {
                Constants.log.info("Adding " + fileName + " to local calendar");
                localEvent = new LocalEvent(localCalendar(), newData, fileName, eTag);
                localEvent.add();
                syncResult.stats.numInserts++;
            }
        } else
            Constants.log.error("Received VCALENDAR with not exactly one VEVENT with UID, but without RECURRENCE-ID; ignoring " + fileName);
    }

}
