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
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.RequestDispatcher;
import javax.servlet.Servlet;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;




/**
 * 
 * 
 * @author grro@xsocket.org
 */
public final class Context {
	
	private static final Logger LOG = Logger.getLogger(Context.class.getName());
	
	private static int i = 0;
	
	private final List<ServletMapping> servletMappings = new ArrayList<ServletMapping>();
	private final Map<String, ServletHolder> servlets = new HashMap<String, ServletHolder>();

	private final List<FilterMapping> filterMappings = new ArrayList<FilterMapping>();
	private final Map<String, FilterHolder> filters = new HashMap<String, FilterHolder>();

	private List<ServletContextListener> listeners = null;
	
	private final Map<String, Object> attributes = new HashMap<String, Object>();
	
	private Map<String, String> initParams = new HashMap<String, String>();
	
	
	private String servletContainerRoot = null;
	private InetAddress serverAddress = null;
	private int serverPort = 0;
	
	private String contextPath = null;
	
	private Map<String, CacheEntry> chainCache = Collections.synchronizedMap(new LinkedHashMap<String, CacheEntry>(1000, .75F, true));
	
	private SessionManager sessionManager = null;

	
	private ServletContext ctx = null;

	private IContextWorkspace workspace = new IContextWorkspace() {
		
		private final Map<String, File> resources = new HashMap<String, File>();
		
		public String getAbsolutePath(String ressourcePath) {
			File file = resources.get(ressourcePath);
			if (file != null) {
				return file.getAbsolutePath();
			} else {
				return null;
			}
		}
		
		public String getAbsoluteResourcePath(String ressourcePath) {
			File file = resources.get(ressourcePath);
			if (file != null) {
				return file.getAbsolutePath();
			} else {
				return null;
			}
		}
	};
	
	public Context(String contextPath) {
		this(contextPath, new HashMap<String, String>(), new ArrayList<ServletContextListener>(), null);
	}

	
	public Context(String contextPath, Map<String, String> initParams) {
		this(contextPath, initParams, new ArrayList<ServletContextListener>(), null);
	}
	
	public Context(String contextPath, Map<String, String> initParams, IContextWorkspace contextWorkspace) {
		this(contextPath, initParams, new ArrayList<ServletContextListener>(), contextWorkspace);
	}
	
	public Context(String contextPath, Map<String, String> initParams, ServletContextListener listener) {
		this(contextPath, initParams, new ArrayList<ServletContextListener>(), null);
		listeners.add(listener);
	}
	
	
	public Context(String contextPath, Map<String, String> initParams, List<ServletContextListener> listeners, IContextWorkspace contextWorkspace) {
		
		if ((contextPath.length() > 0) && (!contextPath.startsWith("/"))) {
			contextPath = "/" + contextPath;
		}
		
		this.contextPath = contextPath;
		this.initParams = initParams;
		this.listeners = listeners;	
		if (contextWorkspace != null) {
			this.workspace = contextWorkspace;
		}
		
		ctx = new ServletContextImpl();
	}
	
	
	private void addJspServlet() {
		try {
			Map<String, String> initParams = new HashMap<String, String>();
			initParams.put("logVerbosityLevel", "DEBUG");
			initParams.put("fork", "false");
			initParams.put("xpoweredBy", "false");
			initParams.put("xpoweredBy", "false");
			addServlet("jsp", org.apache.jasper.servlet.JspServlet.class, initParams);
			addServletMapping("jsp", "*.jsp");
			addServletMapping("jsp", "*.jspf");
			addServletMapping("jsp", "*.jspx");
			addServletMapping("jsp", "*.xsp");
			addServletMapping("jsp", "*.JSP");
			addServletMapping("jsp", "*.JSPF");
			addServletMapping("jsp", "*.JSPX");
			addServletMapping("jsp", "*.XSP");
		} catch (ServletException e) {
			e.printStackTrace();
		}
 	}
	
	
	
	InetAddress getServerAddress() {
		return serverAddress;
	}
	

	int getServerPort() {
		return serverPort;
	}

	
	void init(SessionManager sessionManager, InetAddress serverAddress, int serverPort) throws ServletException {
		this.sessionManager = sessionManager;
		this.serverAddress = serverAddress;
		this.serverPort = serverPort;
		
		addJspServlet();

		
		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("initializing context " + contextPath);
		}

