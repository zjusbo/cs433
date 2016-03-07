package org.xsocket.web.http.servlet;



import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.junit.Assert;
import org.junit.Test;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.servlet.HashSessionManager;
import org.mortbay.jetty.servlet.SessionHandler;
import org.xsocket.stream.IServer;
import org.xsocket.web.http.QAUtil;


public class CompareTest {

	
	
	
	@Test public void testSession() throws Exception {
		int jettyServerPort = 9089; 
		
		// start jetty server
		Server jettyServer = new Server(jettyServerPort);
		org.mortbay.jetty.servlet.Context rootCtx = new org.mortbay.jetty.servlet.Context(jettyServer, "");
		rootCtx.setSessionHandler(new SessionHandler(new HashSessionManager()));
		
		org.mortbay.jetty.servlet.ServletHolder servletHolder = new org.mortbay.jetty.servlet.ServletHolder(new SessionServlet());
		rootCtx.addServlet(servletHolder, "/test/*");
		
		jettyServer.start();
		
		
		HttpClient httpClient = new HttpClient();

		GetMethod jettyMethod = new GetMethod("http://" + "127.0.0.1" + ":" + jettyServerPort + "/test/rtrt");
		int jettyGetStatusCode = httpClient.executeMethod(jettyMethod);
		
		Assert.assertEquals("1", jettyMethod.getResponseBodyAsString().trim());
		jettyMethod.releaseConnection();


		jettyMethod = new GetMethod("http://" + "127.0.0.1" + ":" + jettyServerPort + "/test/rtrt");
		jettyGetStatusCode = httpClient.executeMethod(jettyMethod);
		
		Assert.assertEquals("2", jettyMethod.getResponseBodyAsString().trim());
		jettyMethod.releaseConnection();

		
		jettyServer.stop();
	}
		

	
	
	
	@Test public void testRootContext() throws Exception {
		


		int jettyServerPort = 9085; 
		
		// start jetty server
		Server jettyServer = new Server(jettyServerPort);
		org.mortbay.jetty.servlet.Context rootCtx = new org.mortbay.jetty.servlet.Context(jettyServer, "");
		org.mortbay.jetty.servlet.ServletHolder servletHolder = new org.mortbay.jetty.servlet.ServletHolder(new CompareServlet());
		rootCtx.addServlet(servletHolder, "/test/*");
		
		
		// start xsocket server
		WebAppContainer webContainer = new WebAppContainer();
		Context rootCtx2 = new Context("");
		rootCtx2.addServlet("CompareServlet", CompareServlet.class, "/test/*");
		webContainer.addContext(rootCtx2);
		IServer xSocketServer = new org.xsocket.stream.Server(0, webContainer);
		
		
		compareCases(jettyServer, xSocketServer, "test", jettyServerPort);
		compareCases(jettyServer, xSocketServer, "", jettyServerPort);

		xSocketServer.close();
	}

	
	@Test public void testSubContext() throws Exception {

		// start jetty server
		Server jettyServer = new Server(8091);
		org.mortbay.jetty.servlet.Context rootCtx = new org.mortbay.jetty.servlet.Context(jettyServer, "ctx");
		org.mortbay.jetty.servlet.ServletHolder servletHolder = new org.mortbay.jetty.servlet.ServletHolder(new CompareServlet());
		rootCtx.addServlet(servletHolder, "/test/*");
		
		
		// start xsocket server
		WebAppContainer webContainer = new WebAppContainer();
		Context rootCtx2 = new Context("ctx");
		rootCtx2.addServlet("CompareServlet", CompareServlet.class, "/test/*");
		webContainer.addContext(rootCtx2);
		IServer xSocketServer = new org.xsocket.stream.Server(0, webContainer);
		
		
		compareCases(jettyServer, xSocketServer, "test", 8091);

		xSocketServer.close();
	}

	
	@Test public void testRedirect() throws Exception {

		int jettyServerPort = 9385; 
		
		// start jetty server
		Server jettyServer = new Server(jettyServerPort);
		org.mortbay.jetty.servlet.Context rootCtx = new org.mortbay.jetty.servlet.Context(jettyServer, "");
		org.mortbay.jetty.servlet.ServletHolder servletHolder = new org.mortbay.jetty.servlet.ServletHolder(new RedirectServlet());
		rootCtx.addServlet(servletHolder, "/redirect/*");
		
		
		// start xsocket server
		WebAppContainer webContainer = new WebAppContainer();
		Context rootCtx2 = new Context("");
		rootCtx2.addServlet("RedirectServlet",RedirectServlet.class, "/redirect/*");
		webContainer.addContext(rootCtx2);
		IServer xSocketServer = new org.xsocket.stream.Server(0, webContainer);
		

		jettyServer.start();
		
		Thread t = new Thread(xSocketServer);
		t.start();

		do {
			QAUtil.sleep(100);
		} while (!jettyServer.isStarted());
		
		do {
			QAUtil.sleep(100);
		} while (!xSocketServer.isOpen());
		
		
		HttpClient httpClient = new HttpClient();

		// compare get
		GetMethod jettyMethod = new GetMethod("http://" + "127.0.0.1" + ":" + jettyServerPort + "/redirect/test");
		jettyMethod.setFollowRedirects(false);
		
		GetMethod xSocketMethod = new GetMethod("http://" + xSocketServer.getLocalAddress().getHostName() + ":" + xSocketServer.getLocalPort() + "/redirect/test");
		xSocketMethod.setFollowRedirects(false);
		
		int xSocketGetStatusCode = httpClient.executeMethod(xSocketMethod);
		xSocketMethod.releaseConnection();

		int jettyGetStatusCode = httpClient.executeMethod(jettyMethod);
		jettyMethod.releaseConnection();
		
		Assert.assertEquals(jettyGetStatusCode, xSocketGetStatusCode);
		

		
		// compare post
		PostMethod jettyPostMethod = new PostMethod("http://" + "127.0.0.1" + ":" + jettyServerPort + "/redirect/test");

		PostMethod xSocketPostMethod = new PostMethod("http://" + xSocketServer.getLocalAddress().getHostName() + ":" + xSocketServer.getLocalPort() + "/redirect/test");

		int xSocketPostStatusCode = httpClient.executeMethod(xSocketPostMethod);
		xSocketPostMethod.releaseConnection();

		int jettyPostStatusCode = httpClient.executeMethod(jettyPostMethod);
		jettyPostMethod.releaseConnection();
	
		Assert.assertEquals(jettyPostStatusCode, xSocketPostStatusCode);

		xSocketServer.close();
	}
	
	
	
