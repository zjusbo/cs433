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
import java.io.PrintWriter;
import java.util.List;


import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;

import org.xsocket.stream.INonBlockingConnection;



/**
 * 
 * 
 * @author grro@xsocket.org
 */
final class HttpServletResponseImpl extends HttpResponse implements HttpServletResponse {
 
	private List<Cookie> cookies = null;
	private PrintWriter printWriter = null;
	
	private Context ctx = null;
	private String servletPath = null;
	
	
	public HttpServletResponseImpl(INonBlockingConnection connection) {
		super(connection);
	}
	
	void init(Context ctx, String servletPath) {
		this.ctx = ctx;
		this.servletPath = servletPath;
	}
	
	public String encodeRedirectURL(String url) {
		return url;

	}

	public String encodeRedirectUrl(String url) {
		return url;
	}

	public String encodeURL(String url) {
		return url;
	}

	public String encodeUrl(String url) {
		return url;
	}


	public final void close() throws IOException {
		if (printWriter != null) {
			printWriter.flush();
		}
		
		super.close();
	}

	public final void sendRedirect(String location) throws IOException {
		
		location = toAbsoluteURI(location);
		
		setContentType("text/html");
		setStatus(HttpServletResponse.SC_FOUND);
		addHeader("location", location);
	}

	
	private String toAbsoluteURI(String location) {

		if (!location.toUpperCase().startsWith("HTTP:/")) {
			if (location.startsWith("/")) {
				location = "http://" + ctx.getServerAddress().getHostAddress() + ":" + ctx.getServerPort() + location;
			} else {
				location = "http://" + ctx.getServerAddress().getHostAddress() + ":" + ctx.getServerPort() + ctx.getPath() + "/" + location;
			}
		}
		
		return location;
	}
	

	public ServletOutputStream getOutputStream() throws IOException {
		return new ServletOutputStreamImpl();
	}

	public PrintWriter getWriter() throws IOException {
		if (printWriter == null) {
			printWriter = new PrintWriter(getOutputStream());
		} 
		return printWriter;
	}


	private final class ServletOutputStreamImpl extends ServletOutputStream {
		
		@Override
		public void write(byte[] arg0) throws IOException {
			getOSteam().write(arg0);
		}
		
		@Override
		public void write(int arg0) throws IOException {
			getOSteam().write(arg0);
		}
		
		@Override
		public void write(byte[] arg0, int arg1, int arg2) throws IOException {
			getOSteam().write(arg0, arg1, arg2);
		}
		
		@Override
		public void flush() throws IOException {

		}
		
		@Override
		public void close() throws IOException {
		
		}
		
		public List<Cookie> getCookies() {
			return cookies;
		}	
	}
	
}
