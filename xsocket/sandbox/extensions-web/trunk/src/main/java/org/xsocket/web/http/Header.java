package org.xsocket.web.http;

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xsocket.IDataSource;


abstract class Header implements IHeader {
	
	private static final Logger LOG = Logger.getLogger(Header.class.getName());
	

	
	private String firstLine = null;
	private Map<String, List<String>> headers = new HashMap<String, List<String>>();

	

	
	Header(String firstLine, String... headerLines) {
		this.firstLine = firstLine;
		
		for (int i = 0; i < headerLines.length; i++) {
			int idx = headerLines[i].indexOf(":");
			if (idx != -1) {
				addHeader(headerLines[i].substring(0, idx).toUpperCase(), headerLines[i].substring(idx + 1, headerLines[i].length()).trim());
			} else {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.info("invalid header entry: " + headerLines[i]);
				}
			}
		}
	}
	

	protected final String getFirstLine() {
		return firstLine;
	}
	
	
	public final int getContentLength() {
		String s = getHeader("Content-Length");
		if (s == null) {
			return -1;
		} else {
			return Integer.parseInt(s);
		}
	}
	
	public final String getTransferEncoding() {
		return getHeader("Transfer-Encoding");
	}

	
	public boolean hasBody() {
		if (getContentLength() > 0) {
			return true;
		}
		
		String transferEncoding = getHeader("Transfer-Encoding");
		if (transferEncoding == null) {
			return false;
		} else {
			if (transferEncoding.equalsIgnoreCase("chunked")) {
				return true;
			}
		}
		
		return false;
	}

	
	public void addHeader(String headername, String headervalue) {
		headername = headername.toUpperCase();
		List<String> values = headers.get(headername);
		
		if (values == null) {
			values = new ArrayList<String>();
			headers.put(headername, values);
		} 
		
		values.add(headervalue);
	}

	
	public boolean containsHeader(String headername) {
		headername = headername.toUpperCase();
		return headers.containsKey(headername);
	}
	
	
	public final Enumeration getHeaderNames() {
		return Collections.enumeration(headers.keySet());
	}

	@SuppressWarnings("unchecked")
	public final Enumeration getHeaders(String headername) {
		headername = headername.toUpperCase();
		if (headers.containsKey(headername)) {
			return Collections.enumeration(headers.get(headername));
		} else {
			return Collections.enumeration(new ArrayList());
		}
	}

	
	public final String getHeader(String headername) {
		headername = headername.toUpperCase();
		List<String> values = headers.get(headername);
		if (values != null) {
			return values.get(0);
		} else {
			return null;
		}
	}
	
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		
		// write request line
		sb.append(getFirstLine() + "\r\n");
				
		
		// write message headers
		for (Enumeration enName = getHeaderNames(); enName.hasMoreElements(); ) {
			String headername = (String) enName.nextElement();
			for (Enumeration enValue = getHeaders(headername); enValue.hasMoreElements(); ) {
				String headerLine = headername + ": " + enValue.nextElement();
				sb.append(headerLine + "\r\n");
			}
		}
	
		
		// write blank line
		sb.append("\r\n");
		return sb.toString();
	}
	
	
	


	
	static Header readFrom(IDataSource dataSource, int maxLength) throws BufferUnderflowException, IOException {
		String rawHeader = dataSource.readStringByDelimiter("\r\n\r\n", "US-ASCII", maxLength);
		
		String firstLine = rawHeader.substring(0, rawHeader.indexOf("\r\n"));
		String[] headerLines = rawHeader.substring(rawHeader.indexOf("\r\n") + 2, rawHeader.length()).split("\r\n"); 

		if (firstLine.toUpperCase().startsWith("HTTP/")) {
			return new ResponseHeader(firstLine, headerLines);
		} else {
			return new RequestHeader(firstLine, headerLines);
		}		
	}
}	