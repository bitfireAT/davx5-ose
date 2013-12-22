package at.bitfire.davdroid;

import at.bitfire.davdroid.resource.Resource;

public class ArrayUtils {

	public static <T> Resource[][] partition(Resource[] bigArray, int max) {
		int nItems = bigArray.length;
		int nPartArrays = (nItems + max-1)/max;
		
		Resource[][] partArrays = new Resource[nPartArrays][];
		
		// nItems: number of remaining items
		for (int i = 0; nItems > 0; i++) {
			int n = (nItems < max) ? nItems : max;
			partArrays[i] = new Resource[n];
			System.arraycopy(bigArray, i*max, partArrays[i], 0, n);
			
			nItems -= n;
		}
		
		return partArrays;
	}
	
}
