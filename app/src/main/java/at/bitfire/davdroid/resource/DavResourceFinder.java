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
import java.util.LinkedList;
import java.util.List;

import at.bitfire.dav4android.DavResource;
import at.bitfire.dav4android.HttpClient;
import at.bitfire.dav4android.exception.DavException;
import at.bitfire.dav4android.exception.HttpException;
import at.bitfire.dav4android.property.AddressbookDescription;
import at.bitfire.dav4android.property.AddressbookHomeSet;
import at.bitfire.dav4android.property.CalendarColor;
import at.bitfire.dav4android.property.CalendarDescription;
import at.bitfire.dav4android.property.CalendarHomeSet;
import at.bitfire.dav4android.property.CurrentUserPrincipal;
import at.bitfire.dav4android.property.DisplayName;
import at.bitfire.dav4android.property.ResourceType;
import at.bitfire.davdroid.Constants;
import at.bitfire.davdroid.DAVUtils;

public class DavResourceFinder {
	private final static String TAG = "davdroid.ResourceFinder";
	
	final protected Context context;

	
	public DavResourceFinder(Context context) {
		this.context = context;
	}

	public void findResources(final ServerInfo serverInfo) throws URISyntaxException, IOException, HttpException, DavException {
        final HttpClient httpClient = new HttpClient();
        /*httpClient.setAuthenticator(new Authenticator() {
            @Override
            public Request authenticate(Proxy proxy, Response response) throws IOException {
                String credential = Credentials.basic(serverInfo.getUserName(), serverInfo.getPassword());
                return response.request().newBuilder()
                        .header("Authorization", credential)
                        .build();
            }

            @Override
            public Request authenticateProxy(Proxy proxy, Response response) throws IOException {
                return null;
            }
        });*/

        // CardDAV
        Constants.log.info("*** CardDAV resource detection ***");
		HttpUrl principalUrl = getCurrentUserPrincipal(httpClient, serverInfo, "carddav");

        DavResource principal = new DavResource(httpClient, principalUrl);
        principal.propfind(0, AddressbookHomeSet.NAME);
        AddressbookHomeSet addrHomeSet = (AddressbookHomeSet)principal.properties.get(AddressbookHomeSet.NAME);
        if (addrHomeSet != null && !addrHomeSet.hrefs.isEmpty()) {
            Constants.log.info("Found addressbook home set(s): " + addrHomeSet);
            serverInfo.setCardDAV(true);

            // enumerate address books
            List<ServerInfo.ResourceInfo> addressBooks = new LinkedList<>();
            for (String href : addrHomeSet.hrefs) {
                DavResource homeSet = new DavResource(httpClient, principalUrl.resolve(href));
                homeSet.propfind(1, ResourceType.NAME, DisplayName.NAME, AddressbookDescription.NAME);
                for (DavResource member : homeSet.members) {
                    ResourceType type = (ResourceType)member.properties.get(ResourceType.NAME);
                    if (type != null && type.types.contains(ResourceType.ADDRESSBOOK)) {
                        Constants.log.info("Found address book: " + member.location);

                        DisplayName displayName = (DisplayName)member.properties.get(DisplayName.NAME);
                        AddressbookDescription description = (AddressbookDescription)member.properties.get(AddressbookDescription.NAME);

                        // TODO read-only

                        addressBooks.add(new ServerInfo.ResourceInfo(
                                ServerInfo.ResourceInfo.Type.ADDRESS_BOOK,
                                false,
                                member.location.toString(),
                                displayName != null ? displayName.displayName : null,
                                description != null ? description.description : null,
                                null
                        ));
                    }
                }
            }
            serverInfo.setAddressBooks(addressBooks);
        }

        // CalDAV
        Constants.log.info("*** CalDAV resource detection ***");
        principalUrl = getCurrentUserPrincipal(httpClient, serverInfo, "caldav");

        principal = new DavResource(httpClient, principalUrl);
        principal.propfind(0, CalendarHomeSet.NAME);
        CalendarHomeSet calHomeSet = (CalendarHomeSet)principal.properties.get(CalendarHomeSet.NAME);
        if (calHomeSet != null && !calHomeSet.hrefs.isEmpty()) {
            Constants.log.info("Found calendar home set(s): " + calHomeSet);
            serverInfo.setCalDAV(true);

            // enumerate address books
            List<ServerInfo.ResourceInfo> calendars = new LinkedList<>();
            for (String href : calHomeSet.hrefs) {
                DavResource homeSet = new DavResource(httpClient, principalUrl.resolve(href));
                homeSet.propfind(1, ResourceType.NAME, DisplayName.NAME, CalendarDescription.NAME, CalendarColor.NAME);
                for (DavResource member : homeSet.members) {
                    ResourceType type = (ResourceType)member.properties.get(ResourceType.NAME);
                    if (type != null && type.types.contains(ResourceType.CALENDAR)) {
                        Constants.log.info("Found calendar: " + member.location);

                        DisplayName displayName = (DisplayName)member.properties.get(DisplayName.NAME);
                        CalendarDescription description = (CalendarDescription)member.properties.get(CalendarDescription.NAME);
                        CalendarColor color = (CalendarColor)member.properties.get(CalendarColor.NAME);

                        // TODO read-only, time-zone, supported components

                        calendars.add(new ServerInfo.ResourceInfo(
                                ServerInfo.ResourceInfo.Type.ADDRESS_BOOK,
                                false,
                                member.location.toString(),
                                displayName != null ? displayName.displayName : null,
                                description != null ? description.description : null,
                                color != null ? DAVUtils.CalDAVtoARGBColor(color.color) : null
                        ));
                    }
                }
            }
            serverInfo.setCalendars(calendars);
        }

		/*if (!serverInfo.isCalDAV() && !serverInfo.isCardDAV())
			throw new DavIncapableException(context.getString(R.string.setup_neither_caldav_nor_carddav));*/
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
			String name = "_" + serviceName + "s._tcp." + domain;
			Constants.log.debug("Looking up SRV records for " + name);
			Record[] records = new Lookup(name, Type.SRV).run();
			if (records != null && records.length >= 1) {
				SRVRecord srv = selectSRVRecord(records);
				
				scheme = "https";
				domain = srv.getTarget().toString(true);
				port = srv.getPort();
				Log.d(TAG, "Found " + serviceName + "s service for " + domain + " -> " + domain + ":" + port);

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
	
	
	/**
	 * Detects the current-user-principal for a given WebDavResource. At first, /.well-known/ is tried. Only
	 * if no current-user-principal can be detected for the .well-known location, the given location of the resource
	 * is tried.
	 * @param serverInfo	Location that will be queried
	 * @param serviceName	Well-known service name ("carddav", "caldav")
	 * @return	            WebDavResource of current-user-principal for the given service, or initial context URL if it can't be found
	 * 
	 * TODO: If a TXT record is given, always use it instead of trying .well-known first
	 */
	HttpUrl getCurrentUserPrincipal(HttpClient httpClient, ServerInfo serverInfo, String serviceName) throws URISyntaxException {
        HttpUrl initialURL = getInitialContextURL(serverInfo, serviceName);

        if (initialURL != null) {
            Constants.log.info("Looking up principal URL for service " + serviceName + "; initial context: " + initialURL);

            // look for well-known service (RFC 5785)
            try {
                DavResource wellKnown = new DavResource(httpClient, initialURL.resolve("/.well-known/" + serviceName));
                wellKnown.propfind(0, CurrentUserPrincipal.NAME);

                CurrentUserPrincipal principal = (CurrentUserPrincipal)wellKnown.properties.get(CurrentUserPrincipal.NAME);
                if (principal != null) {
                    HttpUrl url = wellKnown.location.resolve(principal.href);
                    Constants.log.info("Found principal URL from well-known URL: " + url);
                    return url;
                }
            } catch (IOException e) {
                Constants.log.warn("Well-known " + serviceName + " service detection failed with I/O error", e);
            } catch (HttpException e) {
                Constants.log.warn("Well-known " + serviceName + " service detection failed with HTTP error", e);
            } catch (DavException e) {
                Constants.log.warn("Well-known " + serviceName + " service detection failed with DAV error", e);
            }
        }

        // fall back to user-given initial context path
        Log.d(TAG, "Well-known service detection failed, trying initial context path " + initialURL);
        try {
            DavResource base = new DavResource(httpClient, initialURL);
            base.propfind(0, CurrentUserPrincipal.NAME);
            CurrentUserPrincipal principal = (CurrentUserPrincipal)base.properties.get(CurrentUserPrincipal.NAME);
            if (principal != null) {
                HttpUrl url = base.location.resolve(principal.href);
                Constants.log.info("Found principal URL from initial context URL: " + url);
                return url;
            }
        } catch (IOException e) {
            Constants.log.warn("Well-known " + serviceName + " service detection failed with I/O error", e);
        } catch (HttpException e) {
            Log.e(TAG, "HTTP error when querying principal", e);
        } catch (DavException e) {
            Log.e(TAG, "DAV error when querying principal", e);
        }

        Log.i(TAG, "Couldn't find current-user-principal for service " + serviceName + ". Assuming principal path is initial context path!");
		return initialURL;
	}

	
	SRVRecord selectSRVRecord(Record[] records) {
		if (records.length > 1)
			Log.w(TAG, "Multiple SRV records not supported yet; using first one");
		return (SRVRecord)records[0];
	}

}
