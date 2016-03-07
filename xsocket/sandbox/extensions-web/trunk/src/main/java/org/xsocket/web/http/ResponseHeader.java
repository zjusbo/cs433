package org.xsocket.web.http;




final class ResponseHeader extends Header implements IResponseHeader {
	
	
	private int statusCode = 0;
	private String reason = null;
	private String protocol = null;


	public ResponseHeader(int statusCode) {
		this(statusCode, getReason(statusCode));
	}

	
	public ResponseHeader(int statusCode, String reason) {
		this("HTTP/1.1", statusCode, reason);
	}
	
	ResponseHeader(String protocol, int statusCode, String reason) {
		super(protocol + " " + statusCode + " " + reason);
		
		this.protocol = protocol;
		this.statusCode = statusCode;
		this.reason = reason;
	}

	
	
	ResponseHeader(String firstLine, String... headerLines) {
		super(firstLine, headerLines);

		String[] parts = getFirstLine().split(" ");
		protocol = parts[0];
		statusCode = Integer.parseInt(parts[1]);
		if (parts.length > 2) {
			reason = parts[2];
		} else {
			reason = "HTTP/1.0";
		}
	}
	 
	

	
	public int getStatus() {
		return statusCode;
	}
	
	public String getReason() {
		return reason;
	}
	
	public String getProtocol() {
		return protocol;
	}



	private static String getReason(int statusCode) {
		switch (statusCode) {
		case 200:
			return "OK";

		case 404:
			return "Not found";
			
		default:
			return " ";
		}

	}
}	