	@Test public void testPost() throws Exception {

		int jettyServerPort = 9585; 
		
		// start jetty server
		Server jettyServer = new Server(jettyServerPort);
		org.mortbay.jetty.servlet.Context rootCtx = new org.mortbay.jetty.servlet.Context(jettyServer, "");
		org.mortbay.jetty.servlet.ServletHolder servletHolder = new org.mortbay.jetty.servlet.ServletHolder(new PostServlet());
		rootCtx.addServlet(servletHolder, "/post/*");
		
		
		// start xsocket server
		WebAppContainer webContainer = new WebAppContainer();
		Context rootCtx2 = new Context("");
		rootCtx2.addServlet("PostServlet", PostServlet.class, "/post/*");
		webContainer.addContext(rootCtx2);
		IServer xSocketServer = new org.xsocket.stream.Server(0, webContainer);
		

		jettyServer.start();
		
		Thread t = new Thread(xSocketServer);
		t.start();

		do {
			QAUtil.sleep(100);
		} while (!jettyServer.isStarted());
		
		do {
			QAUtil.sleep(100);
		} while (!xSocketServer.isOpen());
		
		
		HttpClient httpClient = new HttpClient();

		// compare post
		PostMethod jettyPostMethod = new PostMethod("http://" + "127.0.0.1" + ":" + jettyServerPort + "/post/test");
		jettyPostMethod.addParameter("param1", "value1");

		PostMethod xSocketPostMethod = new PostMethod("http://" + xSocketServer.getLocalAddress().getHostName() + ":" + xSocketServer.getLocalPort() + "/post/test");
		xSocketPostMethod.addParameter("param1", "value1");

		int xSocketPostStatusCode = httpClient.executeMethod(xSocketPostMethod);
		String xSocketPostResponse = xSocketPostMethod.getResponseBodyAsString();
		xSocketPostMethod.releaseConnection();

		int jettyPostStatusCode = httpClient.executeMethod(jettyPostMethod);
		String jettyPostResponse = jettyPostMethod.getResponseBodyAsString();
		jettyPostMethod.releaseConnection();
	
		Assert.assertEquals(jettyPostStatusCode, xSocketPostStatusCode);

		xSocketServer.close();
	}
	
