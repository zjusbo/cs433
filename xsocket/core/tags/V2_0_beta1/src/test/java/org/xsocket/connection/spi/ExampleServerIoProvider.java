/*
 *  Copyright (c) xsocket.org, 2006-2008. All rights reserved.
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
import org.xsocket.connection.spi.IAcceptor;
import org.xsocket.connection.spi.IAcceptorCallback;
import org.xsocket.connection.spi.IIoHandler;
import org.xsocket.connection.spi.IServerIoProvider;


/**
*
* @author grro@xsocket.org
*/
public final class ExampleServerIoProvider implements IServerIoProvider {

	public IAcceptor createAcceptor(IAcceptorCallback callback, InetSocketAddress address, int backlog, Map<String, Object> options) throws IOException {
		return new ExampleAcceptor(callback, address);
	}
	
	
	public String getImplementationVersion() {
		return "1.0";
	}
	
	public IIoHandler setWriteTransferRate(IIoHandler ioHandler, int bytesPerSecond) throws IOException {
		return null;
	}
	
	public ObjectName registerMBeans(Server server, IAcceptor acceptor, String domain, String address) throws JMException {
		return null;
	}
}
