// $Id: Context.java 293 2006-10-09 16:15:39Z grro $

/*
 *  Copyright (c) xsocket.org, 2006 - 2007. All rights reserved.
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 * Please refer to the LGPL license at: http://www.gnu.org/copyleft/lesser.txt
 * The latest copy of this software may be found on http://www.xsocket.org/
 */
package org.xsocket.web.http.servlet;


import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;


import org.xsocket.stream.Server;
import org.xsocket.stream.ServerMBeanProxyFactory;
import org.xsocket.web.http.JmxServer;



public class RunnableHttpServer {

	/**
	 * @param args
	 */
	public static void main(String... args) throws Exception {
		new RunnableHttpServer().launch(Integer.parseInt(args[0]));

	}

	public void launch(int port) throws Exception  {
		Logger logger = Logger.getLogger("org.xsocket");
		logger.setLevel(Level.FINE);
		ConsoleHandler hdl = new ConsoleHandler();
		hdl.setLevel(Level.FINE);
		logger.addHandler(hdl);
				
		WebAppContainer container = new WebAppContainer();
		
		Context ctx = new Context("/info");
		ctx.addServlet("InfoServlet", InfoServlet.class);
		ctx.addServletMapping("InfoServlet", "/InfoServlet");
		container.addContext(ctx);
		
		Context ctx2 = new Context("/echo");
		ctx2.addServlet("EchoServlet", PongServlet.class);
		ctx2.addServletMapping("EchoServlet", "/EchoServlet");
		ctx2.addFilter("LogFilter", LogFilter.class);
		ctx2.addFilterMapping("LogFilter", "/*", false);
		container.addContext(ctx2);

		Context ctx3 = new Context("/session");
		ctx3.addServlet("SessionServlet", SessionServlet.class);
		ctx3.addServletMapping("SessionServlet", "/SessionServlet");
		container.addContext(ctx3);

		Context ctx4 = new Context("/post");
		ctx4.addServlet("PostServlet", PostServlet.class);
		ctx4.addServletMapping("PostServlet", "/PostServlet");
		container.addContext(ctx4);
		

		Context ctx5 = new Context("/absoluteRedirect");
		ctx5.addServlet("AbsoluteRedirectServlet", AbsoluteRedirectServlet.class);
		ctx5.addServletMapping("AbsoluteRedirectServlet", "/RedirectServlet");
		container.addContext(ctx5);

		
		Context ctx6 = new Context("/relativeRedirect");
		ctx6.addServlet("RelativeRedirectServlet", RelativeRedirectServlet.class);
		ctx6.addServletMapping("RelativeRedirectServlet", "/RedirectServlet");
		container.addContext(ctx6);
		
		Server ss = new Server(port, container);
		ss.setIdleTimeoutSec(2 * 60);
		ss.setConnectionTimeoutSec(30 * 60);

		
		// start jmx server
		JmxServer jmxServer = new JmxServer();
		jmxServer.start("httpServer", 9088);
		
		// create server and register it as mbean
		ServerMBeanProxyFactory.createAndRegister(ss, "test");
		
		
		ss.run();		
	}
}
