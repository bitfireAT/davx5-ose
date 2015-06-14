/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */
package at.bitfire.davdroid.resource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;

import at.bitfire.davdroid.webdav.DavException;
import at.bitfire.davdroid.webdav.HttpException;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * Represents a resource that can be contained in a LocalCollection or RemoteCollection
 * for synchronization by WebDAV.
 */
@ToString
public abstract class Resource {
	@Getter @Setter protected String name, ETag;
	@Getter @Setter protected String uid;
	@Getter protected long localID;

	public Resource(String name, String ETag) {
		this.name = name;
		this.ETag = ETag;
	}
	
	public Resource(long localID, String name, String ETag) {
		this(name, ETag);
		this.localID = localID;
	}
	
	/** initializes UID and remote file name (required for first upload) */
	public abstract void initialize();
	
	/** fills the resource data from an input stream (for instance, .vcf file for Contact)
	 * @param entity        entity to parse
	 * @param downloader    will be used to fetch additional resources like contact images
	 **/
	public abstract void parseEntity(InputStream entity, AssetDownloader downloader) throws IOException, InvalidResourceException;


	/* returns the MIME type that toEntity() will produce */
	public abstract String getMimeType();

	/** writes the resource data to an output stream (for instance, .vcf file for Contact) */
	public abstract ByteArrayOutputStream toEntity() throws IOException;


	public interface AssetDownloader {
		byte[] download(URI url) throws URISyntaxException, IOException, HttpException, DavException;
	}
}
