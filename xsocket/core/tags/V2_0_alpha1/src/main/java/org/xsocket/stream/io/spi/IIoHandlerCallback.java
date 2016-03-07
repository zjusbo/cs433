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

import org.xsocket.Synchronized;


/**
 * Call back interface to notify io events of the {@link IIoHandler}. The IIoHandler
 * is responsible to notify the events in the occured order. <br><br>
 * 
 * 
 * <b>This class is experimental and is subject to change</b>
 * 
 * @author grro@xsocket.org
 */
public interface IIoHandlerCallback {
		
	
	/**
	 * notifies that data has been read from the socket. <br><br>
	 * 
	 * This method has to be called by a worker thread if 
	 * the {@link IIoHandlerContext#getWorkerpool()} is not <code>null</code>. 
	 * If the {@link IIoHandlerContext#getWorkerpool()} is <code>null</code>, 
	 * no dedicate worker threads will be used (non-multithreading mode). <br>
 	 * The callback method will only be called if the {@link IIoHandlerContext#isAppHandlerListenForDataEvent()}
 	 * method returns true. If the handler requires synchronization ({@link IIoHandlerContext#getAppHandlerSynchronizationMode()} != {@link Synchronized.Mode#OFF}),
 	 * the callback method has to be called in a synchronized context.<br><br>
	 *
	 */
	public void onDataRead();
		
	
	/**
	 * notifies that the underlying connection has been established. <br>
	 * The callback method will only be called if the {@link IIoHandlerContext#isAppHandlerListenForConnectEvent()}
 	 * method returns true. If the handler is not thread-safe ({@link IIoHandlerContext#isAppHandlerThreadSafe()} is false),
 	 * the callback method has to be called in a synchronized context.<br><br>
	 * 
	 * The threading behaivour is equals to {@link IIoHandlerCallback#onDataRead()}
	 *
	 */
	public void onConnect();
		
	
	/**
	 * notifies that the underlying connection has been disconnected (closed).<br>
  	 * The callback method will only be called if the {@link IIoHandlerContext#isAppHandlerListenforDisconnectEvent()}
 	 * method returns true. If the handler is not thread-safe ({@link IIoHandlerContext#isAppHandlerThreadSafe()} is false),
 	 * the callback method has to be called in a synchronized context.<br><br>
	 * 
	 * The threading behaivour is equals to {@link IIoHandlerCallback#onDataRead()}
	 *
	 */
	public void onDisconnect();
		
	
	/**
	 * notifies the idle time out has been occured.<br>
	 * The callback method will only be called if the {@link IIoHandlerContext#isAppHandlerListenForTimeoutEvent()}
 	 * method returns true. If the handler is not thread-safe ({@link IIoHandlerContext#isAppHandlerThreadSafe()} is false),
 	 * the callback method has to be called in a synchronized context.<br><br>
	 * 
	 * The threading behaivour is equals to {@link IIoHandlerCallback#onDataRead()} 
	 *
	 */
	public void onIdleTimeout();

	
	/**
	 * notifies the connection time out has been occured.<br>
	 * The callback method will only be called if the {@link IIoHandlerContext#isAppHandlerListenForTimeoutEvent()}
 	 * method returns true. If the handler is not thread-safe ({@link IIoHandlerContext#isAppHandlerThreadSafe()} is false),
 	 * the callback method has to be called in a synchronized context.<br><br>
	 * 
	 * The threading behaivour is equals to {@link IIoHandlerCallback#onDataRead()} 
	 *
	 */
	public void onConnectionTimeout();
		
	
	/**
	 * notifies that the connection has to be closed (connection is corrupt, 
	 * selector has be closed, ...). This call back method will NOT be called in the case of 
	 * idle or connection time out.<br><br>
	 * 
	 * The threading behaivour is equals to {@link IIoHandlerCallback#onDataRead()}
	 * 
	 */
	public void onConnectionAbnormalTerminated();
	
	
	/**
	 * notifies that data has been written on the socket.<br><br>
	 * 
	 * For performance reasons this method shouldn`t be performed by a worker thread.
	 * 
	 */
	public void onWritten();
	
	
	/**
	 * notifies that an error has been occured by writing data on the socket.<br><br>
	 * 
	 * For performance reasons this method shouldn`t be performed by a worker thread.
	 * 
	 * @param ioe ioException an io exception
	 */	
	public void onWriteException(IOException ioException);
}
