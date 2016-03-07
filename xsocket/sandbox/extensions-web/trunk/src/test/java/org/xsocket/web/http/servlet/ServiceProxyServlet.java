package org.xsocket.web.http.servlet;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;



public final class ServiceProxyServlet extends HttpServlet {
	
	private static final long serialVersionUID = -8489546083450812638L;

	private static final Logger LOG = Logger.getLogger(ServiceProxyServlet.class.getName());
	
	public static final String TARGET_URL_KEY = "serviceUrl";

	private String targetUrl = null;
	

	@Override
	public void init(ServletConfig conf) throws ServletException {
		
		for (Enumeration en = conf.getInitParameterNames(); en.hasMoreElements(); ) {
			String name = (String) en.nextElement();
			
			if (name.equals(TARGET_URL_KEY)) {
				targetUrl = conf.getInitParameter(TARGET_URL_KEY);
				
			} else {
				LOG.warning("unknown servlet init parameter " + name); 
			}
		}
	}
	
	
	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
		try {
			String path = computePath(req);
			if (LOG.isLoggable(Level.INFO)) {
				LOG.info("forwarding POST request to " + path);
			}
	
			PostMethod method = new PostMethod(path);
			try {
				addHeaders(req, method);
				method.setRequestBody(req.getInputStream());
				int statusCode = new HttpClient().executeMethod(method);
				forwardResponse(req, res, statusCode, method);

				if (LOG.isLoggable(Level.INFO)) {
//					LOG.info("forwarding POST response " + res);
				}

			} finally {
				method.releaseConnection();
			}
		} catch (Exception e) {
			res.sendError(400, e.getMessage());
		}
	}
	
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
		try {		
			String path = computePath(req);
			if (LOG.isLoggable(Level.INFO)) {
				LOG.info("forwarding GET request to " + path);
			}
			GetMethod method = new GetMethod(path);
			method.setFollowRedirects(false);
			try {
				addHeaders(req, method);
				int statusCode = new HttpClient().executeMethod(method);
				
				if (LOG.isLoggable(Level.INFO)) {
					LOG.info("forwarding GET response (status code: " + statusCode + ")");
				}
				forwardResponse(req, res, statusCode, method);
				
			} finally {
				method.releaseConnection();
			}
		} catch (Exception e) {
			res.sendError(400, e.getMessage());
		}
	}
	
	private String computePath(HttpServletRequest req) {
		String uri = req.getRequestURI();
		uri = uri.substring(req.getServletPath().length() + req.getContextPath().length(), uri.length());
		
		String queryString = req.getQueryString();
		
		String path = "";
		if (targetUrl != null) {
			path = path + targetUrl;
			path = path + uri;
			
		} else {
			throw new RuntimeException("configuration error. no target defined");
		}
		
		
		if (queryString  != null) {
			if (queryString.trim().length() > 1) {
				path += "?" + queryString ;
			}
		}

		return path; 
	}
	
	private void addHeaders(HttpServletRequest req, HttpMethod method) throws ServletException, IOException {
	
		for (Enumeration en = req.getHeaderNames(); en.hasMoreElements(); ) {
			String name = (String) en.nextElement(); 
			for (Enumeration en2 = req.getHeaders(name); en2.hasMoreElements(); ) {
				String value = (String) en2.nextElement();
				method.addRequestHeader(name, value);
			}
		}
	}
		
	
	private void forwardResponse(HttpServletRequest req, HttpServletResponse res, int statusCode, HttpMethod method) throws HttpException, IOException {
		res.setStatus(statusCode);


		for (Header header : method.getResponseHeaders()) {
			String name = header.getName();
			String value = header.getValue();
		
			res.addHeader(name, value);
		}
		

		if (statusCode != HttpServletResponse.SC_FOUND) {
			Header contentTypeHeader = method.getResponseHeader("content-type");
			if (contentTypeHeader != null) {
				if (contentTypeHeader.getValue().toUpperCase().equals("TEXT/HTML")) {
					String body = method.getResponseBodyAsString();
					
					byte[] bodyBytes = body.getBytes();
					OutputStream to = res.getOutputStream();
					to.write(bodyBytes);
					to.close();
					return; 
				}
			}
			
				
			InputStream from = method.getResponseBodyAsStream();
			OutputStream to  = res.getOutputStream();
				
			byte[] buffer = new byte[4096];
			int bytesRead;
	
			while ((bytesRead = from.read(buffer)) != -1) {
				to.write(buffer, 0, bytesRead);
			}
			from.close();
			to.close();
		}
	}
	

	
	boolean isSameHostAddress(String host, String otherHost) throws UnknownHostException {
		return InetAddress.getByName(host).getHostAddress().equals(InetAddress.getByName(otherHost).getHostAddress());
	}
}
