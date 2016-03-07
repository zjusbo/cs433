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
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;

import org.xsocket.DataConverter;
import org.xsocket.stream.INonBlockingConnection;




/**
 * 
 * 
 * @author grro@xsocket.org
 */
class HttpResponse {
	private static final int DEFAULT_BUFFER_SIZE = 4096; 

	private static final Logger LOG = Logger.getLogger(HttpResponse.class.getName());
	
	private INonBlockingConnection connection = null;
	
	private HttpHeader header = new HttpHeader();
	private final OutputStream outputStream = new OutputStreamImpl();  
	private List<Cookie> cookies = new ArrayList<Cookie>();
	
	private int status = HttpServletResponse.SC_OK;
	private String statusText = " OK";

	// out buffer
	private final List<ByteBuffer> outBuffers = new ArrayList<ByteBuffer>();
	private boolean isCommitted = false;
	private int bufferSize = DEFAULT_BUFFER_SIZE;

	private boolean isOpen = true;

	
	public HttpResponse(INonBlockingConnection connection) {
		this.connection = connection;
	}
	
	protected final OutputStream getOSteam() {
		return outputStream;
	}
	
	public final void addCookie(Cookie cookie) {
		cookies.add(cookie);
	}

	public final void addDateHeader(String headername, long date) {
		header.addDateHeader(headername, date);
	}
	
	public final void setDateHeader(String headername, long date) {
		header.setDateHeader(headername, date);
	}


	public final void addHeader(String headername, String headervalue) {
		header.addHeader(headername, headervalue);
	}

	
	public final void setHeader(String headername, String headervalue) {
		header.setHeader(headername, headervalue);
	}
 
	public final void setIntHeader(String headername, int headervalue) {
		header.setIntHeader(headername, headervalue);
	}

	public final void addIntHeader(String headername, int headervalue) {
		header.addIntHeader(headername, headervalue);
	}

	
	public final boolean containsHeader(String headername) {
		return header.containsHeader(headername);
	}

	public final void sendError(int code) throws IOException {
		sendError(code, "<null>");
	}

	public final void sendError(int code, String message) throws IOException {
		setStatus(code);
		
		setContentType("text/html");

		PrintWriter pw = new PrintWriter(new OutputStreamWriter(outputStream));
		pw.println("<?xml version=\"1.0\" encoding=\"ISO-8859-1\" ?>"
				   + "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">"
				   + "<html xmlns=\"http://www.w3.org/1999/xhtml\">"
		  		   + " <head>"
				   + "  <meta http-equiv=\"Content-Type\" content=\"text/html; charset=ISO-8859-1\"/>"
				   + "  <title>"
				   + "    Error"
				   + "  </title>"
                   + " </head>"
                   + " <body>"
                   + "  <h2>"
				   + "   Error " + code
				   + "  </h2>"
				   +    message
				   + "  <br><br>"
				   +    new Date() + " (xSocket/" + getVersion() + ")"
				   + " </body>"
                   + "</html>");
		pw.flush();
		pw.close();
	}

	
	
	public final void setStatus(int status) {
		if (status == HttpServletResponse.SC_OK) {
			setStatus(HttpServletResponse.SC_OK, "OK");
		
		} else if (status == HttpServletResponse.SC_MOVED_TEMPORARILY) {
			setStatus(HttpServletResponse.SC_MOVED_TEMPORARILY, "Found");
			
		} else if (status == HttpServletResponse.SC_TEMPORARY_REDIRECT) {
			setStatus(HttpServletResponse.SC_TEMPORARY_REDIRECT, "Temporary Redirect");
			
		} else {
			setStatus(status, "");
		}
	}

	public final void setStatus(int status, String statusText) {
		this.status = status;
		this.statusText = statusText;
	}



	public final boolean isCommitted() {
		return isCommitted;
	}


	public final void setBufferSize(int bufferSize) {
		this.bufferSize = bufferSize;
	}
	

	public final int getBufferSize() {
		return bufferSize;
	}


	public final void reset() {
		if (isCommitted()) {
			throw new IllegalStateException("response is already comited");
		}
		
		header = new HttpHeader();
		cookies = new ArrayList<Cookie>();
		status = HttpServletResponse.SC_OK;
		statusText = " OK";
		resetBuffer();
	}

	public final void resetBuffer() {
		if (isCommitted()) {
			throw new IllegalStateException("response is already comited");
		}
		
		outBuffers.clear();
	}


