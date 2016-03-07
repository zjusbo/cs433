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
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.FilterChain;
import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;




/**
 * 
 * 
 * @author grro@xsocket.org
 */
final class ServletHolder implements FilterChain {
	
	private static final Logger LOG = Logger.getLogger(ServletHolder.class.getName()); 

	private String name = null;
	private Class clazz = null;
	private ServletContext ctx = null; 
	private Integer loadOnStartUp = null;
	private Map<String, String> initParams = null;

	private Servlet servlet = null;
	private boolean isInitialized = false;
	
	public ServletHolder(String name, Class clazz, ServletContext ctx, Integer loadOnStartUp, Map<String, String> initParams) throws ServletException {	
		this.name = name;
		this.clazz = clazz;
		this.ctx = ctx;
		this.loadOnStartUp = loadOnStartUp;
		this.initParams = initParams;
	}
	
	Integer getLoadOnStartUp() {
		return loadOnStartUp;
	}
	
	
	void init() throws ServletException {
		if (!isInitialized) {
			isInitialized = true;

			long start = System.currentTimeMillis();
			
			servlet = newServlet(clazz);
			
			if (LOG.isLoggable(Level.INFO)) {
				StringBuilder params = new StringBuilder();
				for (String name : initParams.keySet()) {
					params.append(name + "=" + initParams.get(name) + ", ");
				}
				LOG.info("initializing servlet " + name + " (params: " + params + ")");
			}
	
			servlet.init(new ServletConfig() {
				public String getInitParameter(String name) {
					return initParams.get(name);
				}
				
				public Enumeration getInitParameterNames() {
					return Collections.enumeration(initParams.keySet());
				}
				
				public ServletContext getServletContext() {
					return ctx;
				}
				public String getServletName() {
					return name;
				}
			});
			
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("servlet " + name + " has been created and initialized (elapsed init time: " + (System.currentTimeMillis() - start) + " millis)");
			}
		}
	}
	
	private static Servlet newServlet(Class clazz) throws ServletException {
		try {
			return (Servlet) clazz.newInstance();
		} catch (Exception e) {
			ServletException se = new ServletException("couldn't create servlet " + clazz.getName() + ". reason: " + e.toString(), e);
			LOG.throwing(ServletHolder.class.getName(), "newServlet", se);
			throw se;
		}
	}
	

	public void doFilter(ServletRequest request, ServletResponse response) throws IOException, ServletException {
		if (!isInitialized) {
			init();
		}
		
		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("calling service method of " + servlet.getClass().getSimpleName() + "#" + servlet.hashCode());
			LOG.fine("request: " + request.toString());
		}
		servlet.service(request, response);	
		
		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("returning response: " + response.toString());
		}
	}
	
	String getClassname() {
		return clazz.getName();
	}
	
	String getName() {
		return name;
	}
	
	@Override
	public String toString() {
		return "[" + getName() + "] " + getClassname();
	}
}
