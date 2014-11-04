package at.bitfire.davdroid.resource;

import java.io.Closeable;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;

import org.xbill.DNS.Lookup;
import org.xbill.DNS.Record;
import org.xbill.DNS.SRVRecord;
import org.xbill.DNS.TXTRecord;
import org.xbill.DNS.TextParseException;
import org.xbill.DNS.Type;

import android.content.Context;
import android.util.Log;
import at.bitfire.davdroid.R;
import at.bitfire.davdroid.webdav.DavException;
import at.bitfire.davdroid.webdav.DavHttpClient;
import at.bitfire.davdroid.webdav.DavIncapableException;
import at.bitfire.davdroid.webdav.HttpPropfind.Mode;
import at.bitfire.davdroid.webdav.NotAuthorizedException;
import at.bitfire.davdroid.webdav.WebDavResource;
import ch.boye.httpclientandroidlib.HttpException;
import ch.boye.httpclientandroidlib.impl.client.CloseableHttpClient;
import ezvcard.VCardVersion;

public class DavResourceFinder implements Closeable {
	private final static String TAG = "davdroid.DavResourceFinder";
	
	protected Context context;
	protected CloseableHttpClient httpClient;
	
	
	public DavResourceFinder(Context context) {
		this.context = context;
		
		// disable compression and enable network logging for debugging purposes 
		httpClient = DavHttpClient.create(true, true);
	}

