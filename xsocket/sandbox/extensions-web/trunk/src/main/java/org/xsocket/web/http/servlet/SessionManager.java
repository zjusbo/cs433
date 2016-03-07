package org.xsocket.web.http.servlet;

import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionContext;



final class SessionManager {
	
	private final Map<String , HttpSessionImpl> sessions = new HashMap<String, HttpSessionImpl>();
	
	
	HttpSession newSession() {
		return new HttpSessionImpl(); 
	}

	
	private final class HttpSessionImpl implements HttpSession {

		private final String id = UUID.randomUUID().toString();
		private long creationTime = System.currentTimeMillis();
		private long lastTimeAccessed = System.currentTimeMillis();
		private int maxInactiveInterval = 1000;
		private ServletContext servletContext = null;
		
		private final Map<String, Object> attributes = new HashMap<String, Object>();
		private final Map<String, Object> values = new HashMap<String, Object>();
		
		
		void init(ServletContext servletContext) {
			this.servletContext = servletContext;
		}
		
		public Object getAttribute(String name) {
			return attributes.get(name);
		}
		
		public Enumeration getAttributeNames() {
			Set<String> names = attributes.keySet();
			return Collections.enumeration(names);
		}

		public void removeAttribute(String name) {
			attributes.remove(name);
		}

		
		public void setAttribute(String name, Object value) {
			attributes.put(name, value);
		}

		public void putValue(String name, Object value) {
			values.put(name, value);
		}
		
		
		public void removeValue(String name) {
			values.remove(name);
		}		

		
		public long getCreationTime() {
			return creationTime;
		}
		
		public String getId() {
			return id;
		}
		
		public long getLastAccessedTime() {
			return lastTimeAccessed;
		}

		public void setMaxInactiveInterval(int maxInactiveInterval) {
			this.maxInactiveInterval = maxInactiveInterval;
		}
		
		public int getMaxInactiveInterval() {
			return maxInactiveInterval;
		}
		
		public ServletContext getServletContext() {
			return servletContext;
		}
		
		public HttpSessionContext getSessionContext() {
			return null;
		}
		
		public Object getValue(String arg0) {
			// TODO Auto-generated method stub
			return null;
		}
		
		public String[] getValueNames() {
			// TODO Auto-generated method stub
			return null;
		}
		
		public void invalidate() {
			// TODO Auto-generated method stub
			
		}
		
		public boolean isNew() {
			return false;
		}		
	}
}
