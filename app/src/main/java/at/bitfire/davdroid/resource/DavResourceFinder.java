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

import com.squareup.okhttp.HttpUrl;

import org.slf4j.Logger;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.Record;
import org.xbill.DNS.SRVRecord;
import org.xbill.DNS.TXTRecord;
import org.xbill.DNS.Type;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import at.bitfire.dav4android.DavResource;
import at.bitfire.dav4android.UrlUtils;
import at.bitfire.dav4android.exception.DavException;
import at.bitfire.dav4android.exception.HttpException;
import at.bitfire.dav4android.property.AddressbookDescription;
import at.bitfire.dav4android.property.AddressbookHomeSet;
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
    protected enum Service {
        CALDAV("caldav"),
        CARDDAV("carddav");

        final String name;
        Service(String name) { this.name = name;}
        @Override public String toString() { return name; }
    };

    protected final Logger log;
	protected final Context context;
    protected final HttpClient httpClient;
    protected final ServerInfo serverInfo;

    protected Map<HttpUrl, ServerInfo.ResourceInfo>
            addressbooks = new HashMap<>(),
            calendars = new HashMap<>(),
            taskLists = new HashMap<>();


    public DavResourceFinder(Logger log, Context context, ServerInfo serverInfo) {
        this.log = log;
		this.context = context;
        this.serverInfo = serverInfo;

        httpClient = new HttpClient(log, context, serverInfo.getUserName(), serverInfo.getPassword(), serverInfo.authPreemptive);
	}

	public void findResources() {
        try {
            findResources(Service.CARDDAV);
            findResources(Service.CALDAV);
        } catch(URISyntaxException e) {
            log.warn("Invalid user-given URI", e);
        }
    }

    public void findResources(Service service) throws URISyntaxException {
        URI baseURI = serverInfo.getBaseURI();
        String domain = null;

        HttpUrl principalUrl = null;
        Set<HttpUrl> homeSets = new HashSet<>();

        if (service == Service.CALDAV) {
            calendars.clear();
            taskLists.clear();
        } else if (service == Service.CARDDAV)
            addressbooks.clear();

        log.info("*** STARTING COLLECTION DISCOVERY FOR SERVICE " + service.name.toUpperCase(Locale.US) + "***");
        if ("http".equals(baseURI.getScheme()) || "https".equals(baseURI.getScheme())) {
            HttpUrl userURL = HttpUrl.get(baseURI);

            /* check whether:
                1. user-given URL is a calendar
                2. user-given URL has a calendar-home-set property (i.e. is a principal URL)
             */
            log.info("Check whether user-given URL is a calendar collection and/or contains <calendar-home-set> and/or has <current-user-principal>");
            DavResource davBase = new DavResource(log, httpClient, userURL);
            try {
                if (service == Service.CALDAV) {
                    davBase.propfind(0,
                            CalendarHomeSet.NAME, SupportedCalendarComponentSet.NAME,
                            ResourceType.NAME, DisplayName.NAME, CalendarColor.NAME, CalendarDescription.NAME, CalendarTimezone.NAME, CurrentUserPrivilegeSet.NAME,
                            CurrentUserPrincipal.NAME
                    );
                    addIfCalendar(davBase);
                } else if (service == Service.CARDDAV) {
                    davBase.propfind(0,
                            AddressbookHomeSet.NAME,
                            ResourceType.NAME, DisplayName.NAME, AddressbookDescription.NAME, CurrentUserPrivilegeSet.NAME,
                            CurrentUserPrincipal.NAME
                    );
                    addIfAddressBook(davBase);
                }
            } catch (IOException|HttpException|DavException e) {
                log.debug("PROPFIND on user-given URL failed", e);
            }

            if (service == Service.CALDAV) {
                CalendarHomeSet calendarHomeSet = (CalendarHomeSet)davBase.properties.get(CalendarHomeSet.NAME);
                if (calendarHomeSet != null) {
                    log.info("Found <calendar-home-set> at user-given URL");
                    for (String href : calendarHomeSet.hrefs) {
                        HttpUrl url = userURL.resolve(href);
                        if (url != null)
                            homeSets.add(url);
                    }
                }
            } else if (service == Service.CARDDAV) {
                AddressbookHomeSet addressbookHomeSet = (AddressbookHomeSet) davBase.properties.get(AddressbookHomeSet.NAME);
                if (addressbookHomeSet != null) {
                    log.info("Found <addressbook-home-set> at user-given URL");
                    for (String href : addressbookHomeSet.hrefs) {
                        HttpUrl url = userURL.resolve(href);
                        if (url != null)
                            homeSets.add(url);
                    }
                }
            }

            /* When home sets haven already been found, skip further searching.
             * Otherwise (no home sets found), treat the user-given URL as "initial context path" for service discovery.
             *
             * Keep in mind that the CalDAV principal URL must not be the CardDAV principal URL! */
            if (homeSets.isEmpty())
                try {
                    log.info("No home sets found, looking for <current-user-principal>");

                    davBase.options();
                    if ((service == Service.CALDAV && davBase.capabilities.contains("calendar-access")) ||
                            (service == Service.CARDDAV && davBase.capabilities.contains("addressbook"))) {
                        CurrentUserPrincipal currentUserPrincipal = (CurrentUserPrincipal)davBase.properties.get(CurrentUserPrincipal.NAME);
                        if (currentUserPrincipal != null && currentUserPrincipal.href != null)
                            principalUrl = davBase.location.resolve(currentUserPrincipal.href);
                    }
                } catch(IOException|HttpException|DavException e) {
                    log.debug("Couldn't find <current-user-principal> at user-given URL", e);
                }

            if (principalUrl == null)
                try {
                    log.info("User-given URL doesn't contain <current-user-principal>, trying /.well-known/" + service.name);
                    principalUrl = getCurrentUserPrincipal(userURL.resolve("/.well-known/" + service.name));
                } catch(IOException|HttpException|DavException e) {
                    log.debug("Couldn't determine <current-user-principal> from well-known " + service + " path", e);
                }

            if (principalUrl == null)
                // still no principal URL, try service discovery with "domain" = user-given host name
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
            log.info("No principal URL yet, trying SRV/TXT records with domain " + domain);
            try {
                principalUrl = discoverPrincipalUrl(domain, service);
            } catch (IOException|HttpException|DavException e) {
                log.info("Couldn't find principal URL using service discovery");
            }
        }

        // principal URL has been found, get addressbook-home-set/calendar-home-set
        if (principalUrl != null) {
            log.info("Principal URL=" + principalUrl + ", getting <calendar-home-set>");
            try {
                DavResource principal = new DavResource(log, httpClient, principalUrl);

                if (service == Service.CALDAV) {
                    principal.propfind(0, CalendarHomeSet.NAME);
                    CalendarHomeSet calendarHomeSet = (CalendarHomeSet) principal.properties.get(CalendarHomeSet.NAME);
                    if (calendarHomeSet != null) {
                        log.info("Found <calendar-home-set> at principal URL");
                        for (String href : calendarHomeSet.hrefs) {
                            HttpUrl url = principal.location.resolve(href);
                            if (url != null)
                                homeSets.add(url);
                        }
                    }
                } else if (service == Service.CARDDAV) {
                    principal.propfind(0, AddressbookHomeSet.NAME);
                    AddressbookHomeSet addressbookHomeSet = (AddressbookHomeSet) principal.properties.get(AddressbookHomeSet.NAME);
                    if (addressbookHomeSet != null) {
                        log.info("Found <addressbook-home-set> at principal URL");
                        for (String href : addressbookHomeSet.hrefs) {
                            HttpUrl url = principal.location.resolve(href);
                            if (url != null)
                                homeSets.add(url);
                        }
                    }
                }

            } catch (IOException|HttpException|DavException e) {
                log.debug("PROPFIND on " + principalUrl + " failed", e);
            }
        }

        // now query all home sets
        for (HttpUrl url : homeSets)
            if (service == Service.CALDAV)
                try {
                    log.info("Listing calendar collections in home set " + url);
                    DavResource homeSet = new DavResource(log, httpClient, url);
                    homeSet.propfind(1, SupportedCalendarComponentSet.NAME, ResourceType.NAME, DisplayName.NAME, CurrentUserPrivilegeSet.NAME,
                            CalendarColor.NAME, CalendarDescription.NAME, CalendarTimezone.NAME);

                    // home set should not be a calendar, but some servers have only one calendar and it's the home set
                    addIfCalendar(homeSet);

                    // members of the home set can be calendars, too
                    for (DavResource member : homeSet.members)
                        addIfCalendar(member);
                } catch (IOException | HttpException | DavException e) {
                    log.debug("PROPFIND on " + url + " failed", e);
                }
            else if (service == Service.CARDDAV)
                try {
                    log.info("Listing address books in home set " + url);
                    DavResource homeSet = new DavResource(log, httpClient, url);
                    homeSet.propfind(1, ResourceType.NAME, DisplayName.NAME, CurrentUserPrivilegeSet.NAME, AddressbookDescription.NAME);

                    // home set should not be an address book, but some servers have only one address book and it's the home set
                    addIfAddressBook(homeSet);

                    // members of the home set can be calendars, too
                    for (DavResource member : homeSet.members)
                        addIfAddressBook(member);
                } catch (IOException | HttpException | DavException e) {
                    log.debug("PROPFIND on " + url + " failed", e);
                }

        if (service == Service.CALDAV) {
            serverInfo.setCalendars(calendars.values().toArray(new ServerInfo.ResourceInfo[calendars.size()]));
            serverInfo.setTaskLists(taskLists.values().toArray(new ServerInfo.ResourceInfo[taskLists.size()]));
        } else if (service == Service.CARDDAV)
            serverInfo.setAddressBooks(addressbooks.values().toArray(new ServerInfo.ResourceInfo[addressbooks.size()]));
    }

    /**
         * If the given DavResource is a #{@link ResourceType#ADDRESSBOOK}, add it to #{@link #addressbooks}.
         * @param dav    DavResource to check
         */
    protected void addIfAddressBook(@NonNull DavResource dav) {
        ResourceType resourceType = (ResourceType)dav.properties.get(ResourceType.NAME);
        if (resourceType != null && resourceType.types.contains(ResourceType.ADDRESSBOOK)) {
            dav.location = UrlUtils.withTrailingSlash(dav.location);
            log.info("Found address book at " + dav.location);

            addressbooks.put(dav.location, resourceInfo(dav, ServerInfo.ResourceInfo.Type.ADDRESS_BOOK));
        }
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
            dav.location = UrlUtils.withTrailingSlash(dav.location);
            log.info("Found calendar collection at " + dav.location);

            boolean supportsEvents = true, supportsTasks = true;
            SupportedCalendarComponentSet supportedCalendarComponentSet = (SupportedCalendarComponentSet)dav.properties.get(SupportedCalendarComponentSet.NAME);
            if (supportedCalendarComponentSet != null) {
                supportsEvents = supportedCalendarComponentSet.supportsEvents;
                supportsTasks = supportedCalendarComponentSet.supportsTasks;
            }
            if (supportsEvents)
                calendars.put(dav.location, resourceInfo(dav, ServerInfo.ResourceInfo.Type.CALENDAR));
            if (supportsTasks)
                taskLists.put(dav.location, resourceInfo(dav, ServerInfo.ResourceInfo.Type.CALENDAR));
        }
    }

    /**
     * Builds a #{@link at.bitfire.davdroid.resource.ServerInfo.ResourceInfo} from a given
     * #{@link DavResource}. Uses these DAV properties:
     * <ul>
     *     <li>calendars: current-user-properties, current-user-privilege-set, displayname, calendar-description, calendar-color</li>
     *     <li>address books: current-user-properties, current-user-privilege-set, displayname, addressbook-description</li>
     * </ul>. Make sure you have queried these properties from the DavResource.
     * @param dav   DavResource to take the resource info from
     * @param type  must be ADDRESS_BOOK or CALENDAR
     * @return      ResourceInfo which represents the DavResource
     */
    protected ServerInfo.ResourceInfo resourceInfo(DavResource dav, ServerInfo.ResourceInfo.Type type) {
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
        Integer color = null;
        if (type == ServerInfo.ResourceInfo.Type.ADDRESS_BOOK) {
            AddressbookDescription addressbookDescription = (AddressbookDescription)dav.properties.get(AddressbookDescription.NAME);
            if (addressbookDescription != null)
                description = addressbookDescription.description;
        } else if (type == ServerInfo.ResourceInfo.Type.CALENDAR) {
            CalendarDescription calendarDescription = (CalendarDescription)dav.properties.get(CalendarDescription.NAME);
            if (calendarDescription != null)
                description = calendarDescription.description;

            CalendarColor calendarColor = (CalendarColor)dav.properties.get(CalendarColor.NAME);
            if (calendarColor != null)
            color = calendarColor.color;
        }

        return new ServerInfo.ResourceInfo(
                type,
                readOnly,
                UrlUtils.withTrailingSlash(dav.location).toString(),
                title,
                description,
                color
        );
    }

    /**
     * Try to find the principal URL by performing service discovery on a given domain name.
     * @param domain         domain name, e.g. "icloud.com"
     * @param service        service to discover (CALDAV or CARDDAV)
     * @return principal URL, or null if none found
     */
    protected HttpUrl discoverPrincipalUrl(String domain, Service service) throws IOException, HttpException, DavException {
        String scheme = null;
        String fqdn = null;
        Integer port = null;
        List<String> paths = new LinkedList<>();     // there may be multiple paths to try

        final String query = "_" + service.name + "s._tcp." + domain;
        log.debug("Looking up SRV records for " + query);
        Record[] records = new Lookup(query, Type.SRV).run();
        if (records != null && records.length >= 1) {
            // choose SRV record to use (query may return multiple SRV records)
            SRVRecord srv = selectSRVRecord(records);

            scheme = "https";
            fqdn = srv.getTarget().toString(true);
            port = srv.getPort();
            log.info("Found " + service + " service: fqdn=" + fqdn + ", port=" + port);

            // look for TXT record too (for initial context path)
            records = new Lookup(query, Type.TXT).run();
            if (records != null)
                for (Record record : records)
                    if (record instanceof TXTRecord)
                        for (String segment : (List<String>) ((TXTRecord) record).getStrings())
                            if (segment.startsWith("path=")) {
                                paths.add(segment.substring(5));
                                log.info("Found TXT record; initial context path=" + paths);
                                break;
                            }

            // if there's TXT record and if it it's wrong, try well-known
            paths.add("/.well-known/" + service.name);
            // if this fails, too, try "/"
            paths.add("/");
        }

        for (String path : paths) {
            if (!TextUtils.isEmpty(scheme) && !TextUtils.isEmpty(fqdn) && port != null && paths != null) {
                HttpUrl initialContextPath = new HttpUrl.Builder()
                        .scheme(scheme)
                        .host(fqdn).port(port)
                        .encodedPath(path)
                        .build();

                log.info("Trying to determine principal from initial context path=" + initialContextPath);
                HttpUrl principal = getCurrentUserPrincipal(initialContextPath);
                if (principal != null)
                    return principal;
            }
        }
        return null;
    }

    /**
     * Queries a given URL for current-user-principal
     * @param url   URL to query with PROPFIND (Depth: 0)
     * @return      current-user-principal URL, or null if none
     */
    protected HttpUrl getCurrentUserPrincipal(HttpUrl url) throws IOException, HttpException, DavException {
        DavResource dav = new DavResource(log, httpClient, url);
        dav.propfind(0, CurrentUserPrincipal.NAME);
        CurrentUserPrincipal currentUserPrincipal = (CurrentUserPrincipal) dav.properties.get(CurrentUserPrincipal.NAME);
        if (currentUserPrincipal != null && currentUserPrincipal.href != null) {
            HttpUrl principal = url.resolve(currentUserPrincipal.href);
            if (principal != null) {
                log.info("Found current-user-principal: " + principal);
                return principal;
            }
        }
        return null;
    }


    // helpers

	private SRVRecord selectSRVRecord(Record[] records) {
		if (records.length > 1)
			log.warn("Multiple SRV records not supported yet; using first one");
		return (SRVRecord)records[0];
	}

}