	private void compareCases(Server jettyServer, IServer xSocketServer, String basepath,int portJetty) throws Exception {

		compare(jettyServer, xSocketServer, basepath, portJetty);
		compare(jettyServer, xSocketServer, basepath + "/test2", portJetty);
		compare(jettyServer, xSocketServer, basepath + "/", portJetty);
		compare(jettyServer, xSocketServer, basepath + "/*", portJetty);	
		compare(jettyServer, xSocketServer, basepath + "/test2/", portJetty);
		compare(jettyServer, xSocketServer, basepath + "/test2/*", portJetty);
		compare(jettyServer, xSocketServer, basepath + "/test2/test3", portJetty);
		compare(jettyServer, xSocketServer, basepath + "/test2/test3/", portJetty);

		compare(jettyServer, xSocketServer, basepath + "?param1=value1", portJetty);
		compare(jettyServer, xSocketServer, basepath + "?param1=value1&param2=value2", portJetty);
		compare(jettyServer, xSocketServer, basepath + "/?param1=value1", portJetty);
		compare(jettyServer, xSocketServer, basepath + "/*?param1=value1", portJetty);
		compare(jettyServer, xSocketServer, basepath + "/test2?param1=value1", portJetty);
		compare(jettyServer, xSocketServer, basepath + "/test2/?param1=value1", portJetty);
		compare(jettyServer, xSocketServer, basepath + "/test2/test3?param1=value1", portJetty);
		
		compare(jettyServer, xSocketServer, basepath + "/test2/test3?param1=value1&url=" + URLEncoder.encode("http://www.web.de?p1=v1&p2=v2", "UTF8"), portJetty);
	}
	
	
	private void compare(Server jettyServer, IServer xSocketServer, String path, int portJetty) throws Exception {
		
		jettyServer.start();
		
		Thread t = new Thread(xSocketServer);
		t.start();

		do {
			QAUtil.sleep(100);
		} while (!jettyServer.isStarted());
		
		do {
			QAUtil.sleep(100);
		} while (!xSocketServer.isOpen());
		
		
		HttpClient httpClient = new HttpClient();

		// compare get
		GetMethod jettyMethod = new GetMethod("http://" + "127.0.0.1" + ":" + portJetty + "/" + path);
		GetMethod xSocketMethod = new GetMethod("http://" + xSocketServer.getLocalAddress().getHostName() + ":" + xSocketServer.getLocalPort() + "/" + path);
		
		int xSocketGetStatusCode = httpClient.executeMethod(xSocketMethod);
		String xsocketResponse = xSocketMethod.getResponseBodyAsString();
		xSocketMethod.releaseConnection();

		int jettyGetStatusCode = httpClient.executeMethod(jettyMethod);
		String jettyResponse = jettyMethod.getResponseBodyAsString();
		jettyMethod.releaseConnection();
		
		Assert.assertEquals(jettyGetStatusCode, xSocketGetStatusCode);
		if (jettyGetStatusCode == 200) {
			Assert.assertEquals("GetMethod failed", jettyResponse, xsocketResponse);
		}
		

		
		// compare post
		PostMethod jettyPostMethod = new PostMethod("http://" + "127.0.0.1" + ":" + portJetty + "/" + path);
		jettyPostMethod.setRequestBody("test");
		jettyPostMethod.addRequestHeader("Cookie", "$Version=\"1\"; Customer=\"WILE_E_COYOTE\"; $Path=\"/acme\"");

		PostMethod xSocketPostMethod = new PostMethod("http://" + xSocketServer.getLocalAddress().getHostName() + ":" + xSocketServer.getLocalPort() + "/" + path);
		xSocketPostMethod.setRequestBody("test");
		xSocketPostMethod.addRequestHeader("Cookie", "$Version=\"1\"; Customer=\"WILE_E_COYOTE\"; $Path=\"/acme\"");

		int xSocketPostStatusCode = httpClient.executeMethod(xSocketPostMethod);
		String xsocketPostResponse = xSocketPostMethod.getResponseBodyAsString();
		xSocketPostMethod.releaseConnection();

		int jettyPostStatusCode = httpClient.executeMethod(jettyPostMethod);
		String jettyPostResponse = jettyPostMethod.getResponseBodyAsString();
		jettyPostMethod.releaseConnection();
	
		Assert.assertEquals(jettyPostStatusCode, xSocketPostStatusCode);
		if (jettyPostStatusCode == 200) {
			Assert.assertEquals("PostMethod failed", jettyPostResponse, xsocketPostResponse);
		}
	}
	
	
	
	public static class CompareServlet extends HttpServlet {

		private static final long serialVersionUID = 5632497271094661368L;

		@Override
		protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
			process(req, res);
		}
			
