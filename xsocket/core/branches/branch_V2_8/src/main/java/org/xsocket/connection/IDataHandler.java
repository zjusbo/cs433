/*
 * Copyright (c) xlightweb.org, 2006 - 2010. All rights reserved.
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
package org.xsocket.connection;

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.channels.ClosedChannelException;

import org.xsocket.MaxReadSizeExceededException;








/**
 * Reads and processes the incoming data. This method will be called
 * each time when data is available or the connection is closed. Because 
 * this depends on the underlying tcp protocol, it is not predictable 
 * how often and when this method will be call. Please note, that on network level
 * data can be fragmented on several TCP packets as well as data can
 * be bundled into one TCP packet. <br><br>
 *
 * Performing a write operation like <code>connection.write("hello it's me. What I have to say is.")</code>
 * on the client side doesn�t mean that exact one onData call occurs on
 * the server side.  A common pattern to solve this is to identify logical
 * parts by a delimiter or a leading length field.
 * xSocket provides methods to support this pattern. E.g. the {@link INonBlockingConnection#readStringByDelimiter(String)}
 * method only returns a record if the whole part (identified by the delimiter) has
 * been received, or if not, a BufferUnderflowException will be thrown. In
 * contrast to {@link IBlockingConnection}, a {@link INonBlockingConnection} read
 * method always returns immediately and could thrown a BufferUnderflowException.
 * The {@link BufferUnderflowException} will be swallowed by the framework, if
 * the DataHandler doesn�t catch this exception. It is a common pattern
 * not to handle such an exception by the DataHandler.
 *
 * <pre>
 * public final class MyHandler implements IDataHandler, IConnectionScoped {
 *   ...
 *   public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
 *   	...
 *      // BufferUnderflowException will been thrown if delimiter has not been found.
 *      // A MaxReadSizeExceededException will be thrown if the max read size has been exceeded. Not handling this exception causes
 *      // that the server closes the underlying connection
 *      String command = connection.readStringByDelimiter("\r\n", "US-ASCII", 5000);
 *      ...
 *      connection.write(response, "US-ASCII");
 *      return true;
 *   }
 * }
 * </pre>
 *
 * @author grro@xsocket.org
 */
public interface IDataHandler extends IHandler {

	/**
	 * processes the incoming data based on the given connection. <br><br>
	 *
	 * Please note, that the <code>onData</code> call back method can also be called
	 * for if an connection will be closed. In this case the <code>isOpen</code> call 
	 * within the <code>onData</code> Method will return false. Reading of already 
	 * received data will not fail. 
	 * To detect if a connection has been closed the callback method <code>onDisconnect</code>
	 * should be implemented. The correct call back order will be managed by the xSocket.
	 *
	 * @param connection the underlying connection
	 * @return true for positive result of handling, false for negative result of handling.
	 *              The return value will be used by the {@link HandlerChain} to interrupted
	 *              the chaining (if result is true)
	 * @throws IOException If some other I/O error occurs. Throwing this exception causes that the underlying connection will be closed.
 	 * @throws BufferUnderflowException if more incoming data is required to process (e.g. delimiter hasn't yet received -> readByDelimiter methods or size of the available, received data is smaller than the required size -> readByLength). The BufferUnderflowException will be swallowed by the framework
 	 * @throws ClosedChannelException if the connection is closed
 	 * @throws MaxReadSizeExceededException if the max read size has been reached (e.g. by calling method {@link INonBlockingConnection#readStringByDelimiter(String, int)}).
 	 *                                      Throwing this exception causes that the underlying connection will be closed.
     * @throws RuntimeException if an runtime exception occurs. Throwing this exception causes that the underlying connection will be closed.                                       	 *                                      
	 */
	boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, ClosedChannelException, MaxReadSizeExceededException;
}
