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

@Root(name="addressbook-multiget")
@NamespaceList({
	@Namespace(reference="DAV:"),
	@Namespace(prefix="CD",reference="urn:ietf:params:xml:ns:carddav")
})
@Namespace(prefix="CD",reference="urn:ietf:params:xml:ns:carddav")
public class DavAddressbookMultiget extends DavMultiget {
}
