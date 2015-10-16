/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */
package at.bitfire.davdroid.resource;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import com.squareup.okhttp.HttpUrl;

import org.xbill.DNS.Lookup;
import org.xbill.DNS.Record;
import org.xbill.DNS.SRVRecord;
import org.xbill.DNS.TXTRecord;
import org.xbill.DNS.TextParseException;
import org.xbill.DNS.Type;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import at.bitfire.dav4android.DavResource;
import at.bitfire.dav4android.UrlUtils;
import at.bitfire.dav4android.exception.DavException;
import at.bitfire.dav4android.exception.HttpException;
import at.bitfire.dav4android.exception.NotFoundException;
import at.bitfire.dav4android.property.CalendarColor;
import at.bitfire.dav4android.property.CalendarDescription;
import at.bitfire.dav4android.property.CalendarHomeSet;
import at.bitfire.dav4android.property.CalendarTimezone;
import at.bitfire.dav4android.property.CurrentUserPrincipal;
import at.bitfire.dav4android.property.CurrentUserPrivilegeSet;
import at.bitfire.dav4android.property.DisplayName;
import at.bitfire.dav4android.property.ResourceType;
import at.bitfire.dav4android.property.SupportedCalendarComponentSet;
import at.bitfire.davdroid.Constants;
import at.bitfire.davdroid.HttpClient;
import lombok.NonNull;

public class DavResourceFinder {
	private final static String TAG = "davdroid.ResourceFinder";
	
	protected Context context;
    protected final HttpClient httpClient;
    protected final ServerInfo serverInfo;

    protected List<ServerInfo.ResourceInfo>
            calendars = new LinkedList<>(),
            taskLists = new LinkedList<>();


    public DavResourceFinder(Context context, ServerInfo serverInfo) {
		this.context = context;
        this.serverInfo = serverInfo;

        httpClient = new HttpClient(context, serverInfo.getUserName(), serverInfo.getPassword(), serverInfo.authPreemptive);
	}

