/*******************************************************************************
 * Copyright (c) 2014 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 ******************************************************************************/
package at.bitfire.davdroid.webdav;

public class DavNoContentException extends DavException {
	private static final long serialVersionUID = 6256645020350945477L;
	
	private final static String message = "HTTP response entity (content) expected but not received";

	public DavNoContentException() {
		super(message);
	}
}