		for (ServletContextListener listener : listeners) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("initializing listener " + listener);
			}

			listener.contextInitialized(new ServletContextEvent(ctx));
		}
		
		
		Map<Integer, List<ServletHolder>> holders = new HashMap<Integer, List<ServletHolder>>();
		
		for (ServletHolder holder : servlets.values()) {
			Integer loadOnStartUp = holder.getLoadOnStartUp();
			if (loadOnStartUp != null) {
				List<ServletHolder> holderList = holders.get(loadOnStartUp);
				if (holderList == null) {
					holderList = new ArrayList<ServletHolder>();
					holders.put(loadOnStartUp, holderList);
				}
				holderList.add(holder);
			}
		}
		
		for (List<ServletHolder> holderList : holders.values()) {
			for (ServletHolder holder : holderList) {
				holder.init();
			}
		}
	}

	
	
	void destroy() {
		for (ServletContextListener listener : listeners) {
			listener.contextDestroyed(new ServletContextEvent(ctx));
		}
	}
	

	public void addServlet(String servletName, Class servletClass) throws ServletException {
		addServlet(servletName, servletClass, new HashMap<String, String>());
	}

	public void addServlet(Class servletClass, String pattern) throws ServletException {
		addServlet(Integer.toString(i++), servletClass, pattern);
	}

	public void addServlet(Class servletClass, String pattern, Map<String, String> initParams) throws ServletException {
		addServlet(Integer.toString(i++), servletClass, pattern, initParams);
	}
	
	public void addServlet(String servletName, Class servletClass, Map<String, String> initParams) throws ServletException {
		addServlet(servletName, servletClass, (Integer) null, initParams);
	}

	public void addServlet(String servletName, Class servletClass, Integer loadOnStartUp, Map<String, String> initParams) throws ServletException {
		ServletHolder holder = new ServletHolder(servletName, servletClass, ctx, loadOnStartUp, initParams);
		servlets.put(servletName, holder);
	}

	public void addServlet(String servletName, Class servletClass, String pattern) throws ServletException {
		addServlet(servletName, servletClass, pattern, new HashMap<String, String>());
	}

	public void addServlet(String servletName, Class servletClass, String pattern, Map<String, String> initParams) throws ServletException {
		addServlet(servletName, servletClass, initParams);
		addServletMapping(servletName, pattern);
	}

	public void addServlet(String servletName, Class servletClass, String pattern, String... params) throws ServletException {
		addServlet(servletName, servletClass, initParamToMap(params));
		addServletMapping(servletName, pattern);
	}	
	
	
	private Map<String, String> initParamToMap(String... params) {
		Map<String, String> initParam = new HashMap<String, String>();
		for (String param : params) {
			String[] parts = param.split("=");
			if (parts.length == 2) {
				initParam.put(parts[0], parts[1]);
			} else {
				LOG.warning("parameter " + param + " is invalid (usage pattern <key>=<value>");
			}
		}
		return initParam;
	}
	
	public void addServletMapping(String servletname, String pattern) {
		servletMappings.add(new ServletMapping(servletname, pattern));
	}
	
	
	public void addFilter(String filterName, Class filterClass) throws ServletException {
		addFilter(filterName, filterClass, new HashMap<String, String>());
	}
	
	public void addFilter(String filterName, Class filterClass, Map<String, String> initParams) throws ServletException {
		FilterHolder holder = new FilterHolder(filterName, filterClass, ctx, initParams);
		filters.put(filterName, holder);
	}
	
	public void addFilterMapping(String filtername, String pattern, boolean isServletname) {
		filterMappings.add(new FilterMapping(filtername, pattern, isServletname));
	}
	
	
	public void addContextListener(ServletContextListener listener) {
		listeners.add(listener);
	}
	
	


	void handle(HttpServletRequestImpl request, HttpServletResponseImpl response) throws ServletNotFoundException, ServletException, IOException {
		String requestedRessource = null;
		if (contextPath.length() > 0) {
			requestedRessource = request.getRequestURI().substring(contextPath.length(), request.getRequestURI().length());			
		} else {
			requestedRessource = request.getRequestURI();
		}
		
		if (requestedRessource.length() == 0) {
			throw new ServletNotFoundException("no servlet found for empty requestedRessource");
		}
	
		
		// cached chain
		CacheEntry cacheEntry = chainCache.get(requestedRessource);

		if (cacheEntry != null) {
			response.init(this, cacheEntry.getPath());
			request.init(this, cacheEntry.getPath(), sessionManager);
			cacheEntry.getChain().doFilter(request, response);
			return;
			
		}

		
		// non cached chain
		ServletHolder servletHolder = null;
		String path = null;
		for (ServletMapping servletMapping : servletMappings) {
			if (servletMapping.match(requestedRessource)) {
				servletHolder = servlets.get(servletMapping.getServletname());
				path = servletMapping.getPath();
				response.init(this, path);
				request.init(this, path, sessionManager);
				break;
			}
		}
			 
		if (servletHolder != null) {
			FilterChain chain = servletHolder;
			
			for (FilterMapping filterMapping : filterMappings) {
				if (filterMapping.match(servletHolder.getName(), requestedRessource)) {
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("[" + contextPath + "] add filter in chain (" + filters.get(filterMapping.getFiltername()).getClassname() + ")");
					}
					chain = new ChainElement(filters.get(filterMapping.getFiltername()).getFilter(), chain);
				}
			}
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("[" + contextPath + "] calling servlet " + servletHolder.getClassname() + " with " + requestedRessource);
				}
			chain.doFilter(request, response);
				
				
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("add chain for " + requestedRessource + " into cache");
			}
			chainCache.put(requestedRessource, new CacheEntry(chain, path));
				
		} else {
			
			// handle request by resource lookup
			String rp = workspace.getAbsoluteResourcePath(requestedRessource);
			if (rp != null) {
				
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("returning resource " + requestedRessource + " (" + rp + ")");
				}
				
				OutputStream to = response.getOutputStream();
				FileInputStream from = new FileInputStream(rp);
				
				byte[] buffer = new byte[4096];
				int bytesRead;

				while ((bytesRead = from.read(buffer)) != -1) {
					to.write(buffer, 0, bytesRead); 
				}
				
				from.close();
				to.close();
				
				
			} else {
				String msg = "no servlet found for requested resource " + contextPath + requestedRessource;
				ServletNotFoundException se = new ServletNotFoundException(msg);
				LOG.throwing(this.getClass().getName(), "handle", se);
				throw se;
			}
		}
	}
	
	
	
	String getPath() {
		return contextPath;
	}
	
	
	String generateContextSnippet(String host, int port) {
		StringBuilder sb = new StringBuilder();
		sb.append("<b>[" + contextPath + "]</b><br>\r\n");
		sb.append("available servlets:\r\n");
		sb.append("<ul>\r\n");

		String ctxPath = contextPath;
		if (ctxPath.equals("/")) {
			ctxPath = "";
		}
		for (ServletMapping servletMapping : servletMappings) {
			String link = "http://" + host + ":" + port + ctxPath + servletMapping.getPattern();
			sb.append("<li>[" +  servletMapping.getServletname() +  "] <a href=\"" + link + "\"> " + link + "</a> (" + servlets.get(servletMapping.getServletname()).getClassname() + ")</li>");					
		}
		sb.append("</ul>\r\n");


		if (!filterMappings.isEmpty()) {
			sb.append("installed filters:\r\n");
			sb.append("<ul>\r\n");
	
			for (FilterMapping filterMapping : filterMappings) {
				if (filterMapping.isServletName()) {
					sb.append("<li>["  + filterMapping.getFiltername() + "] " + filterMapping.getPattern() + " (" + filters.get(filterMapping.getFiltername()).getClassname() + ") </li>");					
				} else {
					String link = "http://" + host + ":" + port  + contextPath + filterMapping.getPattern();
					sb.append("<li>["  + filterMapping.getFiltername() + "] " + link + " (" + filters.get(filterMapping.getFiltername()).getClassname() + ")</li>");
				}
			}
			sb.append("</ul><br>\r\n");
		}
		
		return sb.toString();
	}
	

	
	String[] getAvailableFilterURIs() {
		List<String> result = new ArrayList<String>();
		
		for (FilterMapping filterMapping : filterMappings) {
			result.add(contextPath + filterMapping.getPattern());
		}
				
		return result.toArray(new String[result.size()]);
	}
	
	
	String getAssignedFilter(String uri) {
		if (uri.startsWith(contextPath)) {
			uri = uri.substring(contextPath.length(), uri.length());
		}
		
		for (FilterMapping filterMapping : filterMappings) {
			if (uri.equals(filterMapping.getPattern())) {
				String filterName = filterMapping.getFiltername();
				return servlets.get(filterName).getClassname() + " [" + filterName + "]";
			}				
		}
		return "<empty>";
	}
	
	@Override
	public int hashCode() {
		return contextPath.hashCode();
	}
	
	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof Context)) {
			return false;
		}
		
		return ((Context) obj).contextPath.equals(this.contextPath);
	}	
	
	
	
	private static final class ChainElement implements FilterChain {
		private Filter filter = null;
		private FilterChain successor = null;
		
		ChainElement(Filter filter, FilterChain successor) {
			this.filter = filter;
			this.successor = successor;
		}
		
		public void doFilter(ServletRequest req, ServletResponse res) throws IOException, ServletException {
			try {
				filter.doFilter(req, res, successor);
			} catch (ServletException se) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("error occured by calling " + filter + ": " + se.toString());
				}
				throw se;

			} catch (IOException ioe) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("error occured by calling  " + filter + ": " + ioe.toString());
				}
				throw ioe;
			}
		}
	}
	
	
	private static final class CacheEntry {
		private FilterChain chain = null;
		private String path = null;
		
		CacheEntry(FilterChain chain, String path) {
			this.chain = chain;
			this.path = path;
		}
		
		public FilterChain getChain() {
			return chain;
		}
		
		public String getPath() {
			return path;
		}
		
	}
	
	private final class ServletContextImpl implements ServletContext {
		public void setAttribute(String name, Object value) {
			attributes.put(name, value);
		}
		
		public Object getAttribute(String name) {
			return attributes.get(name);
		}
		
		public void removeAttribute(String name) {
			attributes.remove(name);
		}
		

		public String getContextPath() {
			return getPath();
		}
		
		public Enumeration getAttributeNames() {
			return Collections.enumeration(attributes.keySet());
		}
		
		public ServletContext getContext(String uripath) {
			return null;
		}
		
		public String getInitParameter(String name) {
			return initParams.get(name);
		}
		
		public Enumeration getInitParameterNames() {
			return Collections.enumeration(initParams.keySet());
		}
		
		public int getMajorVersion() {
			return 2;
		}
		
		public int getMinorVersion() {
			return 5;
		}
		
		public String getMimeType(String file) {
			return null;
		}
		
		public URL getResource(String path) throws MalformedURLException {
			String realPath = getRealPath(path);
			if (realPath != null) {
				File file = new File(realPath);
				return file.toURI().toURL();
			} else {
				return null;
			}
		}
		
		public InputStream getResourceAsStream(String path) {
			try {
				URL url = getResource(path);
				if (url != null) {
					return url.openStream();
				} else {
					return null;
				}
				
			} catch (IOException ioe) {
				return null;
			}
		}
		
		
		public String getRealPath(String path) {
			return workspace.getAbsolutePath(path);
		}
		
		public Set getResourcePaths(String path) {
			return null;
		}
		
		
		public void log(Exception e, String msg) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine(msg + e.toString());
			}
		}
		
		public void log(String msg) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine(msg);
			}
		}
		
		public void log(String msg, Throwable t) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine(msg + t.toString());
			}
		}
		
		public Enumeration getServlets() {
			throw new UnsupportedOperationException("getServlets is not supported");
		}
		

		public Enumeration getServletNames() {
			throw new UnsupportedOperationException("getServletNames is not supported");
		}
		
		public Servlet getServlet(String name) throws ServletException {
			throw new UnsupportedOperationException("getServlet is not supported");

		}
		
		public String getServletContextName() {
			return contextPath;
		}
		
		public String getServerInfo() {
			return "xSocket HttpServletContainer";
		}

		public RequestDispatcher getNamedDispatcher(String arg0) {
			// TODO Auto-generated method stub
			return null;
		}
		
		public RequestDispatcher getRequestDispatcher(String arg0) {
			// TODO Auto-generated method stub
			return null;
		}
	}
}
