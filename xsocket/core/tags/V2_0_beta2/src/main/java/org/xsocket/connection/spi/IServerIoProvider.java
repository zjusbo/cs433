/*
 *  Copyright (c) xsocket.org, 2006 - 2008. All rights reserved.
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
package org.xsocket.connection.spi;


import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Map;

import javax.management.JMException;
import javax.management.ObjectName;

import org.xsocket.connection.Server;




/**
 * Server IO Provider specification <br><br>
 *   
 * @author grro@xsocket.org
 */
public interface IServerIoProvider extends IHandlerIoProvider {

	public static final String PROVIDER_CLASSNAME_KEY = "org.xsocket.stream.io.ServerIoProviderClass";

	
	/**
	 * Return the version of this implementation. It consists of any string assigned 
	 * by the vendor of this implementation and does not have any particular syntax 
	 * specified or expected by the Java runtime. It may be compared for equality 
	 * with other package version strings used for this implementation 
	 * by this vendor for this package.
	 * 
	 * @return the version of the implementation
	 */
	public String getImplementationVersion();
	

	/**
	 * create acceptor 
	 * 
	 * @param callback             the acceptor callback
	 * @param address              the listen address
	 * @param backlog              the backlog
	 * @param options              the acceptor socket options
	 * @return the acceptor
	 * @throws IOException If some other I/O error occurs
	 */
	public IAcceptor createAcceptor(IAcceptorCallback callback, InetSocketAddress address,  int backlog, Map<String, Object> options) throws IOException;

	
	
	/**
	 * register the mbeans of the io implementation based on the given address by using the PlattformMBeanServer 
	 *
	 * @param server     the server
	 * @param acceptor   the assigned acceptor
	 * @param domain     the domain
	 * @param address    the address
	 * @return the object name
	 * @throws JMException if an exception occurs
	 */
	public ObjectName registerMBeans(Server server, IAcceptor acceptor, String domain, String address) throws JMException;

}
