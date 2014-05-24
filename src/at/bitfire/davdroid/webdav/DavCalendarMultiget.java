/*******************************************************************************
 * Copyright (c) 2014 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 ******************************************************************************/
package at.bitfire.davdroid.webdav;

import org.simpleframework.xml.Namespace;
import org.simpleframework.xml.NamespaceList;
import org.simpleframework.xml.Root;

@Root(name="calendar-multiget")
@NamespaceList({
	@Namespace(reference="DAV:"),
	@Namespace(prefix="C",reference="urn:ietf:params:xml:ns:caldav")
})
@Namespace(prefix="C",reference="urn:ietf:params:xml:ns:caldav")
public class DavCalendarMultiget extends DavMultiget {
}
