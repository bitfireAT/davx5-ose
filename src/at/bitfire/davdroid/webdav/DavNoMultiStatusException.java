/*******************************************************************************
 * Copyright (c) 2014 Richard Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 * 
 * Contributors:
 *     Richard Hirner (bitfire web engineering) - initial API and implementation
 ******************************************************************************/
package at.bitfire.davdroid.webdav;

public class DavNoMultiStatusException extends DavException {
	private static final long serialVersionUID = -3600405724694229828L;
	
	private final static String message = "207 Multi-Status expected but not received";
	
	public DavNoMultiStatusException() {
		super(message);
	}
}
