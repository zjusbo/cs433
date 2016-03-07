//$Id: SpringTest.java 1108 2007-03-29 16:44:02Z grro $
/*
 *  Copyright (c) xsocket.org, 2006. All rights reserved.
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
package org.xsocket.stream;



import java.util.logging.Level;

import org.junit.Assert;
import org.junit.Test;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.xml.XmlBeanFactory;
import org.springframework.core.io.ByteArrayResource;
import org.xsocket.QAUtil;
import org.xsocket.stream.BlockingConnection;
import org.xsocket.stream.EchoHandler;
import org.xsocket.stream.IBlockingConnection;
import org.xsocket.stream.IMultithreadedServer;



/**
*
* @author grro@xsocket.org
*/
public final class SpringTest {
	
	
	private static final String SPRING_XML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n\r" +
									  		 "<!DOCTYPE beans PUBLIC \"-//SPRING//DTD BEAN//EN\" \"http://www.springframework.org/dtd/spring-beans.dtd\">\n\r" +
									  		 "\n\r" +
									  		 "<beans>\n\r" +
									  		 " <bean id=\"server\" class=\"org.xsocket.stream.MultithreadedServer\" singleton=\"true\">\n\r" +
									  		 "  <constructor-arg type=\"int\" value=\"8787\"/>\n\r" +
									  		 "  <constructor-arg type=\"org.xsocket.stream.IHandler\" ref=\"Handler\"/>\n\r" +
									  		 "  <property name=\"receiveBufferPreallocationSize\" value=\"8388608\"/>\n\r" +
									  		 "\n\r" +
										  	 " </bean>\n\r" +
										  	 " <bean id=\"Handler\" class=\"org.xsocket.stream.EchoHandler\" singleton=\"false\"/>\n\r" +
										  	 "</beans>";


	@Test public void testSimple() throws Exception {	
		//TestUtil.setLogLevel(Level.FINE);
		
		BeanFactory beanFactory = new XmlBeanFactory(new ByteArrayResource(SPRING_XML.getBytes()));
		IMultithreadedServer server = (IMultithreadedServer) beanFactory.getBean("server");
		Thread t = new Thread(server);
		t.start();
		
		do {
			try {
				Thread.sleep(500);
			} catch (InterruptedException ignore) { }
		} while (!server.isOpen());
		
		
		IBlockingConnection connection = new BlockingConnection(server.getLocalAddress(), server.getLocalPort());
		connection.setAutoflush(true);
		String request = "3453543535";
		connection.write(request + EchoHandler.DELIMITER);
		
		String response = connection.readStringByDelimiter(EchoHandler.DELIMITER, Integer.MAX_VALUE);
		connection.close();
		
		Assert.assertTrue(request.equals(response));
		
		server.close();
	}
}
