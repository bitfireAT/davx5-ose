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
package at.bitfire.davdroid;

import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

import android.util.Log;

public class LoggingInputStream extends FilterInputStream {
	private static final int MAX_LENGTH = 1000;	// don't log more than this amount of data

	String tag;

	ByteArrayOutputStream log = new ByteArrayOutputStream(MAX_LENGTH);
	int logSize = 0;
	boolean overflow = false;
	

	public LoggingInputStream(String tag, InputStream proxy) {
		super(proxy);
		this.tag = tag;
	}
	

	@Override
	public boolean markSupported() {
		return false;
	}

	@Override
	public int read() throws IOException {
		int b = super.read();
		if (logSize < MAX_LENGTH) {
			log.write(b);
			logSize++;
		} else
			overflow = true;
		return b;
	}

	@Override
	public int read(byte[] buffer, int byteOffset, int byteCount)
			throws IOException {
		int read = super.read(buffer, byteOffset, byteCount);
		int bytesToLog = read;
		if (bytesToLog + logSize > MAX_LENGTH) {
			bytesToLog = MAX_LENGTH - logSize;
			overflow = true;
		}
		if (bytesToLog > 0) {
			log.write(buffer, byteOffset, bytesToLog);
			logSize += bytesToLog;
		}
		return read;
	}

	@Override
	public void close() throws IOException {
		Log.d(tag, log.toString() + (overflow ? "…" : ""));
		super.close();
	}

}
