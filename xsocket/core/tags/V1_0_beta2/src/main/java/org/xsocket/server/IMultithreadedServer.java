// $Id: IMultithreadedServer.java 457 2006-12-09 14:13:33Z grro $
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
package org.xsocket.server;



/**
 * A server accepts new incomming connections, and delegates the handling of the 
 * <code>INonBlockingConnection</code> to the assigned handler. 
 * 
 * The server includes dispatchers, which are responsible to perform the 
 * socket I/O operations. A connection is assigned to one dispatcher. <br> 
 * To handle application-relevant events like <code>onData</code>, 
 * <code>onClose</code> or <code>onConnect</code> the appropriated callback method
 * of the assigned <code>IHandler</code> will be called. The supported callback
 * methods of the handler will be analysed by using reflection during  the server start-up
 * phase. The callback method will be marked by implementing the specifc interface
 * like <code>IDataHandler</code> or <code>IConnectHandler</code>. Often a
 * handler will implement serveral handler interfaces.<br>
 * If a handler implements the <code>IConnectionScoped</code>-Interface, a clone of 
 * the registered handler will be created for each new incomming connection.
 * <br>
 * E.g. 
 * <pre>
 *   ...
 *   IMultithreadedServer smtpServer = new MultithreadedServer(port);
 *   smtpServer.setHandler(new SmtpProtcolHandler());
 *   smtpServer.run();
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
public interface IMultithreadedServer extends Runnable {


	/**
	 * the default receive buffer preallocation size
	 */
	public static final int DEFAULT_RECEIVE_BUFFER_PREALLOCATION_SIZE = 64768;
	

	/**
	 * the default idle timeout
	 */
	public static final int DEFAULT_IDLE_TIMEOUT_SEC = 1 * 60 * 60;

	
	
	/**
	 * the default connection timeout
	 */
	public static final int DEFAULT_CONNECTION_TIMEOUT_SEC = Integer.MAX_VALUE;



	
	/**
	 * signals, if service is running
	 * 
	 * @return true, if the server is running 
	 */
	public boolean isRunning();

	
	/**
	 * set the handler. The server performs
	 * the initialzation of the given handler
	 * immediately.   
	 * 
	 * @param handler the handler.
	 */
	public void setHandler(IHandler handler);
	
	
	/**
	 * shutdown the server
	 */
	public void shutdown();

	
	/**
	 * set the thread worker pool size 
	 *  
	 * @param size the worker pool size 
	 */
	public void setWorkerPoolSize(int size);
	
	
	/**
	 * get the thread worker pool size 
	 *  
	 * @return the worker pool size 
	 */
	public int getWorkerPoolSize();	
	
	

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
	public int getPort();
	

	
	/**
	 * set the dispatcher pool size
	 * 
	 * @param size the dispatcher pool size
	 */
	public void setDispatcherPoolSize(int size);
	
	
	/**
	 * get dispatcher pool size
	 * 
	 * @return the dispatcher pool size
	 */
	public int getDispatcherPoolSize();
}