		@Override
		protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
			process(req, res);
		}
		
		private void process(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
			response.setContentType("text/html");
			PrintWriter out = response.getWriter();

			  // Print the HTML header
			  out.println("<HTML><HEAD><TITLE>");
			  out.println("Request info");
			  out.println("</TITLE></HEAD>");

			  // Print the HTML body
			  out.println("<BODY><H1>Request info</H1><PRE>");
			  out.println("getCharacterEncoding: " + request.getCharacterEncoding());
			  out.println("getContentLength: " + request.getContentLength());
			  out.println("getContentType: " + request.getContentType());
			  out.println("getCharacterEncoding: " + request.getCharacterEncoding());
			  out.println("getProtocol: " + request.getProtocol());
			  out.println("getContentLength: " + request.getContentLength());
			  out.println("getScheme: " + request.getScheme());
			  out.println("getAuthType: " + request.getAuthType());
			  out.println("getMethod: " + request.getMethod());
			  out.println("getPathInfo: " + request.getPathInfo());
			  out.println("getPathTranslated: " + request.getPathTranslated());
			  out.println("getQueryString: " + request.getQueryString());
			  out.println("getRemoteUser: " + request.getRemoteUser());
			  out.println("getContextpath: " + request.getContextPath());
			  out.println("getRequestURI: " + request.getRequestURI());
			  out.println("getServletPath: " + request.getServletPath());
			  out.println("getLocale: " + request.getLocale());
			  out.println("getLocales: ");
			  for (Enumeration en = request.getLocales(); en.hasMoreElements(); ) {
				  out.println("  " + en.nextElement() + " ");
			  }
			  
			  
			  out.println();
			  out.println("Parameters:");
			  Enumeration paramNames = request.getParameterNames();
			  while (paramNames.hasMoreElements()) {
			    String name = (String) paramNames.nextElement();
			    String[] values = request.getParameterValues(name);
			    out.println("    " + name + ":");
			    for (int i = 0; i < values.length; i++) {
			      out.println("      " + values[i]);
			    }
			  }

			  
			  
			  out.println();
			  out.println("Request headers:");
			  List<String> headerNames = Collections.list(request.getHeaderNames());
			  Collections.sort(headerNames);
			  for (String name : headerNames) {
				  String value = request.getHeader(name);
				  if (!name.equalsIgnoreCase("host")) {
					  out.println("  " + name + " : " + value);
				  }				
			  }
			  
			  
			  if (request.getContentLength() > 0) {
				  out.println();
				  out.println("Body:");
				  LineNumberReader reader = new LineNumberReader(request.getReader());
				  String line = null;
				  while ((line = reader.readLine()) != null) {
					 out.println(line);
				  }
			  }

			  out.println();
			  out.println("Cookies:");
			  Cookie[] cookies = request.getCookies();
			  if (cookies != null) {
				  for (int i = 0; i < cookies.length; i++) {
				    String name = cookies[i].getName();
				    String value = cookies[i].getValue();
				    out.println("  " + name + " : " + value);
				  }
			  }

			  // Print the HTML footer
			  out.println("</PRE></BODY></HTML>");
			  out.flush();
			  out.close();
		}
	}

	
	public static class PostServlet extends HttpServlet {

			
		@Override
		protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
			process(req, res);
		}
		
		private void process(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
			response.setContentType("text/plain");
			PrintWriter out = response.getWriter();


			InputStream is = request.getInputStream();
			LineNumberReader lnr = new LineNumberReader(new InputStreamReader(is));
			String line = null;
			do {
				line = lnr.readLine();
				if (line != null) {
					out.println(line);
				}
			} while (line != null);
			lnr.close();
			
			out.println("Content-length=" + request.getContentLength());
			
			out.println("Parameters:");
			Enumeration paramNames = request.getParameterNames();
			while (paramNames.hasMoreElements()) {
				String name = (String) paramNames.nextElement();
			    String[] values = request.getParameterValues(name);
			    out.println("    " + name + ":");
			    for (int i = 0; i < values.length; i++) {
			      out.println("      " + values[i]);
			    }
			}

			
			out.flush();
			out.close();
		}
	}
	
	
	
	private static final class SessionServlet extends HttpServlet {

		@Override
		protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
			HttpSession session = req.getSession();
			Integer counter = (Integer) session.getAttribute("counter");
			if (counter == null) {
				counter = 1;
				session.setAttribute("counter", counter);
				
			} else {
				counter++;
			}
			
			resp.setContentType("text/plain");
			resp.getWriter().println(counter);
			resp.getWriter().flush();
		}
	}

}