	public void findResources() throws URISyntaxException, IOException, HttpException, DavException {
        URI baseURI = serverInfo.getBaseURI();
        String domain = null;

        HttpUrl principalUrl = null;
        Set<HttpUrl> calendarHomeSets = new HashSet<>();

        if ("http".equals(baseURI.getScheme()) || "https".equals(baseURI.getScheme())) {
            HttpUrl userURL = HttpUrl.get(baseURI);

            /* check whether:
                1. user-given URL is a calendar
                2. user-given URL has a calendar-home-set property (i.e. is a principal URL)
             */
            Constants.log.info("Check whether user-given URL is a calendar collection and/or contains <calendar-home-set> and/or has <current-user-principal>");
            DavResource davBase = new DavResource(httpClient, userURL);
            try {
                davBase.propfind(0,
                        CalendarHomeSet.NAME, SupportedCalendarComponentSet.NAME,
                        ResourceType.NAME, DisplayName.NAME, CalendarColor.NAME, CalendarDescription.NAME, CalendarTimezone.NAME, CurrentUserPrivilegeSet.NAME,
                        CurrentUserPrincipal.NAME
                );
                addIfCalendar(davBase);
            } catch (IOException | HttpException | DavException e) {
                Constants.log.debug("PROPFIND on user-given URL failed", e);
            }

            CalendarHomeSet calendarHomeSet = (CalendarHomeSet) davBase.properties.get(CalendarHomeSet.NAME);
            if (calendarHomeSet != null) {
                Constants.log.info("Found <calendar-home-set> at user-given URL");
                for (String href : calendarHomeSet.hrefs) {
                    HttpUrl url = userURL.resolve(href);
                    if (url != null)
                        calendarHomeSets.add(url);
                }
            }

            /* When home sets haven already been found, skip further searching.
             * Otherwise (no home sets found), treat the user-given URL as "initial context path" for service discovery. */
            if (calendarHomeSets.isEmpty()) {
                Constants.log.info("No <calendar-home-set> set found, looking for <current-user-principal>");

                CurrentUserPrincipal currentUserPrincipal = (CurrentUserPrincipal) davBase.properties.get(CurrentUserPrincipal.NAME);
                if (currentUserPrincipal != null && currentUserPrincipal.href != null)
                    principalUrl = davBase.location.resolve(currentUserPrincipal.href);

                if (principalUrl == null) {
                    Constants.log.info("User-given URL doesn't contain <current-user-principal>, trying /.well-known/caldav");
                    try {
                        principalUrl = getCurrentUserPrincipal(userURL.resolve("/.well-known/caldav"));
                    } catch (IOException|HttpException|DavException e) {
                        Constants.log.debug("PROPFIND on /.well-known/caldav failed", e);
                    }
                }
            }

            // try service discovery with "domain" = user-given host name
            domain = baseURI.getHost();
        } else if ("mailto".equals(baseURI.getScheme())) {
            String mailbox = baseURI.getSchemeSpecificPart();

            // determine service FQDN
            int posAt = mailbox.lastIndexOf("@");
            if (posAt == -1)
                throw new URISyntaxException(mailbox, "Missing @ sign");

            domain = mailbox.substring(posAt + 1);
        }

        if (principalUrl == null && domain != null) {
            Constants.log.info("No principal URL yet, trying SRV/TXT records with domain " + domain);
            principalUrl = discoverPrincipalUrl(domain, "caldavs");
        }

        // principal URL has been found, get calendar-home-set
        if (principalUrl != null) {
            Constants.log.info("Principal URL=" + principalUrl + ", getting <calendar-home-set>");
            try {
                DavResource principal = new DavResource(httpClient, principalUrl);
                principal.propfind(0, CalendarHomeSet.NAME);
                CalendarHomeSet calendarHomeSet = (CalendarHomeSet)principal.properties.get(CalendarHomeSet.NAME);
                if (calendarHomeSet != null)
                    Constants.log.info("Found <calendar-home-set> at principal URL");
                    for (String href : calendarHomeSet.hrefs) {
                        HttpUrl url = principal.location.resolve(href);
                        if (url != null)
                            calendarHomeSets.add(url);
                    }
            } catch (IOException|HttpException|DavException e) {
                Constants.log.debug("PROPFIND on " + principalUrl + " failed", e);
            }
        }

        // now query all home sets
        for (HttpUrl url : calendarHomeSets)
            try {
                Constants.log.info("Listing collections in home set " + url);
                DavResource homeSet = new DavResource(httpClient, url);
                homeSet.propfind(1, SupportedCalendarComponentSet.NAME, ResourceType.NAME, DisplayName.NAME, CurrentUserPrivilegeSet.NAME,
                        CalendarColor.NAME, CalendarDescription.NAME, CalendarTimezone.NAME);

                // home set should not be a calendar, but some servers have only one calendar and it's the home set
                addIfCalendar(homeSet);

                // members of the home set can be calendars, too
                for (DavResource member : homeSet.members)
                    addIfCalendar(member);
            } catch (IOException|HttpException|DavException e) {
                Constants.log.debug("PROPFIND on " + url + " failed", e);
            }

        // TODO CardDAV

        // TODO remove duplicates
        // TODO notify user on errors?

        serverInfo.setCalendars(calendars);
        serverInfo.setTaskLists(taskLists);
    }

    /**
     * If the given DavResource is a #{@link ResourceType#CALENDAR}:
     * <ul>
     *  <li>add it to #{@link #calendars} if it supports VEVENT</li>
     *  <li>add it to #{@link #taskLists} if it supports VTODO</li>
     * </ul>
     * @param dav    DavResource to check
     */
    protected void addIfCalendar(@NonNull DavResource dav) {
        ResourceType resourceType = (ResourceType)dav.properties.get(ResourceType.NAME);
        if (resourceType != null && resourceType.types.contains(ResourceType.CALENDAR)) {
            Constants.log.info("Found calendar collection at " + dav.location);
            boolean supportsEvents = true, supportsTasks = true;
            SupportedCalendarComponentSet supportedCalendarComponentSet = (SupportedCalendarComponentSet)dav.properties.get(SupportedCalendarComponentSet.NAME);
            if (supportedCalendarComponentSet != null) {
                supportsEvents = supportedCalendarComponentSet.supportsEvents;
                supportsTasks = supportedCalendarComponentSet.supportsTasks;
            }
            if (supportsEvents)
                calendars.add(resourceInfo(dav));
            if (supportsTasks)
                taskLists.add(resourceInfo(dav));
        }
    }

