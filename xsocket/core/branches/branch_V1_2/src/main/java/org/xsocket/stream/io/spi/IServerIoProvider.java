// $Id: IMutlithreadedServerListener.java 1280 2007-05-28 17:59:08Z grro $
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
package org.xsocket.stream.io.spi;


import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Map;




/**
 * Server IO Provider specification <br><br>
 * 
 * <b>This class is experimental and is subject to change</b>
 *  
 * @author grro@xsocket.org
 */
public interface IServerIoProvider extends IHandlerIoProvider {

	public static final String PROVIDER_CLASSNAME_KEY = "org.xsocket.stream.io.spi.ServerIoProviderClass";


	/**
	 * create acceptor 
	 * 
	 * @param callback             the acceptor callback
	 * @param handlerContext       the handler context
	 * @param address              the listen address
	 * @param backlog              the backlog
	 * @param options              the acceptor socket options
	 * @return the acceptor
	 * @throws IOException If some other I/O error occurs
	 */
	public IAcceptor createAcceptor(IAcceptorCallback callback, IIoHandlerContext handlerContext, InetSocketAddress address,  int backlog, Map<String, Object> options) throws IOException;

}
