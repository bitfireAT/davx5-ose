/*******************************************************************************
 * Copyright (c) 2013 Richard Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 ******************************************************************************/
package at.bitfire.davdroid.resource;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedList;
import java.util.List;

import lombok.Getter;
import net.fortuna.ical4j.data.ParserException;
import net.fortuna.ical4j.model.ValidationException;

import org.apache.http.HttpException;

import at.bitfire.davdroid.webdav.HttpPropfind;
import at.bitfire.davdroid.webdav.WebDavCollection;
import at.bitfire.davdroid.webdav.WebDavCollection.MultigetType;
import at.bitfire.davdroid.webdav.WebDavResource;
import at.bitfire.davdroid.webdav.WebDavResource.PutMode;

public abstract class RemoteCollection<ResourceType extends Resource> {
	@Getter WebDavCollection collection;

	abstract protected String memberContentType();
	abstract protected MultigetType multiGetType();
	abstract protected ResourceType newResourceSkeleton(String name, String ETag);
	
	public RemoteCollection(String baseURL, String user, String password) throws IOException, URISyntaxException {
		collection = new WebDavCollection(new URI(baseURL), user, password);
	}

	
	/* collection operations */

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
			
		List<ResourceType> resources = new LinkedList<ResourceType>();
		for (WebDavResource member : collection.getMembers())
			resources.add(newResourceSkeleton(member.getName(), member.getETag()));
				return resources.toArray(new Resource[0]);
	}
	
	@SuppressWarnings("unchecked")
	public Resource[] multiGet(ResourceType[] resources) throws IOException, IncapableResourceException, HttpException, ParserException {
		if (resources.length == 1) {
			Resource resource = get(resources[0]);
			return (resource != null) ? (ResourceType[]) new Resource[] { resource } : null;
		}
		
		LinkedList<String> names = new LinkedList<String>();
		for (ResourceType resource : resources)
			names.add(resource.getName());
		
		collection.multiGet(names.toArray(new String[0]), multiGetType());
		
		LinkedList<ResourceType> foundResources = new LinkedList<ResourceType>();
		for (WebDavResource member : collection.getMembers()) {
			ResourceType resource = newResourceSkeleton(member.getName(), member.getETag());
			resource.parseEntity(member.getContent());
			foundResources.add(resource);
		}
		return foundResources.toArray(new Resource[0]);
	}
	
	
	/* internal member operations */

	public ResourceType get(ResourceType resource) throws IOException, HttpException, ParserException {
		WebDavResource member = new WebDavResource(collection, resource.getName());
		member.get();
		resource.parseEntity(member.getContent());
		return resource;
	}
	
	public void add(Resource res) throws IOException, HttpException, ValidationException {
		WebDavResource member = new WebDavResource(collection, res.getName(), res.getETag());
		member.setContentType(memberContentType());
		member.put(res.toEntity().getBytes("UTF-8"), PutMode.ADD_DONT_OVERWRITE);
	}

	public void delete(Resource res) throws IOException, HttpException {
		WebDavResource member = new WebDavResource(collection, res.getName(), res.getETag());
		member.delete();
	}
	
	public void update(Resource res) throws IOException, HttpException, ValidationException {
		WebDavResource member = new WebDavResource(collection, res.getName(), res.getETag());
		member.setContentType(memberContentType());
		member.put(res.toEntity().getBytes("UTF-8"), PutMode.UPDATE_DONT_OVERWRITE);
	}
}
