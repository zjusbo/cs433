package org.xsocket.web.http.servlet;


final class ServletMapping {

	private String servletname = null;
	private String path = null;
	private String pattern = null; 
	private boolean isWildcardPath = false;
	private boolean isWildcardPathExt = false;

	 
	ServletMapping(String servletname, String pattern) {
		this.servletname = servletname;
		this.pattern = pattern;
		this.path = pattern;
		
		if (pattern.endsWith("/*")) {
			isWildcardPath = true;
			path = pattern.substring(0, pattern.indexOf("/*"));
			
		} else if (pattern.startsWith("*")) {
			path = pattern.substring(1, pattern.length());
			isWildcardPathExt = true;

		} else {
			isWildcardPath = false;
			path = pattern;
		}
	}

	
	boolean match(String requestedRessource) {
		
		if (isWildcardPath) {
			return requestedRessource.startsWith(path) || requestedRessource.equals(path);
			
		} else if (isWildcardPathExt) {
			return requestedRessource.endsWith(path);
			
		} else {
			return requestedRessource.equals(path);
		}
	}
	
	String getServletname() {
		return servletname;
	}
	
	String getPath() {
		if (isWildcardPathExt) {
			return "";
		} else {
			return path;
		}
	}
	
	String getPattern() {
		return pattern;
	}
}
