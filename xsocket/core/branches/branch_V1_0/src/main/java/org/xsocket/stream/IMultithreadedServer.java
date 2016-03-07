// $Id: IMultithreadedServer.java 1047 2007-03-20 19:45:31Z grro $
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

import java.io.Closeable;
import java.net.InetAddress;
import java.util.List;


import org.xsocket.IDispatcher;
import org.xsocket.IWorkerPool;





/**
 * A server accepts new incomming connections, and delegates the handling of the 
 * {@link INonBlockingConnection} to the assigned handler. 
 * 
 * The server includes dispatchers, which are responsible to perform the 
 * socket I/O operations. A connection is assigned to one dispatcher. <br> 
 * To handle application-relevant events like <code>onData</code>, 
 * <code>onClose</code> or <code>onConnect</code> the appropriated callback method
 * of the assigned {@link IHandler} will be called. The supported callback
 * methods of the handler will be analysed by using reflection during  the server start-up
 * phase. The callback method will be marked by implementing the specifc interface
 * like {@link IDataHandler} or {@link IConnectHandler}. Often a
 * handler will implement serveral handler interfaces.<br>
 * If a handler implements the {@link IConnectionScoped} interface, a clone of 
 * the registered handler will be created for each new incomming connection.
 * <br>
 * E.g. 
 * <pre>
 *   ...
 *   IMultithreadedConnectionServer smtpServer = new MultithreadedTcpServer(port, new SmtpProtcolHandler());
 *   new Thread(smtpServer).start();
 *   ...
 *   
 *   
 *   // Handler definition
 *   class SmtpProtcolHandler implements implements IDataHandler, IConnectHandler, IConnectionScoped {
 *   	...
 *   
 *      public boolean onConnect(INonBlockingConnection connection) throws IOException {
 *          connection.write("220 this is the example smtp server" + LINE_DELIMITER);
 *          return true;
 *      }
 *   
 *   
 *   
 *      public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
 *         switch (state) {
 *             case COMMAND_HANDLING:
 *                 handleCommand(connection);
 *                 break;
 *                 
 *             case MESSAGE_DATA_HANDLING:
 *                 handleMessageData(connection);
 *                 break;
 *         }
 *         return true;
 *      }   
 *      
 *      private void handleCommand(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
 *      ...
 *      
 *      
 *      
 *      &#064Override
 *      public Object clone() throws CloneNotSupportedException {
 *          SmtpProtocolHandler copy = (SmtpProtocolHandler) super.clone();
 *          copy.rcptTos = new ArrayList<String>(); // deep copy!
 *          return copy;
 *      }
 *   }
 * </pre> 
 *   
 * 
 * @author grro@xsocket.org
 */
public interface IMultithreadedServer extends Runnable, Closeable {


	/**
	 * the default receive buffer preallocation size
	 */
	public static final int DEFAULT_RECEIVE_BUFFER_PREALLOCATION_SIZE = 64768;
	

	/**
	 * the default idle timeout
	 */
	public static final int DEFAULT_IDLE_TIMEOUT_SEC = 1 * 60 * 60;  // one hour

	
	
	/**
	 * the default connection timeout
	 */
	public static final int DEFAULT_CONNECTION_TIMEOUT_SEC = Integer.MAX_VALUE;  // no timeout  



	
	/**
	 * signals, if service is running
	 * 
	 * @return true, if the server is running 
	 */
	public boolean isOpen();

	
	/**
	 * set the handler. The server performs
	 * the initialzation of the given handler
	 * immediately.   
	 * 
	 * @param handler the handler. (supported: IConnectHandler, IDisconnectHandler, IDataHandler, ITimeoutHandler, IConnectionScoped, ILifeCycle)
	 */
	public void setHandler(IHandler handler);
	
	
	
	/**
	 * replace the worker pool with the given one. <br><br>
	 * 
	 * By closing the endpoint, the close method of the worker pool will be called   
	 * 
	 * @param workerPool the worker pool to set
	 */
	public void setWorkerPool(IWorkerPool workerPool);	 


	/**
	 * return the worker pool
	 * 
	 * @return the worker pool
	 */
	public IWorkerPool getWorkerPool();	 
	
	/**
	 * set the size of the preallocation buffer, 
	 * for reading incomming data
	 *
	 * @param size preallocation buffer size
	 */

	public void setReceiveBufferPreallocationSize(int size);

	
	/**
	 * get the size of the preallocation buffer, 
	 * for reading incomming data
	 *   
	 * @return preallocation buffer size
	 */
	public int getReceiveBufferPreallocationSize();
	
	
	/**
	 * returns the idle timeout in sec. 
	 * 
	 * @return idle timeout in sec
	 */
	public int getIdleTimeoutSec();
	
	
	/**
	 * sets the idle timeout in sec 
	 * 
	 * @param timeoutInSec idle timeout in sec
	 */
	public void setIdleTimeoutSec(int timeoutInSec);
	
	
	/**
	 * adds a listener 
	 * @param listener gthe listener to add
	 */
	public void addListener(IMutlithreadedServerListener listener);
	
	
	/**
	 * removes a listener 
	 * @param listener the listener to remove 
	 * @return true, is the listener has been removed
	 */
	public boolean removeListener(IMutlithreadedServerListener listener);
	
	
	/**
	 * gets the connection timeout
	 * 
	 * @return connection timeout
	 */
	public int getConnectionTimeoutSec();
	
	
	/**
	 * sets the max time for a connections. By 
	 * exceeding this time the connection will be
	 * terminated
	 * 
	 * @param timeoutSec the connection timeout in sec
	 */
	public void setConnectionTimeoutSec(int timeoutSec);
	
	
	/**
	 * get the server port 
	 * 
	 * @return the server port
	 */
	public int getLocalPort();
	

	/**
	 * get the local address
	 * @return the local address
	 */
	public InetAddress getLocalAddress();

	
	/**
	 * set the dispatcher pool size
	 * 
	 * @param size the dispatcher pool size
	 */
	public void setDispatcherPoolSize(int size);
	
	
	/**
	 * get the dispatcher pool size
	 * 
	 * @return the dispatcher pool size
	 */
	public int getDispatcherPoolSize();
	
	
	/**
	 * get the dispatcher 
	 * 
	 * @return the dispatcher
	 */
	public List<IDispatcher> getDispatcher();
}
