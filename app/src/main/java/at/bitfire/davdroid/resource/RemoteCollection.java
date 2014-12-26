/*******************************************************************************
 * Copyright (c) 2014 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 ******************************************************************************/
package at.bitfire.davdroid.resource;

import android.util.Log;

import net.fortuna.ical4j.model.ValidationException;

import org.apache.http.impl.client.CloseableHttpClient;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.LinkedList;
import java.util.List;

import at.bitfire.davdroid.URIUtils;
import at.bitfire.davdroid.webdav.DavException;
import at.bitfire.davdroid.webdav.DavMultiget;
import at.bitfire.davdroid.webdav.DavNoContentException;
import at.bitfire.davdroid.webdav.HttpException;
import at.bitfire.davdroid.webdav.HttpPropfind;
import at.bitfire.davdroid.webdav.WebDavResource;
import at.bitfire.davdroid.webdav.WebDavResource.PutMode;
import ezvcard.io.text.VCardParseException;
import lombok.Cleanup;
import lombok.Getter;

/**
 * Represents a remotely stored synchronizable collection (collection as in
 * WebDAV terminology).
 *
 * @param <T> Subtype of Resource that can be stored in the collection
 */
public abstract class RemoteCollection<T extends Resource> {
	private static final String TAG = "davdroid.RemoteCollection";
	
	CloseableHttpClient httpClient;
	@Getter WebDavResource collection;

	abstract protected String memberContentType();
	abstract protected DavMultiget.Type multiGetType();
	abstract protected T newResourceSkeleton(String name, String ETag);
	
	public RemoteCollection(CloseableHttpClient httpClient, String baseURL, String user, String password, boolean preemptiveAuth) throws URISyntaxException {
		this.httpClient = httpClient;
		
		collection = new WebDavResource(httpClient, URIUtils.parseURI(baseURL, false), user, password, preemptiveAuth);
	}

	
	/* collection operations */

	public String getCTag() throws URISyntaxException, IOException, HttpException {
		try {
			if (collection.getCTag() == null && collection.getMembers() == null)	// not already fetched
				collection.propfind(HttpPropfind.Mode.COLLECTION_CTAG);
		} catch (DavException e) {
			return null;
		}
		return collection.getCTag();
	}
	
	public Resource[] getMemberETags() throws URISyntaxException, IOException, DavException, HttpException {
		collection.propfind(HttpPropfind.Mode.MEMBERS_ETAG);
			
		List<T> resources = new LinkedList<T>();
		if (collection.getMembers() != null) {
			for (WebDavResource member : collection.getMembers())
				resources.add(newResourceSkeleton(member.getName(), member.getETag()));
		}
		return resources.toArray(new Resource[0]);
	}
	
	@SuppressWarnings("unchecked")
	public Resource[] multiGet(Resource[] resources) throws URISyntaxException, IOException, DavException, HttpException {
		try {
			if (resources.length == 1)
				return (T[]) new Resource[] { get(resources[0]) };
			
			Log.i(TAG, "Multi-getting " + resources.length + " remote resource(s)");
			
			LinkedList<String> names = new LinkedList<String>();
			for (Resource resource : resources)
				names.add(resource.getName());
			
			LinkedList<T> foundResources = new LinkedList<T>();
			collection.multiGet(multiGetType(), names.toArray(new String[0]));
			if (collection.getMembers() == null)
				throw new DavNoContentException();
			
			for (WebDavResource member : collection.getMembers()) {
				T resource = newResourceSkeleton(member.getName(), member.getETag());
				try {
					if (member.getContent() != null) {
						@Cleanup InputStream is = new ByteArrayInputStream(member.getContent());
						resource.parseEntity(is);
						foundResources.add(resource);
					} else
						Log.e(TAG, "Ignoring entity without content");
				} catch (InvalidResourceException e) {
					Log.e(TAG, "Ignoring unparseable entity in multi-response", e);
				}
			}
			
			return foundResources.toArray(new Resource[0]);
		} catch (InvalidResourceException e) {
			Log.e(TAG, "Couldn't parse entity from GET", e);
		}
		
		return new Resource[0];
	}
	
	
	/* internal member operations */

	public Resource get(Resource resource) throws URISyntaxException, IOException, HttpException, DavException, InvalidResourceException {
		WebDavResource member = new WebDavResource(collection, resource.getName());
		
		if (resource instanceof Contact)
			member.get(Contact.MIME_TYPE);
		else if (resource instanceof Event)
			member.get(Event.MIME_TYPE);
		else {
			Log.wtf(TAG, "Should fetch something, but neither contact nor calendar");
			throw new InvalidResourceException("Didn't now which MIME type to accept");
		}
		
		byte[] data = member.getContent();
		if (data == null)
			throw new DavNoContentException();
		
		@Cleanup InputStream is = new ByteArrayInputStream(data);
		try {
			resource.parseEntity(is);
		} catch(VCardParseException e) {
			throw new InvalidResourceException(e);
		}
		return resource;
	}
	
	// returns ETag of the created resource, if returned by server
	public String add(Resource res) throws URISyntaxException, IOException, HttpException, ValidationException {
		WebDavResource member = new WebDavResource(collection, res.getName(), res.getETag());
		member.setContentType(memberContentType());
		
		@Cleanup ByteArrayOutputStream os = res.toEntity();
		String eTag = member.put(os.toByteArray(), PutMode.ADD_DONT_OVERWRITE);
		
		// after a successful upload, the collection has implicitely changed, too
		collection.invalidateCTag();
		
		return eTag;
	}

	public void delete(Resource res) throws URISyntaxException, IOException, HttpException {
		WebDavResource member = new WebDavResource(collection, res.getName(), res.getETag());
		member.delete();
		
		collection.invalidateCTag();
	}
	
	// returns ETag of the updated resource, if returned by server
	public String update(Resource res) throws URISyntaxException, IOException, HttpException, ValidationException {
		WebDavResource member = new WebDavResource(collection, res.getName(), res.getETag());
		member.setContentType(memberContentType());
		
		@Cleanup ByteArrayOutputStream os = res.toEntity();
		String eTag = member.put(os.toByteArray(), PutMode.UPDATE_DONT_OVERWRITE);
		
		// after a successful upload, the collection has implicitely changed, too
		collection.invalidateCTag();
		
		return eTag;
	}
}
