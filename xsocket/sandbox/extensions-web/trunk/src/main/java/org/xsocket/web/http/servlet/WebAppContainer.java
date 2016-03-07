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


import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.BufferUnderflowException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Resource;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;


import org.xsocket.ILifeCycle;
import org.xsocket.stream.IConnectHandler;
import org.xsocket.stream.IConnectionScoped;
import org.xsocket.stream.INonBlockingConnection;
import org.xsocket.stream.IDataHandler;
import org.xsocket.stream.IConnection.FlushMode;



/**
 * 
 * 
 * @author grro@xsocket.org
 */
public final class WebAppContainer implements IConnectHandler, IDataHandler, ILifeCycle, IConnectionScoped {
	
	private static final Logger LOG = Logger.getLogger(WebAppContainer.class.getName());	

	private final Map<String, Context> contexts = new HashMap<String, Context>();

	private HttpServletRequestImpl request = new HttpServletRequestImpl();
	
	private final SessionManager sessionManager = new SessionManager();
	
	private Map<String, Context> contextCache = Collections.synchronizedMap(new LinkedHashMap<String, Context>(5000, .75F, true));

	@Resource 
	org.xsocket.stream.IServer srv = null;
	


	
	public WebAppContainer(Context... ctx) throws ServletException {
		for (Context context : ctx) {
			addContext(context);
		}
	}
	
	public void install(String context, War war) throws IOException {
		war.install(this, context);
	}
	
	public void addContext(Context ctx) throws ServletException {
		contexts.put(ctx.getPath(), ctx);
	}
		
	public void onInit() {
		for (Context context : contexts.values()) {
			try {
				context.init(sessionManager, srv.getLocalAddress(), srv.getLocalPort());
			} catch (ServletException se) {
				LOG.warning("couldn't initialize context " + context.getPath() + ". Error: " + se.toString());
			}
		}
	}
	
	public void onDestroy() {
		
	}
	
	public boolean onConnect(INonBlockingConnection connection) throws IOException {
		connection.setAutoflush(false);
		connection.setFlushmode(FlushMode.ASYNC);
		return true;
	}
	
	
	
	public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
		
		// parse http message  
		request.parseData(connection);

		// message received -> handle it
		try {
			HttpServletResponseImpl response = new HttpServletResponseImpl(connection);
			
			onHttpRequest(request, response);
			response.close();
			
		// message handled ->create new empty request object 	
		} finally {
			request = new HttpServletRequestImpl();
			onData(connection);
		}
		

		return true;
	}
	
	
	private void onHttpRequest(HttpServletRequestImpl servletRequest, HttpServletResponseImpl servletResponse) throws IOException {
	
		String requestedRessource = servletRequest.getRequestURI();
		
		Context context = contextCache.get(requestedRessource); 
		
		if (context != null) {
			call(context, requestedRessource, servletRequest, servletResponse);
			
		} else {
			for (String contextPath : contexts.keySet()) {
				if (requestedRessource.startsWith(contextPath)) {
					context = contexts.get(contextPath);
					call(context, requestedRessource, servletRequest, servletResponse);
					
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("add context for " + requestedRessource + " into cache");
					}
					contextCache.put(requestedRessource, context);
					return; 
				}
			}
			
			
			
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("no servlet found for url '" + requestedRessource + "'");
			}
			servletResponse.sendError(HttpServletResponse.SC_NOT_FOUND, "requestedURI '" + requestedRessource + "' not found <br><br>" + generateServletListSnippet(servletRequest.getLocalName(), servletRequest.getLocalPort()));
		}
	}


	private void call(Context ctx, String requestedRessource, HttpServletRequestImpl servletRequest, HttpServletResponseImpl servletResponse) throws IOException {
		try {
			ctx.handle(servletRequest, servletResponse);
			servletResponse.close();
			return;
			
		} catch (ServletNotFoundException nsfe) {
			servletResponse.sendError(HttpServletResponse.SC_NOT_FOUND, "requestedURI '" + requestedRessource + "' not found <br><br>" + generateServletListSnippet(servletRequest.getLocalName(), servletRequest.getLocalPort()));
			
		} catch (ServletException se) {
			IOException ioe = new IOException("Error occured by handling request. Reason: " + se.toString()
					                          + " Request: " + servletRequest.toString());
			LOG.throwing(this.getClass().getName(), "handle(HttpServletRequestImpl, HttpServletResponseImpl)", ioe);
			throw ioe;
		}
	}
	
	private String generateServletListSnippet(String host, int port) {
		StringBuilder sb = new StringBuilder();
		for (Context context : contexts.values()) {
			sb.append(context.generateContextSnippet(host, port));
		}
		sb.append("</ul>\r\n");

		
		return sb.toString();
	}
	
	@Override
	public Object clone() throws CloneNotSupportedException {
		WebAppContainer copy = (WebAppContainer) super.clone();
		copy.request = new HttpServletRequestImpl();
		return copy;
	}
}