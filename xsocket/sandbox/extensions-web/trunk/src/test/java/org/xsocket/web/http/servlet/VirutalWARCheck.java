package org.xsocket.web.http.servlet;




import java.util.logging.Level;

import org.xsocket.stream.IServer;
import org.xsocket.stream.Server;
import org.xsocket.web.http.QAUtil;



public class VirutalWARCheck {

	/**
	 * @param args
	 */
	public static void main(String... args) throws Exception {
		new VirutalWARCheck().launch(Integer.parseInt(args[0]));

	}

	public void launch(int port) throws Exception  {
		
		QAUtil.setLogLevel(Level.FINE);
		
		WebAppContainer container = new WebAppContainer();
		VirtualWAR vwar = new VirtualWAR();
		vwar.add("/", "src/test/resources/org/xsocket/web/http/servlet");
		vwar.setContentCacheActivated(false);
		
		container.install("/test", vwar);
		IServer ss = new Server(port, container);
		
		ss.run();		
		
		try {
			Thread.sleep(5000000); 
		} catch (InterruptedException ignore) { }
	}
}
