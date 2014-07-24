/*******************************************************************************
 * Copyright (c) 2014 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 ******************************************************************************/
package at.bitfire.davdroid.syncadapter;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedList;
import java.util.List;

import android.app.DialogFragment;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.AsyncTaskLoader;
import android.content.Context;
import android.content.Loader;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.Toast;
import at.bitfire.davdroid.R;
import at.bitfire.davdroid.webdav.DavException;
import at.bitfire.davdroid.webdav.DavHttpClient;
import at.bitfire.davdroid.webdav.DavIncapableException;
import at.bitfire.davdroid.webdav.HttpPropfind.Mode;
import at.bitfire.davdroid.webdav.WebDavResource;
import ch.boye.httpclientandroidlib.HttpException;
import ch.boye.httpclientandroidlib.impl.client.CloseableHttpClient;

public class QueryServerDialogFragment extends DialogFragment implements LoaderCallbacks<ServerInfo> {
	private static final String TAG = "davdroid.QueryServerDialogFragment";
	public static final String
		EXTRA_BASE_URL = "base_uri",
		EXTRA_USER_NAME = "user_name",
		EXTRA_PASSWORD = "password",
		EXTRA_AUTH_PREEMPTIVE = "auth_preemptive";
	
	ProgressBar progressBar;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setStyle(DialogFragment.STYLE_NO_TITLE, android.R.style.Theme_Holo_Light_Dialog);
		setCancelable(false);

		Loader<ServerInfo> loader = getLoaderManager().initLoader(0, getArguments(), this);
		if (savedInstanceState == null)		// http://code.google.com/p/android/issues/detail?id=14944
			loader.forceLoad();
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View v = inflater.inflate(R.layout.query_server, container, false);
		return v;
	}

	@Override
	public Loader<ServerInfo> onCreateLoader(int id, Bundle args) {
		Log.i(TAG, "onCreateLoader");
		return new ServerInfoLoader(getActivity(), args);
	}

	@Override
	public void onLoadFinished(Loader<ServerInfo> loader, ServerInfo serverInfo) {
		if (serverInfo.getErrorMessage() != null)
			Toast.makeText(getActivity(), serverInfo.getErrorMessage(), Toast.LENGTH_LONG).show();
		else {
			SelectCollectionsFragment selectCollections = new SelectCollectionsFragment();
			Bundle arguments = new Bundle();
			arguments.putSerializable(SelectCollectionsFragment.KEY_SERVER_INFO, serverInfo);
			selectCollections.setArguments(arguments);
			
			getFragmentManager().beginTransaction()
				.replace(R.id.fragment_container, selectCollections)
				.addToBackStack(null)
				.commitAllowingStateLoss();
		}
		
		getDialog().dismiss();
	}

	@Override
	public void onLoaderReset(Loader<ServerInfo> arg0) {
	}
	
	
	static class ServerInfoLoader extends AsyncTaskLoader<ServerInfo> {
		private static final String TAG = "davdroid.ServerInfoLoader";
		final Bundle args;
		final Context context;
		
		public ServerInfoLoader(Context context, Bundle args) {
			super(context);
			this.context = context;
			this.args = args;
		}

		@Override
		public ServerInfo loadInBackground() {
			ServerInfo serverInfo = new ServerInfo(
				args.getString(EXTRA_BASE_URL),
				args.getString(EXTRA_USER_NAME),
				args.getString(EXTRA_PASSWORD),
				args.getBoolean(EXTRA_AUTH_PREEMPTIVE)
			);
			
			// disable compression and enable network logging for debugging purposes 
			CloseableHttpClient httpClient = DavHttpClient.create(true, true);
			
			try {
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
							homeSetAddressBooks.propfind(Mode.MEMBERS_COLLECTIONS);
							
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
							homeSetCalendars.propfind(Mode.MEMBERS_COLLECTIONS);
							
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
					throw new DavIncapableException(getContext().getString(R.string.neither_caldav_nor_carddav));
				
			} catch (URISyntaxException e) {
				serverInfo.setErrorMessage(getContext().getString(R.string.exception_uri_syntax, e.getMessage()));
			}  catch (IOException e) {
				serverInfo.setErrorMessage(getContext().getString(R.string.exception_io, e.getLocalizedMessage()));
			} catch (HttpException e) {
				Log.e(TAG, "HTTP error while querying server info", e);
				serverInfo.setErrorMessage(getContext().getString(R.string.exception_http, e.getLocalizedMessage()));
			} catch (DavException e) {
				Log.e(TAG, "DAV error while querying server info", e);
				serverInfo.setErrorMessage(getContext().getString(R.string.exception_incapable_resource, e.getLocalizedMessage()));
			}
			
			return serverInfo;
		}
		
		/**
		 * Detects the current-user-principal for a given WebDavResource. At first, /.well-known/ is tried. Only
		 * if no current-user-principal can be detected for the .well-known location, the given location of the resource
		 * is tried.
		 * @param resource 		Location that will be queried
		 * @param serviceName	Well-known service name ("carddav", "caldav")
		 * @return	WebDavResource of current-user-principal for the given service, or null if it can't be found
		 */
		private WebDavResource getCurrentUserPrincipal(WebDavResource resource, String serviceName) throws IOException, HttpException, DavException {
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
		
		private boolean checkCapabilities(WebDavResource resource, String davCapability) throws IOException {
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
}
