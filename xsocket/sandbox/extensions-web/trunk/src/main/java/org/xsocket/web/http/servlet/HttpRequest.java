// $Id: Context.java 293 2006-10-09 16:15:39Z grro $

/*
 *  Copyright (c) xsocket.org, 2006 - 2007. All rights reserved.
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 * Please refer to the LGPL license at: http://www.gnu.org/copyleft/lesser.txt
 * The latest copy of this software may be found on http://www.xsocket.org/
 */
package org.xsocket.web.http.servlet;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.Cookie;

import org.xsocket.DataConverter;
import org.xsocket.stream.INonBlockingConnection;



/**
 * 
 * 
 * @author grro@xsocket.org
 */
abstract class HttpRequest {
	
	private static final Logger LOG = Logger.getLogger(HttpRequest.class.getName());

	private INonBlockingConnection connection = null;
	
	private Parser parser = new HeaderParser();
	
	private HttpHeader header = null;
	private InputStreamImpl inputStream = null; 

	private boolean isPostResovled = false;

	
	final void parseData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
		if (parser != null) {
			this.connection = connection;
			parser.parse(connection);
		}
	}
	 
	final protected InputStream getIStream() {
		if (inputStream == null) {
			return new InputStreamImpl(new ByteBuffer[0]);
		}
		return inputStream;
	}
	

	public final String getMethod() {
		return header.getMethod();
	}
	
	public final String getProtocol() {
		return header.getProtocol();
	}
	
	public String getScheme() {
		return header.getScheme();
	}

	public final int getContentLength() {
		return header.getContentLength();
	}
	

	public final String getContentType() {
		return header.getContentType();
	}

	
	public final String getQueryString() {
		return header.getQueryString();
	}
	
	public final String getRequestURI() {
		return header.getRequestURI(); 
	}
	
	
	public final String getParameter(String name) {
		parsePostParameter();
		return header.getParameter(name);
	}
	
	public final Map getParameterMap() {
		parsePostParameter();
		return header.getParameterMap();
	}
	
	public final Enumeration getParameterNames() {
		parsePostParameter();
		return header.getParameterNames();
	}
	
	public final String[] getParameterValues(String name) {
		parsePostParameter();
		return header.getParameterValues(name);
	}
	
	
	private void parsePostParameter() {
		if (getMethod().equals("POST")) {
			if (!isPostResovled) {
				isPostResovled = true;
				
				if (getContentType() != null) {
					
					String[] parts = getContentType().split(";");
					String type = parts[0].trim();
					if (type.equalsIgnoreCase("application/x-www-form-urlencoded")) {
						if (getContentLength() > 0) {
							String enc = "US-ASCII";
							if (parts.length > 1) {
								String subType = parts[1].trim();
								int pos = subType.indexOf("charset=");
								if (pos != -1) {
									enc = subType.substring("charset=".length(), subType.length());
								}
							}

							try {
								InputStream is = getIStream();
								byte[] bytes = new byte[getContentLength()];
								is.read(bytes);
								String s = new String(bytes, enc);
								String[] params = s.split("&");
								for (String param : params) {
									String[] kv = param.split("=");
									
									header.addParameter(kv[0], URLDecoder.decode(kv[1], "UTF-8"));
								}
								
							} catch (Exception ignore) { }
						}
					}
				}
			}
		}		
	}
	
	public final long getDateHeader(String headername) {
		return header.getDateHeader(headername);
	}

	public final String getHeader(String headername) {
		return header.getHeader(headername);
	}

	public final Enumeration getHeaderNames() {
		return header.getHeaderNames();
	}

	public final Enumeration getHeaders(String headername) {
		return header.getHeaders(headername);
	}

	public final int getIntHeader(String headername) {
		return header.getIntHeader(headername);
	}
	
	public final Locale getLocale() {
		return header.getLocale();
	}
	
	public final Enumeration getLocales() {
		return header.getLocales();
	}

	public final String getRemoteAddr() {
		return connection.getRemoteAddress().getHostAddress();
	}

	public final String getRemoteHost() {
		return connection.getRemoteAddress().getHostName();
	}

	public final int getRemotePort() {
		return connection.getRemotePort();
	}
	
	public final String getLocalAddr() {
		return connection.getLocalAddress().getHostAddress();
	}

	public final String getLocalName() {
		return connection.getLocalAddress().getHostName();
	}
	 
	public final int getLocalPort() {
		return getServerPort();
	}

	public final int getServerPort() {
		return connection.getLocalPort();
	}
	
	public final StringBuffer getRequestURL() {
		return new StringBuffer(getScheme() + "://" + getLocalName() + ":" + getLocalPort() + getRequestURI());
	}

	public final Cookie[] getCookies() {
		
		List<String> cookieLines = header.getHeaderValues("Cookie");
		if (cookieLines != null) {
			List<Cookie> cookies = new ArrayList<Cookie>();
			for (String cookieLine : cookieLines) {
				cookieLine = cookieLine.trim();
				List<Cookie> cooks = new ArrayList<Cookie>();
				
				int version = 0;
				
				boolean newSeparator = (cookieLine.indexOf(",") != -1);
				
				// new separator
				if (newSeparator) {
					if (cookieLine.startsWith("$Version=")) {
						int pos = cookieLine.indexOf(",");
						version = Integer.parseInt(cookieLine.substring("$Version=\"".length(), pos - 1));
						cookieLine = cookieLine.substring(pos + 1, cookieLine.length());
					}
					
					String[] cookieValues = cookieLine.split(",");
					for (String cookieValue : cookieValues) {
						String[] parts = cookieValue.split(";");
						String[] nv = parts[0].split("=");
						nv[0] = nv[0].trim();
						nv[1] = nv[1].trim();
						Cookie cookie = new Cookie(nv[0], nv[1]);
						cookies.add(cookie);
						cookie.setVersion(version);
						
						if (parts.length > 1) {
							for (int i = 1; i < parts.length; i++) {
								String[] dv = parts[i].split("=");
								dv[0] = dv[0].trim();
								dv[1] = dv[1].trim();
								if (dv[0].equalsIgnoreCase("$Path")) {
									cookie.setPath(dv[1]);
								} else if (dv[0].equalsIgnoreCase("$Domain")) {
									cookie.setDomain(dv[1]);
								}
							}
						}
					}
					
				// old separator	
				} else {
					if (cookieLine.startsWith("$Version=")) {
						int pos = cookieLine.indexOf(";");
						version = Integer.parseInt(cookieLine.substring("$Version=\"".length(), pos - 1));
						cookieLine = cookieLine.substring(pos + 1, cookieLine.length());
					}
					
					String[] elements = cookieLine.split(";");
					Cookie cookie = null;
					for (String element : elements) {
						String[] nv = element.split("=");
						nv[0]= nv[0].trim();
						nv[1]= nv[1].trim();
						
						if (nv[0].equalsIgnoreCase("$Path")) {
							cookie.setPath(nv[1].substring(1, nv[1].length() - 1));
						} else if (nv[0].equalsIgnoreCase("$Domain")) {
							cookie.setDomain(nv[1].substring(1, nv[1].length() - 1));
						} else {
							cookie = new Cookie(nv[0], nv[1].substring(1, nv[1].length() - 1));
							cookies.add(cookie);
							cookie.setVersion(version);
						}
					}
				}
		
				cookies.addAll(cooks);
			}
			
			return cookies.toArray(new Cookie[cookies.size()]);
		} else {
			return null;
        }
	}
	


	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		
		sb.append("method: " + getMethod() + "\n");
		sb.append("protocol: " + getProtocol() + "\n");
		sb.append("requesteduri: " + getRequestURI() + "\n");
		sb.append("scheme: " + getScheme() + "\n");
		sb.append("Content-Length: " + getContentLength() + "\n");
		sb.append("Content-Type: " + getContentType() + "\n");
		sb.append("QueryString " + getQueryString() + "\n");
		sb.append("Locale: " + getLocale() + "\n");
		sb.append("Locales: ");
		for (Enumeration en = getLocales(); en.hasMoreElements(); ) {
			sb.append(en.nextElement() + " ");
		}
		
		sb.append("\n");
		sb.append("Parameters: \n");
		for (Object obj : getParameterMap().keySet()) {
			String key = (String) obj;
			sb.append("  " + key + " :");
			for (String value : getParameterValues(key)) {
				sb.append(" " + value);
			}
		    sb.append("\n");
		}
			
		
		sb.append("\n");
		sb.append("Request headers: \n");
		Enumeration headerNames = getHeaderNames();
		while (headerNames.hasMoreElements()) {
			String name = (String) headerNames.nextElement();
		    String value = getHeader(name);
		    sb.append("  " + name + " : " + value + "\n");
		}

		
		sb.append("\r\n\r\n");
		if (inputStream != null) {
			for (ByteBuffer buffer : inputStream.buffers) {
				try {
					sb.append(DataConverter.toString(buffer.duplicate()));
				} catch (UnsupportedEncodingException ignore) { }
			}
		}
		
		return sb.toString();
	}

	
	private interface Parser {
		void parse(INonBlockingConnection connection) throws IOException, BufferUnderflowException;
	}
	
	
	private final class HeaderParser implements Parser {
		
		public void parse(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
			
			// pare header 
			String rawHeader = connection.readStringByDelimiter("\r\n\r\n", Integer.MAX_VALUE);
			header = new HttpHeader(rawHeader);
			
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("got request header " + rawHeader);
			}
				
			// let the body be parsed (if exists)
			if (header.hasBody()) {
				String transferEncoing = header.getTransferEncoding();
				if (transferEncoing != null) {
					if (transferEncoing.equalsIgnoreCase("chunked")) {
						parser = new ChunkedBodyParser();
					} else {
						parser = new BodyParser(header.getContentLength());
					}
				} else {
					parser = new BodyParser(header.getContentLength());
				}
				
				parser.parse(connection);
			}
		}
	}

	private final class BodyParser implements Parser {
		private ByteBuffer[] buffers = null;
		private int bodylength = 0;
		
		BodyParser(int bodylength) {
			this.bodylength = bodylength;
		}
		
		public void parse(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
			if (bodylength > 0) {
				buffers = connection.readByteBufferByLength(bodylength);
				inputStream = new InputStreamImpl(buffers);
			}  
			
			// finish
			parser = null;
		}		
	}
	
	private final class ChunkedBodyParser implements Parser {
		
		private int chunklength = -1;
		private final List<ByteBuffer> buffers = new ArrayList<ByteBuffer>();
		
		public void parse(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
			
			do {
				if (chunklength < 0) {
					String s = connection.readStringByDelimiter("\r\n", Integer.MAX_VALUE).trim();
					if (s.length() > 0) {
						chunklength = Integer.valueOf(s, 16);
					}
				}
				
				if (chunklength > 0) {
					ByteBuffer[] bufs = connection.readByteBufferByLength(chunklength);
					for (ByteBuffer buffer : bufs) {
						buffers.add(buffer);
					}
					chunklength = -1;
				}
			} while (chunklength != 0);
			
			inputStream = new InputStreamImpl(buffers.toArray(new ByteBuffer[buffers.size()]));
			
			// finish
			parsePostParameter();
			parser = null;
		}
	}
	
	
	private static final class InputStreamImpl extends InputStream {
		private boolean isOpen = true;
		private int currentBufferNumber = 0;
		private ByteBuffer[] buffers = null;
		
		
		public InputStreamImpl(ByteBuffer[] buffers) {
			this.buffers = buffers;
		}
		
		public boolean isOpen() {
			return isOpen;
		}
		
		@Override
		public void close() throws IOException {
			isOpen = false;
		}
		
		@Override
		public int read() throws IOException {
			if (available() == 0) {
				return -1;
			}
			
			int bufferNumbers = buffers.length;
			do {
				if (buffers[currentBufferNumber].remaining() > 0) {
					return buffers[currentBufferNumber].get();
				} else {
					currentBufferNumber++;
				}
			} while (currentBufferNumber < bufferNumbers);
			
			return -1;
		}
			
		
		@Override
		public int read(byte[] bytes, int offset, int length) throws IOException {
			if (available() == 0) {
				return -1;
			}
			
			int read = 0;
			do {
				int availableInBuffer = buffers[currentBufferNumber].remaining();

				// enough data in buffer
				if (availableInBuffer >= (length - read)) {
					buffers[currentBufferNumber].get(bytes, (offset + read), (length - read));
					read = length;
					
				// not enough data in buffer
				} else {
					buffers[currentBufferNumber].get(bytes, (offset + read), availableInBuffer);
					read += availableInBuffer;
					currentBufferNumber++;
				}
				
				
			} while ((read < length) && (currentBufferNumber < buffers.length));
			
			return read;
		}
		
		
		
		@Override
		public int available() throws IOException {
			int count = 0;
			if (buffers != null) {
				int totalBuffes = buffers.length;
				for (int i = currentBufferNumber; i < totalBuffes; i++) {
					count += buffers[i].remaining();
				}
			}
			return count;
		}
	}
}
