/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid;

import android.support.annotation.NonNull;

import org.apache.commons.lang3.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import lombok.Cleanup;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import okio.BufferedSink;

public class DavUtils {

    public static String ARGBtoCalDAVColor(int colorWithAlpha) {
        byte alpha = (byte)(colorWithAlpha >> 24);
        int color = colorWithAlpha & 0xFFFFFF;
        return String.format("#%06X%02X", color, alpha);
    }

    public static RequestBody requestBody(final MediaType mediaType, @NonNull final ByteArrayOutputStream dataStream) throws IOException {
        return new RequestBody() {
            @Override
            public MediaType contentType() {
                return mediaType;
            }

            @Override
            public void writeTo(BufferedSink sink) throws IOException {
                @Cleanup OutputStream os = sink.outputStream();
                dataStream.writeTo(os);
            }
        };
    }

    public static String lastSegmentOfUrl(@NonNull String url) {
        // the list returned by HttpUrl.pathSegments() is unmodifiable, so we have to create a copy
        List<String> segments = new LinkedList<>(HttpUrl.parse(url).pathSegments());
        Collections.reverse(segments);

        for (String segment : segments)
            if (!StringUtils.isEmpty(segment))
                return segment;

        return "/";
    }

}
