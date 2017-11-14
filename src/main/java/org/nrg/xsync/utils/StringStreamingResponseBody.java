/**
 * 
 */
package org.nrg.xsync.utils;

import java.io.IOException;
import java.io.OutputStream;

import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

public class StringStreamingResponseBody implements StreamingResponseBody {
	
	public static final String MEDIA_TYPE_TEXT_CSV = "text/csv";
	public static final String MEDIA_TYPE_TEXT_PLAIN = "text/plain";
	public static final String MEDIA_TYPE_TEXT_HTML = "text/html";
	public static final String MEDIA_TYPE_TEXT_CSS = "text/css";
	public static final String MEDIA_TYPE_APPLICATION_JSON = "application/json";
	public static String _csvContent;
	
	public StringStreamingResponseBody(final String csvContent) {
		_csvContent = csvContent;
	}

	@Override
	public void writeTo(OutputStream outputStream) throws IOException {
		outputStream.write(_csvContent.getBytes());
	}

}
