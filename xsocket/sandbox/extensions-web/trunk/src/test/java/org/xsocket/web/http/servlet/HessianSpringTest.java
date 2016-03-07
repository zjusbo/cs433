package org.xsocket.web.http.servlet;


import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

import org.junit.Assert;
import org.junit.Test;
import org.xsocket.stream.IServer;
import org.xsocket.stream.Server;
import org.xsocket.web.http.QAUtil;

import com.caucho.hessian.client.HessianProxyFactory;

public class HessianSpringTest {
	
	
	@Test public void testSimple() throws Exception {	
		QAUtil.setLogLevel(Level.FINE);
		
		WebAppContainer container = new WebAppContainer();
		IServer ss = new Server(container);


		Context ctx = new Context("/test");
			
		Map<String, String> initParams = new HashMap<String, String>();
		initParams.put("contextConfigLocation", "file://" + HessianSpringTest.class.getResource("applicationContext.xml").getPath());
		ctx.addServlet("Test", org.springframework.web.servlet.DispatcherServlet.class, initParams);
		ctx.addServletMapping("Test", "/remoting/*");
		container.addContext(ctx);

		Thread t = new Thread(ss);
		t.start();

		do {
			try {
				Thread.sleep(100);
			} catch (InterruptedException ignore) { }
		} while (!ss.isOpen());
		

		
	    String url = "http://" + ss.getLocalAddress().getHostName() + ":" + ss.getLocalPort() + "/test/remoting/Service";
	    HessianProxyFactory factory = new HessianProxyFactory();
		IService service = (IService) factory.create(IService.class, url);
		
		AddressDTO addr = new AddressDTO();
		addr.setName("hans wurst");
		addr.setZip(3343);
		int result = service.calculate(45, 45, addr);			

		Assert.assertTrue(result == (45 * 45));
		ss.close();
	}
}
