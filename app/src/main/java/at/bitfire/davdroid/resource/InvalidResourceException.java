/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */
package at.bitfire.davdroid.resource;

public class InvalidResourceException extends Exception {
	private static final long serialVersionUID = 1593585432655578220L;
	
	public InvalidResourceException(String message) {
		super(message);
	}

	public InvalidResourceException(Throwable throwable) {
		super(throwable);
	}
}