    /**
     * Builds a #{@link at.bitfire.davdroid.resource.ServerInfo.ResourceInfo} from a given
     * #{@link DavResource}. Uses the DAV properties current-user-properties, current-user-privilege-set,
     * displayname, calendar-description and calendar-color. Make sure you have queried these
     * properties from the DavResource.
     * @param dav   DavResource to take the resource info from
     * @return      ResourceInfo which represents the DavResource
     */
    protected ServerInfo.ResourceInfo resourceInfo(DavResource dav) {
        boolean readOnly = false;
        CurrentUserPrivilegeSet privilegeSet = (CurrentUserPrivilegeSet)dav.properties.get(CurrentUserPrivilegeSet.NAME);
        if (privilegeSet != null)
            readOnly = !privilegeSet.mayWriteContent;

        String title = null;
        DisplayName displayName = (DisplayName)dav.properties.get(DisplayName.NAME);
        if (displayName != null)
            title = displayName.displayName;
        if (TextUtils.isEmpty(title))
            title = UrlUtils.lastSegment(dav.location);

        String description = null;
        CalendarDescription calendarDescription = (CalendarDescription)dav.properties.get(CalendarDescription.NAME);
        if (calendarDescription != null)
            description = calendarDescription.description;

        Integer color = null;
        CalendarColor calendarColor = (CalendarColor)dav.properties.get(CalendarColor.NAME);
        if (calendarColor != null)
            color = calendarColor.color;

        return new ServerInfo.ResourceInfo(
                ServerInfo.ResourceInfo.Type.CALENDAR,
                readOnly,
                dav.location.toString(),
                title,
                description,
                color
        );
    }

    /**
     * Try to find the principal URL by performing service discovery on a given domain name.
     * @param domain         domain name, e.g. "icloud.com"
     * @param serviceName    service name: "caldavs" or "carddavs"
     * @return principal URL, or null if none found
     */
    protected HttpUrl discoverPrincipalUrl(String domain, String serviceName) {
        String scheme = null;
        String fqdn = null;
        Integer port = null;
        String path = null;

        try {
            final String query = "_" + serviceName + "._tcp." + domain;
            Constants.log.debug("Looking up SRV records for " + query);
            Record[] records = new Lookup(query, Type.SRV).run();
            if (records != null && records.length >= 1) {
                // choose SRV record to use (query may return multiple SRV records)
                SRVRecord srv = selectSRVRecord(records);

                scheme = "https";
                fqdn = srv.getTarget().toString(true);
                port = srv.getPort();
                Constants.log.info("Found " + serviceName + " service: fqdn=" + fqdn + ", port=" + port);

                // look for TXT record too (for initial context path)
                records = new Lookup(domain, Type.TXT).run();
                if (records != null && records.length >= 1) {
                    TXTRecord txt = (TXTRecord)records[0];
                    for (String segment : (String[])txt.getStrings().toArray(new String[0]))
                        if (segment.startsWith("path=")) {
                            path = segment.substring(5);
                            Constants.log.info("Found TXT record; initial context path=" + path);
                            break;
                        }
                }

                if (path == null)      // no path from TXT records, use .well-known
                    path = "/.well-known/caldav";
            }

            if (!TextUtils.isEmpty(scheme) && !TextUtils.isEmpty(fqdn) && port != null && path != null) {
                HttpUrl initialContextPath = new HttpUrl.Builder()
                        .scheme(scheme)
                        .host(fqdn).port(port)
                        .encodedPath(path)
                        .build();

                HttpUrl principal = null;
                try {
                    principal = getCurrentUserPrincipal(initialContextPath);
                } catch(NotFoundException e) {
                    principal = getCurrentUserPrincipal(initialContextPath.resolve("/"));
                }
                return principal;
            }
        } catch (IOException|HttpException|DavException e) {
            Constants.log.debug("Service discovery failed", e);
        }
        return null;
    }

