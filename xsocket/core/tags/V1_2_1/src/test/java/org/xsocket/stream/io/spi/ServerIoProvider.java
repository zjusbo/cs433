// $Id: ManagementTest.java 1237 2007-05-13 16:55:37Z grro $
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
package org.xsocket.stream.io.spi;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Map;


import org.xsocket.stream.io.spi.IServerIoProvider;
import org.xsocket.stream.io.spi.IAcceptorCallback;
import org.xsocket.stream.io.spi.IIoHandlerContext;


/**
*
* @author grro@xsocket.org
*/
public final class ServerIoProvider implements IServerIoProvider {

	public IAcceptor createAcceptor(IAcceptorCallback callback, IIoHandlerContext handlerContext, InetSocketAddress address, int backlog, Map<String, Object> options) throws IOException {
		return new ExampleAcceptor(callback, handlerContext, address);
	}
	
	public IIoHandler setWriteTransferRate(IIoHandler ioHandler, int bytesPerSecond) throws IOException {
		return null;
	}
}