	@Override
	public void close() throws IOException {
		httpClient.close();
	}
	
	
	public void findResources(ServerInfo serverInfo) throws URISyntaxException, DavException, HttpException, IOException {
		// CardDAV
		WebDavResource principal = getCurrentUserPrincipal(serverInfo, "carddav");
		if (principal != null) {
			serverInfo.setCardDAV(true);
		
			principal.propfind(Mode.HOME_SETS);
			String pathAddressBooks = principal.getAddressbookHomeSet();
			if (pathAddressBooks != null) {
				Log.i(TAG, "Found address book home set: " + pathAddressBooks);
			
				WebDavResource homeSetAddressBooks = new WebDavResource(principal, pathAddressBooks);
				if (checkHomesetCapabilities(homeSetAddressBooks, "addressbook")) {
					homeSetAddressBooks.propfind(Mode.CARDDAV_COLLECTIONS);
					
					List<ServerInfo.ResourceInfo> addressBooks = new LinkedList<ServerInfo.ResourceInfo>();
					if (homeSetAddressBooks.getMembers() != null)
						for (WebDavResource resource : homeSetAddressBooks.getMembers())
							if (resource.isAddressBook()) {
								Log.i(TAG, "Found address book: " + resource.getLocation().getPath());
								ServerInfo.ResourceInfo info = new ServerInfo.ResourceInfo(
									ServerInfo.ResourceInfo.Type.ADDRESS_BOOK,
									resource.isReadOnly(),
									resource.getLocation().toString(),
									resource.getDisplayName(),
									resource.getDescription(), resource.getColor()
								);
								
								VCardVersion version = resource.getVCardVersion();
								if (version == null)
									version = VCardVersion.V3_0;	// VCard 3.0 MUST be supported
								info.setVCardVersion(version);
								
								addressBooks.add(info);
							}
					serverInfo.setAddressBooks(addressBooks);
				} else
					Log.w(TAG, "Found address-book home set, but it doesn't advertise CardDAV support");
			}
		}
		
		// CalDAV
		principal = getCurrentUserPrincipal(serverInfo, "caldav");
		if (principal != null) {
			serverInfo.setCalDAV(true);

			principal.propfind(Mode.HOME_SETS);
			String pathCalendars = principal.getCalendarHomeSet();
			if (pathCalendars != null) {
				Log.i(TAG, "Found calendar home set: " + pathCalendars);
			
				WebDavResource homeSetCalendars = new WebDavResource(principal, pathCalendars);
				if (checkHomesetCapabilities(homeSetCalendars, "calendar-access")) {
					homeSetCalendars.propfind(Mode.CALDAV_COLLECTIONS);
					
					List<ServerInfo.ResourceInfo> calendars = new LinkedList<ServerInfo.ResourceInfo>();
					if (homeSetCalendars.getMembers() != null)
						for (WebDavResource resource : homeSetCalendars.getMembers())
							if (resource.isCalendar()) {
								Log.i(TAG, "Found calendar: " + resource.getLocation().getPath());
								if (resource.getSupportedComponents() != null) {
									// CALDAV:supported-calendar-component-set available
									boolean supportsEvents = false;
									for (String supportedComponent : resource.getSupportedComponents())
										if (supportedComponent.equalsIgnoreCase("VEVENT"))
											supportsEvents = true;
									if (!supportsEvents) {	// ignore collections without VEVENT support
										Log.i(TAG, "Ignoring this calendar because of missing VEVENT support");
										continue;
									}
								}
								ServerInfo.ResourceInfo info = new ServerInfo.ResourceInfo(
									ServerInfo.ResourceInfo.Type.CALENDAR,
									resource.isReadOnly(),
									resource.getLocation().toString(),
									resource.getDisplayName(),
									resource.getDescription(), resource.getColor()
								);
								info.setTimezone(resource.getTimezone());
								calendars.add(info);
							}
					serverInfo.setCalendars(calendars);
				} else
					Log.w(TAG, "Found calendar home set, but it doesn't advertise CalDAV support");
			}
		}
						
		if (!serverInfo.isCalDAV() && !serverInfo.isCardDAV())
			throw new DavIncapableException(context.getString(R.string.neither_caldav_nor_carddav));

	}
	
	
	/**
	 * Finds the initial service URL from a given base URI (HTTP[S] or mailto URI, user name, password)
	 * @param serverInfo	User-given service information (including base URI, i.e. HTTP[S] URL+user name+password or mailto URI and password)
	 * @param serviceName	Service name ("carddav" or "caldav")
	 * @return				Initial service URL (HTTP/HTTPS), without user credentials
	 * @throws URISyntaxException when the user-given URI is invalid
	 * @throws MalformedURLException when the user-given URI is invalid
	 * @throws UnknownServiceURLException when no intial service URL could be determined
	 */
	URL getInitialURL(ServerInfo serverInfo, String serviceName) throws URISyntaxException, MalformedURLException {
		String	scheme = null,
				domain = null;
		int		port = -1;
		String	path = "/";
		
		URI baseURI = serverInfo.getBaseURI();
		if ("mailto".equalsIgnoreCase(baseURI.getScheme())) {
			// mailto URIs
			String mailbox = serverInfo.getBaseURI().getSchemeSpecificPart();

			// determine service FQDN
			int pos = mailbox.lastIndexOf("@");
			if (pos == -1)
				throw new URISyntaxException(mailbox, "Email address doesn't contain @");
			domain = mailbox.substring(pos + 1);
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
			Log.d(TAG, "Looking up SRV records for " + name);
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
					for (String segment : (String[])txt.getStrings().toArray())
						if (segment.startsWith("path=")) {
							path = segment.substring(5);
							Log.d(TAG, "Found initial context path for " + serviceName + " at " + domain + " -> " + path);
							break;
						}
				}
			}
		} catch (TextParseException e) {
			throw new URISyntaxException(domain, "Invalid domain name");
		}
		
		if (port != -1)
			return new URL(scheme, domain, port, path);
		else
			return new URL(scheme, domain, path);
	}
	
	
	/**
	 * Detects the current-user-principal for a given WebDavResource. At first, /.well-known/ is tried. Only
	 * if no current-user-principal can be detected for the .well-known location, the given location of the resource
	 * is tried.
	 * @param resource 		Location that will be queried
	 * @param serviceName	Well-known service name ("carddav", "caldav")
	 * @return	WebDavResource of current-user-principal for the given service, or null if it can't be found
	 */
	WebDavResource getCurrentUserPrincipal(ServerInfo serverInfo, String serviceName) throws URISyntaxException, IOException, NotAuthorizedException {
		URL initialURL = getInitialURL(serverInfo, serviceName);
		
		// determine base URL (host name and initial context path)
		WebDavResource base = new WebDavResource(httpClient,
				//new URI(URIUtils.ensureTrailingSlash(serverInfo.getBaseURI())),
				initialURL,
				serverInfo.getUserName(), serverInfo.getPassword(), serverInfo.isAuthPreemptive());
		
		// look for well-known service (RFC 5785)
		try {
			WebDavResource wellKnown = new WebDavResource(base, "/.well-known/" + serviceName);
			wellKnown.propfind(Mode.CURRENT_USER_PRINCIPAL);
			if (wellKnown.getCurrentUserPrincipal() != null)
				return new WebDavResource(wellKnown, wellKnown.getCurrentUserPrincipal());
		} catch (NotAuthorizedException e) {
			Log.d(TAG, "Well-known " + serviceName + " service detection not authorized", e);
			throw e;
		} catch (HttpException e) {
			Log.d(TAG, "Well-known " + serviceName + " service detection failed with HTTP error", e);
		} catch (DavException e) {
			Log.d(TAG, "Well-known " + serviceName + " service detection failed at DAV level", e);
		}

		// fall back to user-given initial context path
		try {
			base.propfind(Mode.CURRENT_USER_PRINCIPAL);
			if (base.getCurrentUserPrincipal() != null)
				return new WebDavResource(base, base.getCurrentUserPrincipal());
		} catch (NotAuthorizedException e) {
			Log.d(TAG, "Not authorized for querying principal for " + serviceName + " service", e);
			throw e;
		} catch (HttpException e) {
			Log.d(TAG, "HTTP error when querying principal for " + serviceName + " service", e);
		} catch (DavException e) {
			Log.d(TAG, "DAV error when querying principal for " + serviceName + " service", e);
		}
		Log.i(TAG, "Couldn't find current-user-principal for service " + serviceName);
		return null;
	}
	
	private static boolean checkHomesetCapabilities(WebDavResource resource, String davCapability) throws URISyntaxException, IOException {
		// check for necessary capabilities
		try {
			resource.options();
			if (resource.supportsDAV(davCapability) &&
				resource.supportsMethod("PROPFIND"))		// check only for methods that MUST be available for home sets
				return true;
		} catch(HttpException e) {
			// for instance, 405 Method not allowed
		}
		return false;
	}
	
	
	SRVRecord selectSRVRecord(Record[] records) {
		if (records.length > 1)
			Log.w(TAG, "Multiple SRV records not supported yet; using first one");
		return (SRVRecord)records[0];
	}

}
