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

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.entity.HttpEntityWrapper;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.HttpContext;

class GzipDecompressingEntity extends HttpEntityWrapper {
    public GzipDecompressingEntity(final HttpEntity entity) {
        super(entity);
    }

    @Override
    public InputStream getContent()
        throws IOException, IllegalStateException {

        // the wrapped entity's getContent() decides about repeatability
        InputStream wrappedin = wrappedEntity.getContent();

        return new GZIPInputStream(wrappedin);
    }

    @Override
    public long getContentLength() {
        // length of ungzipped content is not known
        return -1;
    }
    
    
    public static void enable(DefaultHttpClient client) {
		client.addRequestInterceptor(new HttpRequestInterceptor() {
			@Override
			public void process(HttpRequest request, HttpContext context) throws HttpException, IOException {
	            if (!request.containsHeader("Accept-Encoding"))
	                request.addHeader("Accept-Encoding", "gzip");
		    }
		});
	    client.addResponseInterceptor(new HttpResponseInterceptor() {
			@Override
			public void process(HttpResponse response, HttpContext context) throws HttpException, IOException {
	            HttpEntity entity = response.getEntity();
	            if (entity != null) {
	                Header ceheader = entity.getContentEncoding();
	                if (ceheader != null) {
	                    HeaderElement[] codecs = ceheader.getElements();
	                    for (int i = 0; i < codecs.length; i++) {
	                        if (codecs[i].getName().equalsIgnoreCase("gzip")) {
	                            response.setEntity(new GzipDecompressingEntity(response.getEntity()));
	                            return;
	                        }
	                    }
	                }
	            }
			}
	    });
    }
} 
