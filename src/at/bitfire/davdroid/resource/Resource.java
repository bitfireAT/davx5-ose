/*******************************************************************************
 * Copyright (c) 2013 Richard Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 ******************************************************************************/
package at.bitfire.davdroid.resource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import net.fortuna.ical4j.data.ParserException;
import net.fortuna.ical4j.model.ValidationException;
import ezvcard.VCardException;

@ToString
public abstract class Resource {
	@Getter protected String name, ETag;
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
	
	// sets resource name and UID
	public abstract void initRemoteFields();
	
	public abstract void parseEntity(InputStream entity) throws IOException, ParserException, VCardException;
	public abstract ByteArrayOutputStream toEntity() throws IOException, ValidationException;
}
