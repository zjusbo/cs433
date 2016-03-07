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

import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Map;

import org.xsocket.stream.IServer;



/**
 * Acceptor specification <br><br>
 *
 * <b>This class is experimental and is subject to change</b>
 *
 * @author grro@xsocket.org
 */
public interface IAcceptor extends Closeable {

	public static final String ACCEPTOR_CLASSNAME_KEY = "org.xsocket.stream.spi.AcceptorClassname";

	public static final String SO_RCVBUF = IServer.SO_RCVBUF;
	public static final String SO_REUSEADDR = IServer.SO_REUSEADDR;


	/**
	 * returns the value of a option
	 *
	 * @param name  the name of the option
	 * @return the value of the option
	 * @throws IOException In an I/O error occurs
	 */
	public Object getOption(String name) throws IOException;



	/**
	 * Returns an unmodifiable map of the options supported by this endpont.
	 *
	 * The key in the returned map is the name of a option, and its value
	 * is the type of the option value. The returned map will never contain null keys or values.
	 *
	 * @return An unmodifiable map of the options supported by this channel
	 */
	public Map<String,Class> getOptions();



	/**
	 * executes the acceptor by listening incoming connections. This method blocks
	 *
	 * @throws IOException If some other I/O error occurs
	 */
	public void listen() throws IOException;


	/**
	 * return the number of open connections
	 *
	 */
	public int getNumberOfOpenConnections();


	/**
	 * returns the local port
	 *
	 * @return the local port
	 */
	public int getLocalPort();

	/**<
	 * returns the local address
	 *
	 * @return the local address
	 */
	public InetAddress getLocalAddress();


}
