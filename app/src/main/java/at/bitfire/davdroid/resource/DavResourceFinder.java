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

import okhttp3.HttpUrl;

import org.slf4j.Logger;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.Record;
import org.xbill.DNS.SRVRecord;
import org.xbill.DNS.TXTRecord;
import org.xbill.DNS.Type;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import at.bitfire.dav4android.DavResource;
import at.bitfire.dav4android.UrlUtils;
import at.bitfire.dav4android.exception.DavException;
import at.bitfire.dav4android.exception.HttpException;
import at.bitfire.dav4android.exception.NotFoundException;
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
import at.bitfire.davdroid.HttpClient;
import at.bitfire.davdroid.log.StringLogger;
import at.bitfire.davdroid.ui.setup.LoginCredentialsFragment;
import lombok.Data;
import lombok.NonNull;
import lombok.ToString;
import okhttp3.OkHttpClient;

public class DavResourceFinder {
    protected enum Service {
        CALDAV("caldav"),
        CARDDAV("carddav");

        final String name;
        Service(String name) { this.name = name;}
        @Override public String toString() { return name; }
    };

    protected final Context context;
    protected final LoginCredentialsFragment.LoginCredentials credentials;

    protected final Logger log = new StringLogger("DavResourceFinder", true);
    protected OkHttpClient httpClient;

    protected HttpUrl carddavPrincipal, caldavPrincipal;
    protected Map<HttpUrl, ServerConfiguration.Collection>
            addressBooks = new HashMap<>(),
            calendars = new HashMap<>();


    public DavResourceFinder(Context context, LoginCredentialsFragment.LoginCredentials credentials) {
		this.context = context;
        this.credentials = credentials;

        httpClient = HttpClient.create(context);
        httpClient = HttpClient.addLogger(httpClient, log);
        httpClient = HttpClient.addAuthentication(httpClient, credentials.getUserName(), credentials.getPassword(), credentials.isAuthPreemptive());
	}


    public ServerConfiguration findInitialConfiguration() {
        addressBooks.clear();
        findInitialConfiguration(Service.CARDDAV);

        calendars.clear();
        findInitialConfiguration(Service.CALDAV);

        return new ServerConfiguration(
                carddavPrincipal, addressBooks.values().toArray(new ServerConfiguration.Collection[0]),
                caldavPrincipal, calendars.values().toArray(new ServerConfiguration.Collection[0]),
                log.toString()
        );
    }

