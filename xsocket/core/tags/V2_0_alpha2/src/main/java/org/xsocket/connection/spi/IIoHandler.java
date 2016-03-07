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
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.Map;

import org.xsocket.ClosedException;



/**
 * The <code>IIoHandler</code> is responsible to perform the low level io operation of a
 * {@link IReadWriteableConnection}. Each connection holds a dedicated io handler. There
 * is always a 1:1 life cycle relationship between the connection instance and the io handler
 * instance.
 * Because a connection object is stateful, the io handler is (implicit) stateful, too.
 * The io handler has to handle the read and write operations independently. There is no
 * implicit call behaviour like request-response. <br>
 * The io handler is responsible to notify io events by calling the assigned
 * {@link IIoHandlerCallback} reference. <br><br>
 *
 * The implementation needn`t to be threadsafe
 *
 * @author grro@xsocket.org
 */
public interface IIoHandler  {


	/**
	 * "starts" the handler. Callback methods will not be called before
	 * this method has been performed.
	 *
	 *
	 * @param callbackHandler  the callback handler
	 */
	public void init(IIoHandlerCallback callbackHandler) throws IOException;



	/**
	 * return a unique conection id
	 * @return unique conection is
	 */
	public String getId();



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
	 * reset internal caches and buffers of the io handler
	 *
	 * @return true, is handler has been reset
	 */
	public boolean reset();


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
	 * returns the idle timeout.
	 *
	 * @return the idle timeout
	 */
	public int getIdleTimeoutSec();


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
	 * returns the remaining time before a idle timeout occurs
	 *
	 * @return the remaining time
	 */
	public int getRemainingSecToIdleTimeout();


	/**
	 * returns the remaining time before a connection timeout occurs
	 *
	 * @return the remaining time
	 */
	public int getRemainingSecToConnectionTimeout();


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
	@SuppressWarnings("unchecked")
	public Map<String, Class> getOptions();



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
	 * non-blocking write.  Because the IIoHandler is free to write the given buffers
	 * within a backgound activity, the caller can`t reuse the buffers, until the
	 * {@link IIoHandlerCallback#onWritten(IOException)} call back method has been called.
	 *
	 * @param buffers the data to add into the out buffer
	 * @throws ClosedException if the underlying connection is already closed
	 * @throws IOException If some other I/O error occurs
	 */
	public void write(ByteBuffer[] buffers) throws ClosedException, IOException;



	/**
	 * returns the size of the data which have already been written, but not
	 * yet transferred to the underlying socket.
	 *
	 * @return the size of the pending data to write
	 */
	public int getPendingWriteDataSize();



	/**
	 * returns if there are data to send
	 *
	 * @return true, if there are data to send
	 */
	public boolean hasDataToSend();


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
}
