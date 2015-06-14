/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.webdav;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.Namespace;

import lombok.Getter;
import lombok.Setter;

@Namespace(prefix="C",reference="urn:ietf:params:xml:ns:caldav")
public class DavCompFilter {
	public DavCompFilter(String name) {
		this.name = name;
	}

	@Attribute(required=false)
	final String name;

	@Element(required=false,name="comp-filter")
	@Getter @Setter	DavCompFilter compFilter;
}
