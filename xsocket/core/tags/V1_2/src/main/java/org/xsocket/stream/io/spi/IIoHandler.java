// $Id: IoHandlerBase.java 1315 2007-06-10 08:05:00Z grro $
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
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.Map;

import org.xsocket.ClosedConnectionException;



/**
 * The <code>IIoHandler</code> is responsible to perform the low level io operation of a 
 * {@link IConnection}. Each connection holds a dedicated io handler. There
 * is always a 1:1 life cycle relationship between the connection instance and the io handler
 * instance. 
 * Because a connection object is stateful, the io handler is (implicit) stateful, too. 
 * The io handler has to handle the read and write operations independently. There is no
 * implicit call behaviour like request-response. <br>
 * The io handler is responsible to notify io events by calling the assigned
 * {@link IIoHandlerContext} reference. <br><br>
 * 
 * The implementation needn't to be threadsafe 
 * 
 * <b>This class is experimental</b> and is subject to change
 * 
 * @author grro@xsocket.org
 */
public interface IIoHandler  {
	
	
	/**
	 * "starts" the handler. Callback methods will not be called before
	 * this method has been performed. 
	 * 
	 * @param callbackHandler  the callback handler 
	 */
	public void init(IIoHandlerCallback callbackHandler) throws IOException;
		
	
	
	/**
	 * returns the local address of the underlying connection.
	 * 
	 * @return the local address of the underlying connection
	 */	
	public InetAddress getLocalAddress();

	
	/**
	 * returns the local port of the underlying connection. 
	 * 
	 * @return the local port of the underlying connection 
	 */
	public int getLocalPort();
	
	
	
	/**
	 * returns the address of the remote host of the underlying connection.
	 * 
	 * @return the address of the remote host of the underlying connection
	 */
	public InetAddress getRemoteAddress();
	
	
	/**
	 * returns the port of the remote host of the underlying connection. 
	 * 
	 * @return the port of the remote host of the underlying connection
	 */
	public int getRemotePort();
	
	
	
	/**
	 * sets the idle timeout.  
	 * 
	 * @param timeout the idle timeout
	 */
	public void setIdleTimeoutSec(int timeout);
	
	
	
	/**
	 * return a unique conection id 
	 * @return unique conection is
	 */
	public String getId();
	
	/**
	 * sets the connection timout.  
	 * 
	 * @param timeout the connection timeout
	 */
	public void setConnectionTimeoutSec(int timeout);
	
	 
	/**
	 * returns the connection timeout.  
	 *  
	 * @return the connection timeout
	 */
	public int getConnectionTimeoutSec();
	
	
	/**
	 * returns the idle timeout. 
	 * 
	 * @return the idle timeout
	 */
	public int getIdleTimeoutSec();
	
	
	/**
	 * returns the vlaue of a option
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
	 * set a option 
	 * 
	 * @param name    the option name
	 * @param value   the option value
	 * @throws IOException In an I/O error occurs 
	 */
	public void setOption(String name, Object value) throws IOException;
	
	
	/**
	 * check, if the handler (underlying connection) is open.
	 * 
	 * @return true, if the handler is open
	 */
	public boolean isOpen();
		
	
	/**
	 * non-blocking close of the handler. <br><br>
	 *  
	 * The implementation has to be threadsafe 
	 * 
	 * @param immediate if true, close the connection immediate. If false remaining
	 *                  out buffers (collected by the writOutgoing methods) has 
	 *                  to written before closing 
	 * @throws IOException If some other I/O error occurs
	 */
	public void close(boolean immediate) throws IOException;
	

	/**
	 * non-blocking write. Because the IIoHandler is free to write the given buffer within
	 * a backgound avtivity, the caller can't reuse the buffer, util the 
	 * {@link IIoHandlerCallback#onWritten(IOException)} call back method has been called.    
	 * 
	 * @param buffer the data to add into the out buffer
	 * @throws ClosedConnectionException if the underlying connection is already closed
	 * @throws IOException If some other I/O error occurs
	 */
	public void writeOutgoing(ByteBuffer buffer) throws ClosedConnectionException, IOException;
	
	
	
	/**
	 * non-blocking write.  Because the IIoHandler is free to write the given buffers 
	 * within a backgound avtivity, the caller can't reuse the buffers, util the 
	 * {@link IIoHandlerCallback#onWritten(IOException)} call back method has been called.    
	 *  
	 * @param buffers the datas to add into the out buffer
	 * @throws ClosedConnectionException if the underlying connection is already closed
	 * @throws IOException If some other I/O error occurs
	 */
	public void writeOutgoing(LinkedList<ByteBuffer> buffers) throws ClosedConnectionException, IOException;

	

	/**
	 * returns the size of the data which have already been written, but not 
	 * yet transferred to the underlying socket. 
	 * 
	 * @return the size of the pending data to write 
	 */
	public int getPendingWriteDataSize();
	
	
	
	/**
	 * suspend reading data from the underlying subsystem
	 *
	 * @throws IOException If some other I/O error occurs
	 */
	public void suspendRead() throws IOException;
	

	/**
	 * resume reading data from the underlying subsystem
	 *
	 * @throws IOException If some other I/O error occurs
	 */
	public void resumeRead() throws IOException; 
	
	/**
	 * drains the handler's read buffer. Because the buffers can be store temporally by 
	 * the connection and accessed later, the passed over buffers are not subject of reuse.<br>
	 * This method will only be called within the same thread, which performs the 
	 * {@link IIoHandlerCallback#onDataRead()} method.
	 * <pre>
	 * Example Stacktrace:  
	 * [DataReaderThread]
	 *  &lt;IIoHandler&gt;.drainIncomming()
	 *  &lt;IIoHandlerCallback&gt;.onDataRead()
	 *  ...
	 * </pre>
	 * 
	 * @return the content of the handler's read buffer
	 */
	public LinkedList<ByteBuffer> drainIncoming();
}
