/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */
package at.bitfire.davdroid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Constants {
	public static final String
		ACCOUNT_TYPE = "bitfire.at.davdroid",
		WEB_URL_MAIN = "https://davdroid.bitfire.at/?pk_campaign=davdroid-app",
		WEB_URL_HELP = "https://davdroid.bitfire.at/configuration?pk_campaign=davdroid-app",
		WEB_URL_VIEW_LOGS = "https://github.com/bitfireAT/davdroid/wiki/How-to-view-the-logs";

	//public static final ProdId ICAL_PRODID = new ProdId("-//bitfire web engineering//DAVdroid " + BuildConfig.VERSION_CODE + " (ical4j 2.0-beta1)//EN");

    public static final Logger log = LoggerFactory.getLogger("davdroid");
}
