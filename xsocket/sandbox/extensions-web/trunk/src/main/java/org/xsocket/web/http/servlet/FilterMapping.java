package org.xsocket.web.http.servlet;


final class FilterMapping {

	private String filtername = null;
	private String pattern = null;
	private String path = null;
	private boolean isServletname = false;
	private boolean isWildcardPath = false;

	 
	FilterMapping(String filtername, String pattern,  boolean isServletname) {
		this.filtername = filtername;
		this.pattern = pattern;
		this.isServletname = isServletname;
		this.path = pattern;
		
		if (!isServletname) {
			if (pattern.endsWith("/*")) {
				isWildcardPath = true;
				path = pattern.substring(0, pattern.indexOf("/*"));
			} else {
				isWildcardPath = false;
				path = pattern;
			}
		} 
	}

	
	boolean match(String servletname, String requestedRessource) {
		
		// servletname filter
		if (isServletname) {
			return servletname.equals(path);
			
		// url pattern filter
		} else {
			if (isWildcardPath) {
				return requestedRessource.startsWith(path);
			} else {
				return requestedRessource.equals(path);
			}
		}			
	}
	
	String getFiltername() {
		return filtername;
	}
	
	String getPattern() {
		return pattern;
	}
	
	boolean isServletName() {
		return isServletname;
	}
}
