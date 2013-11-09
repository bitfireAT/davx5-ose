/*******************************************************************************
 * Copyright (c) 2013 Richard Hirner (bitfire web engineering).
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

import org.apache.http.HttpException;

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
import at.bitfire.davdroid.resource.IncapableResourceException;
import at.bitfire.davdroid.webdav.HttpPropfind.Mode;
import at.bitfire.davdroid.webdav.WebDavResource;

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
		Bundle args;
		
		public ServerInfoLoader(Context context, Bundle args) {
			super(context);
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
			
			try {
				// (1/5) detect capabilities
				WebDavResource base = new WebDavResource(new URI(serverInfo.getBaseURL()), serverInfo.getUserName(),
						serverInfo.getPassword(), serverInfo.isAuthPreemptive(), true);
				base.options();
				
				serverInfo.setCardDAV(base.supportsDAV("addressbook"));
				serverInfo.setCalDAV(base.supportsDAV("calendar-access"));
				if (!base.supportsMethod("PROPFIND") || !base.supportsMethod("GET") ||
					!base.supportsMethod("PUT") || !base.supportsMethod("DELETE") ||
					(!serverInfo.isCalDAV() && !serverInfo.isCardDAV()))
					throw new IncapableResourceException(getContext().getString(R.string.neither_caldav_nor_carddav));
				
				// (2/5) get principal URL
				base.propfind(Mode.CURRENT_USER_PRINCIPAL);
				
				String principalPath = base.getCurrentUserPrincipal();
				if (principalPath != null)
					Log.i(TAG, "Found principal path: " + principalPath);
				else
					throw new IncapableResourceException(getContext().getString(R.string.error_principal_path));
				
				// (3/5) get home sets
				WebDavResource principal = new WebDavResource(base, principalPath);
				principal.propfind(Mode.HOME_SETS);
				
				String pathAddressBooks = null;
				if (serverInfo.isCardDAV()) {
					pathAddressBooks = principal.getAddressbookHomeSet();
					if (pathAddressBooks != null)
						Log.i(TAG, "Found address book home set: " + pathAddressBooks);
					else
						throw new IncapableResourceException(getContext().getString(R.string.error_home_set_address_books));
				}
				
				String pathCalendars = null;
				if (serverInfo.isCalDAV()) {
					pathCalendars = principal.getCalendarHomeSet();
					if (pathCalendars != null)
						Log.i(TAG, "Found calendar home set: " + pathCalendars);
					else
						throw new IncapableResourceException(getContext().getString(R.string.error_home_set_calendars));
				}
				
				// (4/5) get address books
				if (serverInfo.isCardDAV()) {
					WebDavResource homeSetAddressBooks = new WebDavResource(principal, pathAddressBooks, true);
					homeSetAddressBooks.propfind(Mode.MEMBERS_COLLECTIONS);
					
					List<ServerInfo.ResourceInfo> addressBooks = new LinkedList<ServerInfo.ResourceInfo>();
					if (homeSetAddressBooks.getMembers() != null)
						for (WebDavResource resource : homeSetAddressBooks.getMembers())
							if (resource.isAddressBook()) {
								Log.i(TAG, "Found address book: " + resource.getLocation().getPath());
								ServerInfo.ResourceInfo info = new ServerInfo.ResourceInfo(
									ServerInfo.ResourceInfo.Type.ADDRESS_BOOK,
									resource.getLocation().getPath(),
									resource.getDisplayName(),
									resource.getDescription(), resource.getColor()
								);
								addressBooks.add(info);
							}
					
					serverInfo.setAddressBooks(addressBooks);
				}

				// (5/5) get calendars
				if (serverInfo.isCalDAV()) {
					WebDavResource homeSetCalendars = new WebDavResource(principal, pathCalendars, true);
					homeSetCalendars.propfind(Mode.MEMBERS_COLLECTIONS);
					
					List<ServerInfo.ResourceInfo> calendars = new LinkedList<ServerInfo.ResourceInfo>();
					if (homeSetCalendars.getMembers() != null)
						for (WebDavResource resource : homeSetCalendars.getMembers())
							if (resource.isCalendar()) {
								Log.i(TAG, "Found calendar: " + resource.getLocation().getPath());
								ServerInfo.ResourceInfo info = new ServerInfo.ResourceInfo(
									ServerInfo.ResourceInfo.Type.CALENDAR,
									resource.getLocation().getPath(),
									resource.getDisplayName(),
									resource.getDescription(), resource.getColor()
								);
								calendars.add(info);
							}
					
					serverInfo.setCalendars(calendars);
				}
				
			} catch (URISyntaxException e) {
				serverInfo.setErrorMessage(getContext().getString(R.string.exception_uri_syntax, e.getMessage()));
			}  catch (IOException e) {
				serverInfo.setErrorMessage(getContext().getString(R.string.exception_io, e.getLocalizedMessage()));
			} catch (HttpException e) {
				serverInfo.setErrorMessage(getContext().getString(R.string.exception_http, e.getLocalizedMessage()));
			} catch (IncapableResourceException e) {
				serverInfo.setErrorMessage(getContext().getString(R.string.exception_incapable_resource, e.getLocalizedMessage()));
			}
			
			return serverInfo;
		}
	}
}