    protected void findInitialConfiguration(Service service) {
        // user-given base URI (mailto or URL)
        URI baseURI = credentials.getUri();

        // domain for service discovery
        String domain = null;

        HttpUrl principal = null;

        // Step 1a (only when user-given URI is URL):
        //         * Check whether URL represents a calendar/address-book collection itself,
        //         * and/or whether it has a current-user-principal,
        //         * or whether it represents a principal itself.
        if ("http".equalsIgnoreCase(baseURI.getScheme()) || "https".equalsIgnoreCase(baseURI.getScheme())) {
            HttpUrl baseURL = HttpUrl.get(baseURI);

            // remember domain for service discovery (if required)
            // try service discovery only for https:// URLs because only secure service discovery is implemented
            if ("https".equalsIgnoreCase(baseURL.scheme()))
                domain = baseURI.getHost();

            log.info("Checking user-given URL: " + baseURL.toString());
            try {
                DavResource davBase = new DavResource(log, httpClient, baseURL);

                if (service == Service.CARDDAV) {
                    davBase.propfind(0,
                            AddressbookHomeSet.NAME,
                            ResourceType.NAME, DisplayName.NAME, AddressbookDescription.NAME, CurrentUserPrivilegeSet.NAME,
                            CurrentUserPrincipal.NAME
                    );
                    addIfAddressBook(davBase);
                } else if (service == Service.CALDAV) {
                    davBase.propfind(0,
                            CalendarHomeSet.NAME, SupportedCalendarComponentSet.NAME,
                            ResourceType.NAME, DisplayName.NAME, CalendarColor.NAME, CalendarDescription.NAME, CalendarTimezone.NAME, CurrentUserPrivilegeSet.NAME,
                            CurrentUserPrincipal.NAME
                    );
                    addIfCalendar(davBase);
                }

                // check for current-user-principal
                CurrentUserPrincipal currentUserPrincipal = (CurrentUserPrincipal)davBase.properties.get(CurrentUserPrincipal.NAME);
                if (currentUserPrincipal != null && currentUserPrincipal.href != null)
                    principal = davBase.location.resolve(currentUserPrincipal.href);

                // check for resourcetype = principal
                if (principal == null) {
                    ResourceType resourceType = (ResourceType)davBase.properties.get(ResourceType.NAME);
                    if (resourceType.types.contains(ResourceType.PRINCIPAL))
                        principal = davBase.location;
                }

                // If a principal has been detected successfully, ensure that it provides the required service.
                if (principal != null && !providesService(principal, service))
                    principal = null;

            } catch (IOException|HttpException|DavException e) {
                log.debug("PROPFIND on user-given URL failed", e);
            }

            // Step 1b: Try well-known URL, too
            if (principal == null)
                try {
                    principal = getCurrentUserPrincipal(baseURL.resolve("/.well-known/" + service.name), service);
                } catch (IOException|HttpException|DavException e) {
                    log.debug("Well-known URL detection failed", e);
                }

        } else if ("mailto".equalsIgnoreCase(baseURI.getScheme())) {
            String mailbox = baseURI.getSchemeSpecificPart();

            int posAt = mailbox.lastIndexOf("@");
            if (posAt != -1)
                domain = mailbox.substring(posAt + 1);
        }

        // Step 2: If user-given URL didn't reveal a principal, search for it: SERVICE DISCOVERY
        if (principal == null && domain != null) {
            log.info("No principal found at user-given URL, trying to discover");
            try {
                principal = discoverPrincipalUrl(domain, service);
            } catch (IOException|HttpException|DavException e) {
                log.debug(service.name + " service discovery failed", e);
            }
        }

        if (service == Service.CALDAV)
            caldavPrincipal = principal;
        else if (service == Service.CARDDAV)
            carddavPrincipal = principal;
    }

    protected void addIfAddressBook(@NonNull DavResource dav) {
        ResourceType resourceType = (ResourceType)dav.properties.get(ResourceType.NAME);
        if (resourceType != null && resourceType.types.contains(ResourceType.ADDRESSBOOK)) {
            dav.location = UrlUtils.withTrailingSlash(dav.location);
            log.info("Found address book at " + dav.location);

            addressBooks.put(dav.location, collectionInfo(dav, ServerConfiguration.Collection.Type.ADDRESS_BOOK));
        }
    }

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
            if (supportsEvents || supportsTasks) {
                ServerConfiguration.Collection info = collectionInfo(dav, ServerConfiguration.Collection.Type.CALENDAR);
                info.supportsEvents = supportsEvents;
                info.supportsTasks = supportsTasks;
                calendars.put(dav.location, info);
            }
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
    protected ServerConfiguration.Collection collectionInfo(DavResource dav, ServerConfiguration.Collection.Type type) {
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
        if (type == ServerConfiguration.Collection.Type.ADDRESS_BOOK) {
            AddressbookDescription addressbookDescription = (AddressbookDescription)dav.properties.get(AddressbookDescription.NAME);
            if (addressbookDescription != null)
                description = addressbookDescription.description;
        } else if (type == ServerConfiguration.Collection.Type.CALENDAR) {
            CalendarDescription calendarDescription = (CalendarDescription)dav.properties.get(CalendarDescription.NAME);
            if (calendarDescription != null)
                description = calendarDescription.description;

            CalendarColor calendarColor = (CalendarColor)dav.properties.get(CalendarColor.NAME);
            if (calendarColor != null)
                color = calendarColor.color;
        }

        ServerConfiguration.Collection collection = new ServerConfiguration.Collection(
                type,
                readOnly,
                UrlUtils.withTrailingSlash(dav.location).toString(),
                title,
                description,
                color
        );

        return collection;
    }


