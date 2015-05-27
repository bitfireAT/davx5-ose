/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */
package at.bitfire.davdroid.webdav;

import org.apache.http.HttpStatus;

public class NotAuthorizedException extends HttpException {
	private static final long serialVersionUID = 2490525047224413586L;

	public NotAuthorizedException(String reason) {
		super(HttpStatus.SC_UNAUTHORIZED, reason);
	}
	
}
