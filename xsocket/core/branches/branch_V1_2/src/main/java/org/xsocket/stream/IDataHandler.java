// $Id: IDataHandler.java 1692 2007-08-20 10:43:10Z grro $
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
package org.xsocket.stream;

import java.io.IOException;
import java.nio.BufferUnderflowException;

import org.xsocket.MaxReadSizeExceededException;








/**
 * Reads and processes the incoming data. This method will be called
 * each time when data is available. Because this depends on the 
 * underlying tcp protocol, it is not predictable how often and 
 * when this method will be call. Furthermore the call of this 
 * method is independent of the received data size. The data handler 
 * is responsible to extract the application specific data packages
 * (like HTTP request or SMTP commands) based on the received data.
 * Calling a read method on the given connection instance like 
 * connection.readLong() or connection.readStringByDelimiter(…) 
 * will throw a <code>BufferUnderflowException</code> exception 
 * if the required data isn’t available . <br><br>
 * 
 * <pre>
 * public final class MyHandler implements IDataHandler, IConnectionScoped {
 *   ...
 *   public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
 *   	...
 *      // BufferUnderflowException will been thrown if delimiter hasn`t been found. 
 *      // A MaxReadSizeExceededException will be thrown if the max read size has been exceeded. Unhandling this exception causes 
 *      // that the server closes the underlying connection 
 *      String command = connection.readStringByDelimiter("\r\n", "US-ASCII", 5000);
 *      ...
 *      connection.write(resonse, "US-ASCII");
 *      return true;
 *   }
 * } 
 * </pre> 
 * 
 * @author grro@xsocket.org
 */
public interface IDataHandler extends IHandler {
	
	/**
	 * processes the incomming data based on the given connection. <br><br>
	 * 
	 * Please note, that the <code>onData</code> callback method could also be called 
	 * for an already closed connection. This occurs when data has been received 
	 * (and buffered internally) and the connection has been closed by the peer, 
	 * immediately. In this case the <oce>isOpen</code> call within the <code>onData</code> 
	 * Method will return false. Reading of already received data wouldn`t fail.
	 * To detect if a connection has been closed the callback method <code>onDisconnect</code> 
	 * should be implemented. The correct callback order will be managed by the xSocket. 
	 * 
	 * @param connection the underlying connection
	 * @return true for positive result of handling, false for negative result of handling.
	 *              The return value will be used by the {@link HandlerChain} to interrupted 
	 *              the chaining (if result is true) 
	 * @throws IOException If some other I/O error occurs. Throwing this exception causes that the underlying connection will be closed.
 	 * @throws BufferUnderflowException if more incoming data is required to process. The BufferUnderflowException will be swallowed by the framework   
 	 * @throws MaxReadSizeExceededException if the max read size has been reached (e.g. by calling method {@link INonBlockingConnection#readStringByDelimiter(String, int)}). 
 	 *                                      Throwing this exception causes that the underlying connection will be closed.    
	 */
	public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException;
}
