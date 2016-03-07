package org.xsocket.web.http;




final class RequestHeader extends Header implements IRequestHeader {
	
	private String method = null;
	private String requestURI = null;
	private String protocol = null;
	

	public RequestHeader(String method, String requestURI) {
		this(method, requestURI, "HTTP/1.1");
	}
	
	public RequestHeader(String method, String requestURI, String protocol) {
		super(method + " " + requestURI + " " + protocol);
		this.method = method;
		this.requestURI = requestURI;
		this.protocol = protocol;
	}
	
	
	RequestHeader(String firstLine, String... headerLines) {
		super(firstLine, headerLines);
		
		String[] parts = getFirstLine().split(" ");
		method = parts[0];
		requestURI = parts[1];
		if (parts.length > 2) {
			protocol = parts[2];
		} else {
			protocol = "HTTP/1.0";
		}
	}
	

	public final String getMethod() {
		return method;
	}
		
		
	public String getRequestURI() {
		return requestURI; 
	}	

	public String getProtocol() {
		return protocol;
	}
}	