    /**
     * Queries a given URL for current-user-principal
     * @param url   URL to query with PROPFIND (Depth: 0)
     * @return      current-user-principal URL, or null if none
     */
    protected HttpUrl getCurrentUserPrincipal(HttpUrl url) throws IOException, HttpException, DavException {
        DavResource dav = new DavResource(httpClient, url);
        dav.propfind(0, CurrentUserPrincipal.NAME);
        CurrentUserPrincipal currentUserPrincipal = (CurrentUserPrincipal)dav.properties.get(CurrentUserPrincipal.NAME);
        if (currentUserPrincipal != null && currentUserPrincipal.href != null)
            return url.resolve(currentUserPrincipal.href);
        return null;
    }

	/**
	 * Finds the initial service URL from a given base URI (HTTP[S] or mailto URI, user name, password)
	 * @param serverInfo	User-given service information (including base URI, i.e. HTTP[S] URL+user name+password or mailto URI and password)
	 * @param serviceName	Service name ("carddav" or "caldav")
	 * @return				Initial service URL (HTTP/HTTPS), without user credentials
	 * @throws URISyntaxException when the user-given URI is invalid
	 */
	public HttpUrl getInitialContextURL(ServerInfo serverInfo, String serviceName) throws URISyntaxException {
		String	scheme,
				domain;
		int		port = -1;
		String	path = "/";
		
		URI baseURI = serverInfo.getBaseURI();
		if ("mailto".equalsIgnoreCase(baseURI.getScheme())) {
			// mailto URIs
			String mailbox = serverInfo.getBaseURI().getSchemeSpecificPart();

			// determine service FQDN
			int pos = mailbox.lastIndexOf("@");
			if (pos == -1)
				throw new URISyntaxException(mailbox, "Missing @ sign");
			
			scheme = "https";
			domain = mailbox.substring(pos + 1);
			if (domain.isEmpty())
				throw new URISyntaxException(mailbox, "Missing domain name");
		} else {
			// HTTP(S) URLs
			scheme = baseURI.getScheme();
			domain = baseURI.getHost();
			port = baseURI.getPort();
			path = baseURI.getPath();
		}

		// try to determine FQDN and port number using SRV records
		try {
			String name = "_" + serviceName + "._tcp." + domain;
			Constants.log.debug("Looking up SRV records for " + name);
			Record[] records = new Lookup(name, Type.SRV).run();
			if (records != null && records.length >= 1) {
				SRVRecord srv = selectSRVRecord(records);
				
				scheme = "https";
				domain = srv.getTarget().toString(true);
				port = srv.getPort();
				Log.d(TAG, "Found " + serviceName + " service for " + domain + " -> " + domain + ":" + port);

				// SRV record found, look for TXT record too (for initial context path)
				records = new Lookup(name, Type.TXT).run();
				if (records != null && records.length >= 1) {
					TXTRecord txt = (TXTRecord)records[0];
					for (Object o : txt.getStrings().toArray()) {
						String segment = (String)o;
						if (segment.startsWith("path=")) {
							path = segment.substring(5);
                            Constants.log.debug("Found initial context path for " + serviceName + " at " + domain + " -> " + path);
							break;
						}
					}
				}
			}
		} catch (TextParseException e) {
			throw new URISyntaxException(domain, "Invalid domain name");
		}
		
		HttpUrl.Builder builder = new HttpUrl.Builder().scheme(scheme).host(domain);
        if (port != -1)
            builder.port(port);
        if (TextUtils.isEmpty(path))
            path = "/";
        return builder.encodedPath(path).build();
	}


    // helpers
	
	private SRVRecord selectSRVRecord(Record[] records) {
		if (records.length > 1)
			Constants.log.warn("Multiple SRV records not supported yet; using first one");
		return (SRVRecord)records[0];
	}

}
