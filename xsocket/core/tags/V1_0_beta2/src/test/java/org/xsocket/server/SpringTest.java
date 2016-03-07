// $Id: SpringTest.java 439 2006-12-06 06:43:30Z grro $
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
package org.xsocket.server;

import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import junit.framework.JUnit4TestAdapter;
import junit.textui.TestRunner;

import org.junit.Assert;
import org.junit.Test;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.xml.XmlBeanFactory;
import org.springframework.core.io.ByteArrayResource;
import org.xsocket.IBlockingConnection;
import org.xsocket.BlockingConnection;
import org.xsocket.LogFormatter;



/**
*
* @author grro@xsocket.org
*/
public final class SpringTest {
	
	
	private static final String SPRING_XML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n\r" +
									  		 "<!DOCTYPE beans PUBLIC \"-//SPRING//DTD BEAN//EN\" \"http://www.springframework.org/dtd/spring-beans.dtd\">\n\r" +
									  		 "\n\r" +
									  		 "<beans>\n\r" +
									  		 " <bean id=\"server\" class=\"org.xsocket.server.MultithreadedServer\" singleton=\"true\">\n\r" +
									  		 "  <constructor-arg type=\"int\" value=\"8787\"/>\n\r" +
									  		 "    <property name=\"receiveBufferPreallocationSize\" value=\"8388608\"/>\n\r" +
									  		 "    <property name=\"handler\" ref=\"Handler\"/>\n\r" +
									  		 "\n\r" +
										  	 " </bean>\n\r" +
										  	 " <bean id=\"Handler\" class=\"org.xsocket.server.EchoHandler\" singleton=\"false\"/>\n\r" +
										  	 "</beans>";


	@Test public void testSimple() throws Exception {
		BeanFactory beanFactory = new XmlBeanFactory(new ByteArrayResource(SPRING_XML.getBytes()));
		IMultithreadedServer server = (IMultithreadedServer) beanFactory.getBean("server");
		Thread t = new Thread(server);
		t.start();
		
		do {
			try {
				Thread.sleep(500);
			} catch (InterruptedException ignore) { }
		} while (!server.isRunning());
		
		
		IBlockingConnection connection = new BlockingConnection("127.0.0.1", server.getPort());
		String request = "3453543535";
		connection.write(request + EchoHandler.DELIMITER);
		String response = connection.receiveStringByDelimiter(EchoHandler.DELIMITER);
		connection.close();
		
		Assert.assertTrue(request.equals(response));
		
		server.shutdown();
	}
}
