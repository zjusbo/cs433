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


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.nio.BufferUnderflowException;
import java.security.Principal;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.xsocket.stream.INonBlockingConnection;



/**
 * 
 * 
 * @author grro@xsocket.org
 */
final class HttpServletRequestImpl extends HttpRequest implements HttpServletRequest {

	private Context ctx = null;
	private String servletPath = null;

	private SessionManager sessionManager = null;
	
	private final Map<String, Object> attributes = new HashMap<String, Object>();


	void init(Context ctx, String servletPath, SessionManager sessionManager) {
		this.ctx = ctx;
		this.servletPath = servletPath;
		this.sessionManager = sessionManager;
	}
	
	
	public String getAuthType() {
		// TODO Auto-generated method stub
		return null;
	}

	public String getContextPath() {
		return ctx.getPath();
	}


	public String getPathInfo() {
		String uri = getRequestURI();
		String pathInfo = uri.substring(getContextPath().length() + getServletPath().length(), uri.length());
		if ((pathInfo.equals("/*")) || (pathInfo.length() == 0)) {
			return null;
		} else {
			return pathInfo;
		}
	}

	public String getPathTranslated() {
		// TODO Auto-generated method stub
		return null;
	}


	public String getCharacterEncoding() {
		// TODO Auto-generated method stub
		return null;
	}
	
	public String getRemoteUser() {
		// TODO Auto-generated method stub
		return null;
	}

	

	public String getRequestedSessionId() {
		// TODO Auto-generated method stub
		return null;
	}

	public String getServletPath() {
		return servletPath;
	}

	public HttpSession getSession() {
		return sessionManager.newSession();
	}

	public HttpSession getSession(boolean arg0) {
		return sessionManager.newSession();
	}

	public Principal getUserPrincipal() {
		// TODO Auto-generated method stub
		return null;
	}

	public boolean isRequestedSessionIdFromCookie() {
		// TODO Auto-generated method stub
		return false;
	}

	public boolean isRequestedSessionIdFromURL() {
		// TODO Auto-generated method stub
		return false;
	}

	public boolean isRequestedSessionIdFromUrl() {
		// TODO Auto-generated method stub
		return false;
	}

	public boolean isRequestedSessionIdValid() {
		// TODO Auto-generated method stub
		return false;
	}

	public boolean isUserInRole(String arg0) {
		// TODO Auto-generated method stub
		return false;
	}


	public ServletInputStream getInputStream() throws IOException {
		return new ServletInputStreamImpl();
	}


	public BufferedReader getReader() throws IOException {
		return new BufferedReader(new InputStreamReader(getIStream()));
	}

	public String getRealPath(String arg0) {
		// TODO Auto-generated method stub
		return null;
	}

	public RequestDispatcher getRequestDispatcher(String arg0) {
		// TODO Auto-generated method stub
		return null;
	}

	public String getServerName() {
		return getLocalName();
	}


	public boolean isSecure() {
		// TODO Auto-generated method stub
		return false;
	}

	
	public Object getAttribute(String name) {
		return attributes.get(name);
	}

	public void removeAttribute(String name) {
		attributes.remove(name);
	}

	public void setAttribute(String name, Object value) {
		attributes.put(name, value);
	}

	
	public Enumeration getAttributeNames() {
		return Collections.enumeration(attributes.keySet());
	}

	
	public void setCharacterEncoding(String arg0)
			throws UnsupportedEncodingException {
		// TODO Auto-generated method stub

	}
	
	@Override
	public String toString() {
		return super.toString();
	}
	

	private final class ServletInputStreamImpl extends ServletInputStream {		
		
		@Override
		public int read() throws IOException {
			return getIStream().read();
		}
		
		@Override
		public int read(byte[] arg0) throws IOException {
			return getIStream().read(arg0);
		}
		
		@Override
		public int read(byte[] arg0, int arg1, int arg2) throws IOException {
			return getIStream().read(arg0, arg1, arg2);
		}
		
		@Override
		public int available() throws IOException {
			return getIStream().available();
		} 
		
		@Override
		public synchronized void mark(int arg0)  {
			getIStream().mark(arg0);
		}
		
		@Override
		public boolean markSupported() {
			return getIStream().markSupported();
		}
		
		@Override
		public synchronized void reset() throws IOException {
			getIStream().reset();
		}
		
		@Override
		public long skip(long arg0) throws IOException {
			return getIStream().skip(arg0);
		}	
	}
	
}