	public void flushBuffer() throws IOException {
		if (isCommitted()) {
			throw new IllegalStateException("response is already comited");
		}
		
		close();
	}
	
	
/*	
	public final void flushBuffer() throws IOException {
		if (!isCommitted) {
			StringBuilder resp = new StringBuilder();
			
			resp.append("HTTP/1.1" + " " + status + " " + statusText + "\r\n");

			if (cookies != null) {
				for (Cookie cookie : cookies) {
					StringBuilder sb = new StringBuilder("Set-Cookie: " + cookie.getName() + "=\"" + cookie.getValue()+ "\"");
					if (cookie.getVersion() != 0) {
						sb.append("; Version=\""+ cookie.getVersion() + "\"");
					}
					if (cookie.getPath() != null) {
						sb.append("; Path="+ cookie.getPath());
					}
					if (cookie.getMaxAge() != -1) {
						sb.append("; Max-Age="+ cookie.getMaxAge());
					}
					resp.append(sb.toString() + "\r\n");	
				}
			}
			resp.append("Server: xsocket (" + getVersion() + ")\r\n");
			resp.append("Transfer-Encoding: chunked\r\n");

			for (Enumeration en = header.getHeaderNames(); en.hasMoreElements(); ) {
				String name = (String) en.nextElement();
				String value = header.getHeader(name);
				resp.append(name + ": " + value + "\r\n");
			}
			
			connection.write(resp.toString(), "US-ASCII");
			connection.flush();
			
			if (LOG.isLoggable(Level.FINE)) {
				LOG.info("return response header " + resp);
			}
			
			
			isCommitted = true;
		}
		
		
		if (!outBuffers.isEmpty()) {
			
			StringBuilder part = new StringBuilder();
			int length = getCurrentBufferSize();
			part.append("\r\n");
			part.append(Integer.toHexString(length) + "\r\n");
			connection.write(part.toString());

			if (LOG.isLoggable(Level.FINE)) {
				StringBuilder resp = new StringBuilder();
				ByteBuffer[] copy = new ByteBuffer[outBuffers.size()];
				for (int i = 0; i < copy.length; i++) {
					copy[i] = outBuffers.get(i).duplicate();
				}
				
				LOG.info("return response body " + resp + DataConverter.toTextAndHexString(copy, "Cp1252", 300));
			}

			for (ByteBuffer buffer : outBuffers) {
				connection.write(buffer);
			}
			outBuffers.clear();
			
			connection.flush();
		}
	}
*/	
	
	private void addBytesToSend(ByteBuffer buffer) throws IOException {
		outBuffers.add(buffer);
	}
	
	private int getCurrentBufferSize() {
		int length = 0;
		for (ByteBuffer buffer : outBuffers) {
			length += buffer.remaining();
		}

		return length;
	}
	

	
	
	public void close() throws IOException {
		if (isOpen) {
			isOpen = false;

			StringBuilder resp = new StringBuilder();
				
			resp.append("HTTP/1.1" + " " + status + " " + statusText + "\r\n");
	
			resp.append("Server: xsocket (" + getVersion() + ")\r\n");
	
			for (Enumeration en = header.getHeaderNames(); en.hasMoreElements(); ) {
				String name = (String) en.nextElement();
				String value = header.getHeader(name);
				resp.append(name + ": " + value + "\r\n");
			}
			
			if (!outBuffers.isEmpty()) {
				int length = getCurrentBufferSize();
				resp.append("Content-Length: " + length + "\r\n");
				resp.append("\r\n");

				connection.write(resp.toString());
				
				for (ByteBuffer buffer : outBuffers) {
					connection.write(buffer);
				}
			} else {
				connection.write(resp.toString());			
			}
			
			connection.write("\r\n\r\n");
			
			outBuffers.clear();			
			
			connection.flush();
		}
	}
	
	
	private String getVersion() {
		Package p = Package.getPackage("org.xsocket");
		if (p != null) {
			return p.getSpecificationTitle() + " " + p.getImplementationVersion();
		} else {
			return "";
		}
	}
	

	public final String getCharacterEncoding() {
		// TODO Auto-generated method stub
		return null;
	}

	public final String getContentType() {
		return header.getContentType();
	}

	public final Locale getLocale() {
		return header.getLocale();
	}


	public final void setCharacterEncoding(String arg0) {
		// TODO Auto-generated method stub

	}

	public final void setContentLength(int arg0) {
		// TODO Auto-generated method stub
	}

	public final void setContentType(String contentType) {
		header.setContentType(contentType);
	}
	

	public final void setLocale(Locale arg0) {
		// TODO Auto-generated method stub

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
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		
		sb.append("HTTP/1.1" + " " + status + " " + statusText + "\r\n");
		Enumeration headerNames = getHeaderNames();
		while (headerNames.hasMoreElements()) {
			String name = (String) headerNames.nextElement();
		    String value = getHeader(name);
		    sb.append("  " + name + " : " + value + "\n");
		}

		
		sb.append("\r\n\r\n");
		if (outBuffers != null) {
			for (ByteBuffer buffer : outBuffers) {
				try {
					sb.append(DataConverter.toString(buffer.duplicate()));
				} catch (UnsupportedEncodingException ignore) { }
			}
		}
		
		return sb.toString();
	}
	
	
	private final class OutputStreamImpl extends OutputStream {

		@Override
		public void write(int b) throws IOException {
			addBytesToSend(ByteBuffer.wrap(new byte[] {(byte) b}));
		}
	
		
		@Override
		public void write(byte[] bytes) throws IOException {
			byte[] b = new byte[bytes.length];
			System.arraycopy(bytes, 0, b, 0, bytes.length);
			addBytesToSend(ByteBuffer.wrap(b));
		}
		
		
		@Override
		public void write(byte[] bytes, int offset, int length) throws IOException {
			byte[] b = new byte[bytes.length];
			System.arraycopy(bytes, 0, b, 0, bytes.length);
			ByteBuffer buffer = ByteBuffer.wrap(b, offset, length);
			addBytesToSend(buffer);
		}
		
		@Override
		public void flush() throws IOException {
			//flushBuffer();
		}
		
		@Override
		public void close() throws IOException {
			HttpResponse.this.close();
		}
	}
}
