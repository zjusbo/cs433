package org.xsocket.web.http.servlet;



import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.junit.Test;
import org.mortbay.jetty.Server;



public class SimpleTest {
	
	
	@Test public void testSimle() throws Exception {

		int jettyServerPort = 9085; 
		
		// start jetty server
		Server jettyServer = new Server(jettyServerPort);
		org.mortbay.jetty.servlet.Context rootCtx = new org.mortbay.jetty.servlet.Context(jettyServer, "");
		org.mortbay.jetty.servlet.ServletHolder servletHolder = new org.mortbay.jetty.servlet.ServletHolder(new TestServlet());
		rootCtx.addServlet(servletHolder, "/test/*");
		jettyServer.start();
		

		GetMethod jettyMethod = new GetMethod("http://" + "127.0.0.1" + ":" + jettyServerPort + "/test/testCall/ctx?testparam=testvalue");
		jettyMethod.setFollowRedirects(false);
		
		new HttpClient().executeMethod(jettyMethod);
		jettyMethod.releaseConnection();

		jettyServer.stop();
	}
	
	
	public static class TestServlet extends HttpServlet {

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
			
			String param = request.getParameter("testparam");
			System.out.println(param);
			
		}
	}

}