    boolean providesService(HttpUrl url, Service service) {
        DavResource davPrincipal = new DavResource(log, httpClient, url);
        try {
            davPrincipal.options();

            if ((service == Service.CARDDAV && davPrincipal.capabilities.contains("addressbook")) ||
                (service == Service.CALDAV && davPrincipal.capabilities.contains("calendar-access")))
                return true;

        } catch (IOException|HttpException|DavException e) {
            log.error("Couldn't detect services on {}", url);
        }
        return false;
    }


    /**
     * Try to find the principal URL by performing service discovery on a given domain name.
     * Only secure services (caldavs, carddavs) will be discovered!
     * @param domain         domain name, e.g. "icloud.com"
     * @param service        service to discover (CALDAV or CARDDAV)
     * @return principal URL, or null if none found
     */
    protected HttpUrl discoverPrincipalUrl(String domain, Service service) throws IOException, HttpException, DavException {
        String scheme = null;
        String fqdn = null;
        Integer port = 443;
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
            log.info("Found " + service + " service at https://" + fqdn + ":" + port);

        } else {
            // no SRV records, try domain name as FQDN
            log.info("Didn't find " + service + " service, trying at https://" + domain + ":" + port);

            scheme = "https";
            fqdn = domain;
        }

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

        for (String path : paths)
            try {
                if (!TextUtils.isEmpty(scheme) && !TextUtils.isEmpty(fqdn) && paths != null) {
                    HttpUrl initialContextPath = new HttpUrl.Builder()
                            .scheme(scheme)
                            .host(fqdn).port(port)
                            .encodedPath(path)
                            .build();

                    log.info("Trying to determine principal from initial context path=" + initialContextPath);
                    HttpUrl principal = getCurrentUserPrincipal(initialContextPath, service);

                    if (principal != null)
                        return principal;
                }
            } catch(NotFoundException e) {
                log.warn("No resource found", e);
            }
        return null;
    }

    /**
     * Queries a given URL for current-user-principal
     * @param url       URL to query with PROPFIND (Depth: 0)
     * @param service   required service (may be null, in which case no service check is done)
     * @return          current-user-principal URL that provides required service, or null if none
     */
    protected HttpUrl getCurrentUserPrincipal(HttpUrl url, Service service) throws IOException, HttpException, DavException {
        DavResource dav = new DavResource(log, httpClient, url);
        dav.propfind(0, CurrentUserPrincipal.NAME);
        CurrentUserPrincipal currentUserPrincipal = (CurrentUserPrincipal) dav.properties.get(CurrentUserPrincipal.NAME);
        if (currentUserPrincipal != null && currentUserPrincipal.href != null) {
            HttpUrl principal = dav.location.resolve(currentUserPrincipal.href);
            if (principal != null) {
                log.info("Found current-user-principal: " + principal);

                // service check
                if (service != null && !providesService(principal, service)) {
                    log.info("{} doesn't provide required {} service, dismissing", principal, service);
                    principal = null;
                }

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


    // data classes

    @Data
    @ToString(exclude="logs")
    public static class ServerConfiguration {
        final public HttpUrl cardDavPrincipal;
        final public Collection[] addressBooks;

        final public HttpUrl calDavPrincipal;
        final public Collection[] calendars;

        final String logs;

        @Data
        @ToString
        public static class Collection {
            public enum Type {
                ADDRESS_BOOK,
                CALENDAR
            }

            final Type type;
            final boolean readOnly;

            final String url,       // absolute URL of resource
                  title,
                  description;
            final Integer color;

            /**
             * full VTIMEZONE definition (not the TZ ID)
             */
            boolean supportsEvents, supportsTasks;
            String timezone;
        }
    }

}
