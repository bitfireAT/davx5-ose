/*******************************************************************************
 * Copyright (c) 2013 Richard Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 ******************************************************************************/
package at.bitfire.davdroid.resource;

import java.io.IOException;

import net.fortuna.ical4j.data.ParserException;

import org.apache.http.HttpException;

import lombok.Getter;
import at.bitfire.davdroid.webdav.HttpPropfind;
import at.bitfire.davdroid.webdav.WebDavCollection;
import at.bitfire.davdroid.webdav.WebDavResource;
import at.bitfire.davdroid.webdav.WebDavResource.PutMode;

public abstract class RemoteCollection {
	@Getter WebDavCollection collection;

	protected abstract String memberContentType();

	
	/* collection methods */

	public String getCTag() throws IOException, HttpException {
		try {
			if (collection.getCTag() == null && collection.getMembers() == null)	// not already fetched
				collection.propfind(HttpPropfind.Mode.COLLECTION_CTAG);
		} catch (IncapableResourceException e) {
			return null;
		}
		return collection.getCTag();
	}
	
	public Resource[] getMemberETags() throws IOException, IncapableResourceException, HttpException {
		collection.propfind(HttpPropfind.Mode.MEMBERS_ETAG);
		return null;
	}
	
	public abstract Resource[] multiGet(Resource[] resource) throws IOException, IncapableResourceException, HttpException, ParserException;
	
	
	/* internal member methods */

	public Resource get(Resource resource) throws IOException, HttpException, ParserException {
		WebDavResource member = new WebDavResource(collection, resource.getName());
		member.get();
		resource.parseEntity(member.getContent());
		return resource;
	}
	
	public void add(Resource resource) throws IOException, HttpException {
		WebDavResource member = new WebDavResource(collection, resource.getName(), resource.getETag());
		member.setContentType(memberContentType());
		member.put(resource.toEntity().getBytes("UTF-8"), PutMode.ADD_DONT_OVERWRITE);
	}

	public void delete(Resource resource) throws IOException, HttpException {
		WebDavResource member = new WebDavResource(collection, resource.getName(), resource.getETag());
		member.delete();
	}
	
	public void update(Resource resource) throws IOException, HttpException {
		WebDavResource member = new WebDavResource(collection, resource.getName(), resource.getETag());
		member.setContentType(memberContentType());
		member.put(resource.toEntity().getBytes("UTF-8"), PutMode.UPDATE_DONT_OVERWRITE);
	}
}
