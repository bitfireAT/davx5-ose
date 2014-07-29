package at.bitfire.davdroid.syncadapter;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedList;
import java.util.List;

import ch.boye.httpclientandroidlib.HttpException;
import ch.boye.httpclientandroidlib.impl.client.CloseableHttpClient;
import ezvcard.VCardVersion;
import android.content.Context;
import android.util.Log;
import at.bitfire.davdroid.R;
import at.bitfire.davdroid.webdav.DavException;
import at.bitfire.davdroid.webdav.DavHttpClient;
import at.bitfire.davdroid.webdav.DavIncapableException;
import at.bitfire.davdroid.webdav.WebDavResource;
import at.bitfire.davdroid.webdav.HttpPropfind.Mode;

public class DavResourceFinder {
	private final static String TAG = "davdroid.DavResourceFinder";
	
	
	public static void findResources(Context context, ServerInfo serverInfo) throws URISyntaxException, DavException, HttpException, IOException {
		// disable compression and enable network logging for debugging purposes 
		CloseableHttpClient httpClient = DavHttpClient.create(true, true);
		
		WebDavResource base = new WebDavResource(httpClient, new URI(serverInfo.getProvidedURL()), serverInfo.getUserName(),
				serverInfo.getPassword(), serverInfo.isAuthPreemptive());

		// CardDAV
		WebDavResource principal = getCurrentUserPrincipal(base, "carddav");
		if (principal != null) {
			serverInfo.setCardDAV(true);
		
			principal.propfind(Mode.HOME_SETS);
			String pathAddressBooks = principal.getAddressbookHomeSet();
			if (pathAddressBooks != null) {
				Log.i(TAG, "Found address book home set: " + pathAddressBooks);
			
				WebDavResource homeSetAddressBooks = new WebDavResource(principal, pathAddressBooks);
				if (checkCapabilities(homeSetAddressBooks, "addressbook")) {
					homeSetAddressBooks.propfind(Mode.CARDDAV_COLLECTIONS);
					
					List<ServerInfo.ResourceInfo> addressBooks = new LinkedList<ServerInfo.ResourceInfo>();
					if (homeSetAddressBooks.getMembers() != null)
						for (WebDavResource resource : homeSetAddressBooks.getMembers())
							if (resource.isAddressBook()) {
								Log.i(TAG, "Found address book: " + resource.getLocation().getRawPath());
								ServerInfo.ResourceInfo info = new ServerInfo.ResourceInfo(
									ServerInfo.ResourceInfo.Type.ADDRESS_BOOK,
									resource.isReadOnly(),
									resource.getLocation().toASCIIString(),
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
		principal = getCurrentUserPrincipal(base, "caldav");
		if (principal != null) {
			serverInfo.setCalDAV(true);

			principal.propfind(Mode.HOME_SETS);
			String pathCalendars = principal.getCalendarHomeSet();
			if (pathCalendars != null) {
				Log.i(TAG, "Found calendar home set: " + pathCalendars);
			
				WebDavResource homeSetCalendars = new WebDavResource(principal, pathCalendars);
				if (checkCapabilities(homeSetCalendars, "calendar-access")) {
					homeSetCalendars.propfind(Mode.CALDAV_COLLECTIONS);
					
					List<ServerInfo.ResourceInfo> calendars = new LinkedList<ServerInfo.ResourceInfo>();
					if (homeSetCalendars.getMembers() != null)
						for (WebDavResource resource : homeSetCalendars.getMembers())
							if (resource.isCalendar()) {
								Log.i(TAG, "Found calendar: " + resource.getLocation().getRawPath());
								if (resource.getSupportedComponents() != null) {
									// CALDAV:supported-calendar-component-set available
									boolean supportsEvents = false;
									for (String supportedComponent : resource.getSupportedComponents())
										if (supportedComponent.equalsIgnoreCase("VEVENT"))
											supportsEvents = true;
									if (!supportsEvents)	// ignore collections without VEVENT support
										continue;
								}
								ServerInfo.ResourceInfo info = new ServerInfo.ResourceInfo(
									ServerInfo.ResourceInfo.Type.CALENDAR,
									resource.isReadOnly(),
									resource.getLocation().toASCIIString(),
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
	 * Detects the current-user-principal for a given WebDavResource. At first, /.well-known/ is tried. Only
	 * if no current-user-principal can be detected for the .well-known location, the given location of the resource
	 * is tried.
	 * @param resource 		Location that will be queried
	 * @param serviceName	Well-known service name ("carddav", "caldav")
	 * @return	WebDavResource of current-user-principal for the given service, or null if it can't be found
	 */
	private static WebDavResource getCurrentUserPrincipal(WebDavResource resource, String serviceName) throws IOException, HttpException, DavException {
		// look for well-known service (RFC 5785)
		try {
			WebDavResource wellKnown = new WebDavResource(resource, "/.well-known/" + serviceName);
			wellKnown.propfind(Mode.CURRENT_USER_PRINCIPAL);
			if (wellKnown.getCurrentUserPrincipal() != null)
				return new WebDavResource(wellKnown, wellKnown.getCurrentUserPrincipal());
		} catch (HttpException e) {
			Log.d(TAG, "Well-known service detection failed with HTTP error", e);
		} catch (DavException e) {
			Log.d(TAG, "Well-known service detection failed at DAV level", e);
		}

		// fall back to user-given initial context path 
		resource.propfind(Mode.CURRENT_USER_PRINCIPAL);
		if (resource.getCurrentUserPrincipal() != null)
			return new WebDavResource(resource, resource.getCurrentUserPrincipal());
		return null;
	}
	
	private static boolean checkCapabilities(WebDavResource resource, String davCapability) throws IOException {
		// check for necessary capabilities
		try {
			resource.options();
			if (resource.supportsDAV(davCapability) &&
				resource.supportsMethod("PROPFIND") &&
				resource.supportsMethod("GET") &&
				resource.supportsMethod("REPORT") &&
				resource.supportsMethod("PUT") &&
				resource.supportsMethod("DELETE"))
				return true;
		} catch(HttpException e) {
			// for instance, 405 Method not allowed
		}
		return false;
	}
	
}
