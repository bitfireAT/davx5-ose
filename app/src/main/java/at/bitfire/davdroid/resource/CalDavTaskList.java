/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.resource;

import android.util.Log;

import org.apache.http.impl.client.CloseableHttpClient;
import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.core.Persister;

import java.io.StringWriter;
import java.net.URISyntaxException;

import at.bitfire.davdroid.webdav.DavCalendarQuery;
import at.bitfire.davdroid.webdav.DavCompFilter;
import at.bitfire.davdroid.webdav.DavFilter;
import at.bitfire.davdroid.webdav.DavMultiget;
import at.bitfire.davdroid.webdav.DavProp;

public class CalDavTaskList extends WebDavCollection<Task> {
	private final static String TAG = "davdroid.CalDAVTaskList";

	public CalDavTaskList(CloseableHttpClient httpClient, String baseURL, String user, String password, boolean preemptiveAuth) throws URISyntaxException {
		super(httpClient, baseURL, user, password, preemptiveAuth);
	}

	@Override
	protected String memberAcceptedMimeTypes()
	{
		return "text/calendar";
	}

	@Override
	protected DavMultiget.Type multiGetType() {
		return DavMultiget.Type.CALENDAR;
	}

	@Override
	protected Task newResourceSkeleton(String name, String ETag) {
		return new Task(name, ETag);
	}


	@Override
	public String getMemberETagsQuery() {
		DavCalendarQuery query = new DavCalendarQuery();

		// prop
		DavProp prop = new DavProp();
		prop.setGetetag(new DavProp.GetETag());
		query.setProp(prop);

		// filter
		DavFilter filter = new DavFilter();
		query.setFilter(filter);

		DavCompFilter compFilter = new DavCompFilter("VCALENDAR");
		filter.setCompFilter(compFilter);

		compFilter.setCompFilter(new DavCompFilter("VTODO"));

		Serializer serializer = new Persister();
		StringWriter writer = new StringWriter();
		try {
			serializer.write(query, writer);
		} catch (Exception e) {
			Log.e(TAG, "Couldn't prepare REPORT query", e);
			return null;
		}

		return writer.toString();
	}

}
