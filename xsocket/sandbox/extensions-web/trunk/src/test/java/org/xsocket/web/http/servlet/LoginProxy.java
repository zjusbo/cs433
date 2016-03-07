package org.xsocket.web.http.servlet;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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


public final class LoginProxy extends HttpServlet {
	
	private static final Logger LOG = Logger.getLogger(LoginProxy.class.getName());
	
	public static final String LOGIN_SERVICE_URL = "loginServiceUrl";
	
	private String loginServiceUrl = null;
	

	@Override
	public void init(ServletConfig conf) throws ServletException {
		
		for (Enumeration en = conf.getInitParameterNames(); en.hasMoreElements(); ) {
			String name = (String) en.nextElement();
			
			if (name.equals(LOGIN_SERVICE_URL)) {
				loginServiceUrl = conf.getInitParameter(LOGIN_SERVICE_URL);
				
			}  else {
				LOG.warning("unknown servlet init parameter " + name); 
			}
		}
		
	}
	
	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
		handle(req, res);
	}
	
	
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
		handle(req, res);
	}
		
	
	private void handle(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
		
		String username = req.getParameter("username");
		if (username == null) {
			res.sendError(400, "parameter username is missing");
			return;
		}
		
		String password = req.getParameter("password");
		if (username == null) {
			res.sendError(400, "parameter password is missing");
			return;
		}

		
		try {		
			String url = loginServiceUrl + "?username=" + username + "&password=" + password;
			LOG.info("calling " + url);
			GetMethod method = new GetMethod(url);
			try {
				method.setFollowRedirects(true);
				int statusCode = new HttpClient().executeMethod(method);
				forwardResponse(req, res, statusCode, method);
				
			} finally {
				method.releaseConnection();
			}
		} catch (Exception e) {
			res.sendError(400, e.getMessage());
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
}
