/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */
package at.bitfire.davdroid.webdav;

import lombok.Getter;

public class HttpException extends org.apache.http.HttpException {
	private static final long serialVersionUID = -4805778240079377401L;
	
	@Getter private int code;
	
	HttpException(int code, String message) {
		super(message);
		this.code = code;
	}
	
	public boolean isClientError() {
		return code/100 == 4;
	}

}
