/*
 * Copyright (C) 2014 Marten Gajda <marten@dmfs.org>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 */

package org.dmfs.provider.tasks;

import java.util.HashMap;
import java.util.Map;

import android.net.Uri;


public class UriFactory
{
	public final String authority;

	private final Map<String, Uri> mUriMap = new HashMap<String, Uri>(16);


	UriFactory(String authority)
	{
		this.authority = authority;
		mUriMap.put((String) null, Uri.parse("content://" + authority));
	}


	void addUri(String path)
	{
		mUriMap.put(path, Uri.parse("content://" + authority + "/" + path));
	}


	public Uri getUri()
	{
		return mUriMap.get(null);
	}


	public Uri getUri(String path)
	{
		return mUriMap.get(path);
	}
}
