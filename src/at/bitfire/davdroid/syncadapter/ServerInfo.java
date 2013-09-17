/*******************************************************************************
 * Copyright (c) 2013 Richard Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 ******************************************************************************/
package at.bitfire.davdroid.syncadapter;

import java.io.Serializable;
import java.util.List;

import lombok.Data;

@Data
public class ServerInfo implements Serializable {
	private static final long serialVersionUID = 6744847358282980437L;
	
	final private String baseURL;
	final private String userName, password;
	
	private String errorMessage;
	
	private boolean calDAV, cardDAV;
	private List<ResourceInfo> addressBooks, calendars;
	
	@Data
	public static class ResourceInfo implements Serializable {
		private static final long serialVersionUID = -5516934508229552112L;
		
		enum Type {
			ADDRESS_BOOK,
			CALENDAR
		}
		
		final Type type;
		final String path, title, description;
	}
}
