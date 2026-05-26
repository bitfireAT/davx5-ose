/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package org.dmfs.tasks.contract;

import android.net.Uri;

import java.util.HashMap;
import java.util.Map;


/**
 * TODO
 */
@SuppressWarnings("ALL")
public final class UriFactory
{
    private final String mAuthority;
    private final Map<String, Uri> mUriMap = new HashMap<String, Uri>(16);


    UriFactory(String authority)
    {
        mAuthority = authority;
        mUriMap.put(null, Uri.parse("content://" + authority));
    }


    void addUri(String path)
    {
        mUriMap.put(path, Uri.parse("content://" + mAuthority + "/" + path));
    }


    Uri getUri()
    {
        return mUriMap.get(null);
    }


    Uri getUri(String path)
    {
        return mUriMap.get(path);
    }
}
