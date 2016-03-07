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

import java.util.concurrent.Executor;



/**
 * Provides the {@link IIoHandler}'s context parameters <br><br>
 * 
 * <b>This class is experimental and is subject to change</b>
 *  
 * @author grro@xsocket.org
 */
public interface IIoHandlerContext {

	/**
	 * the (shared) worker pool.  In case of multithreaded {@link IIoHandlerContext#isMultithreaded()}, 
	 * this worker pool will also be used to perform the onXX callback Methods of the {@link IIoHandlerCallback} calls.
	 * 
	 * @return the worker pool
	 */
	public Executor getWorkerpool();
	
	
	/**
	 * return if handling is multithreaded.
	 * 
	 * @return true, if multithreaded
	 */
	public boolean isMultithreaded();
	
	
	
	/**
	 * return if handler is thread save. If the appHandler is not thread-safe
	 * the IoHandler has to synchonize the callback methods
	 * 
	 * @return true, if handler is threadsafe
	 */
	public boolean isAppHandlerThreadSafe();
	
	
	/**
	 * returns true, if the callback handler's onConnect method should be called  
	 * 
	 * @return true, if the callback handler's onConnect method should be called
	 */
	public boolean isAppHandlerListenForConnectEvent();

	
	
	/**
	 * returns true, if the callback handler's onData method should be called  
	 * 
	 * @return true, if the callback handler's onData method should be called
	 */
	public boolean isAppHandlerListenForDataEvent();
		
	
	/**
	 * returns true, if the callback handler's onDisconnect method should be called  
	 * 
	 * @return true, if the callback handler's onDisconnect method should be called
	 */
	public boolean isAppHandlerListenforDisconnectEvent();
		
	
	/**
	 * returns true, if the callback handler's onTimeout method should be called  
	 * 
	 * @return true, if the callback handler's onTimeout method should be called
	 */
	public boolean isAppHandlerListenForTimeoutEvent();
	
}
