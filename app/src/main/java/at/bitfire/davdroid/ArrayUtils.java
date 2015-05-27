/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */
package at.bitfire.davdroid;

import java.lang.reflect.Array;

public class ArrayUtils {

	@SuppressWarnings("unchecked")
	public static <T> T[][] partition(T[] bigArray, int max) {
		int nItems = bigArray.length;
		int nPartArrays = (nItems + max-1)/max;
		
		T[][] partArrays = (T[][])Array.newInstance(bigArray.getClass().getComponentType(), nPartArrays, 0); 
		
		// nItems is now the number of remaining items
		for (int i = 0; nItems > 0; i++) {
			int n = (nItems < max) ? nItems : max;
			partArrays[i] = (T[])Array.newInstance(bigArray.getClass().getComponentType(), n); 
			System.arraycopy(bigArray, i*max, partArrays[i], 0, n);
			
			nItems -= n;
		}
		
		return partArrays;
	}
	
}
