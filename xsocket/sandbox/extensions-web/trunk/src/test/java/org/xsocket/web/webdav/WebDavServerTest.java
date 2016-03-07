package org.xsocket.web.webdav;


import java.util.logging.Level;

import org.junit.Test;
import org.xsocket.stream.Server;
import org.xsocket.web.http.HttpProtocolHandler;
import org.xsocket.web.http.QAUtil;
import org.xsocket.web.webdav.WebDavProtocolHandler;



public class WebDavServerTest {
	 
	
	@Test 
	public void testSimple() throws Exception {	
		QAUtil.setLogLevel(Level.FINE);
		
		Server server = new Server(8091, new HttpProtocolHandler(new WebDavProtocolHandler()));
		server.run();
		
	}
}
