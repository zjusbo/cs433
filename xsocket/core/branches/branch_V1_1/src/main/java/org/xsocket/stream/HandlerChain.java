// $Id: HandlerChain.java 1281 2007-05-29 19:48:07Z grro $

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
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xsocket.Resource;




/**
 * Implements a handler chain
 * <br>
 * E.g. 
 * <pre>
 *   ...
 *   HandlerChain tcpBasedSpamfilter = new HandlerChain();
 *   tcpBasedSpamfilter.addLast(new BlackIPFilter()); 
 *   tcpBasedSpamfilter.addLast(new FirstConnectRefuseFilter());
 *   
 *   HandlerChain mainChain = new HandlerChain();
 *   mainChain.addLast(tcpBasedSpamfilter);
 *   mainChain.addLast(new SmtpProtocolHandler());
 *   
 *   IMultithreadedServer smtpServer = new MultithreadedServer(port, mainChain);
 *   StreamUtils.start(server);
 *   ...
 *   
 * </pre>

 * 
 * 
 * @author grro@xsocket.org
 */
public final class HandlerChain implements  IHandler, IConnectHandler, IDisconnectHandler, IDataHandler, ITimeoutHandler, IConnectionScoped, org.xsocket.ILifeCycle {

	private static final Logger LOG = Logger.getLogger(HandlerChain.class.getName());
	
	private IHandler[] handlers = new IHandler[0];
	private Integer[] connectionScopedIndex = new Integer[0];

	private Integer[] connectHandlerChain = new Integer[0];
	private Integer[] disconnectHandlerChain = new Integer[0];
	private Integer[] dataHandlerChain = new Integer[0];
	private Integer[] timeoutHandlerChain = new Integer[0];
	private Integer[] lifeCycleChain = new Integer[0];

	private List<HandlerChain> enclosingChains = new ArrayList<HandlerChain>();
	
	
	@Resource
	private IServerContext ctx = null;

	
	

	/**
	 * constructor 
	 * 
	 */
	public HandlerChain() {
		
	}

	
	/**
	 * constructor 
	 * 
	 * @param handlers the initial handlers 
	 */
	public HandlerChain(List<IHandler> handlers) {
		for (IHandler hdl : handlers) {
			addLast(hdl);
		}
	}
	
	/**
	 * add a handler to the end og the chain
	 * 
	 * @param handler the handler to add
	 */
	public void addLast(IHandler handler) {
		int pos = handlers.length;
		handlers = incArray(handlers, handler);
		
		if (handler instanceof IConnectHandler) {
			connectHandlerChain = incArray(connectHandlerChain, pos);
		}
		
		if (handler instanceof IDisconnectHandler) {
			disconnectHandlerChain = incArray(disconnectHandlerChain, pos);
		} 

		
		if (handler instanceof IDataHandler) {
			dataHandlerChain = incArray(dataHandlerChain, pos);
		} 

		if (handler instanceof ITimeoutHandler) {
			timeoutHandlerChain = incArray(timeoutHandlerChain, pos);
		} 
		
		if (handler instanceof org.xsocket.ILifeCycle) {
			lifeCycleChain = incArray(lifeCycleChain, pos);
		} 

		
		// if handler chain
		if (handler instanceof HandlerChain) {
			((HandlerChain) handler).addEnclosingChain(this);
		}
				
		
		// handle scope
		if (handler instanceof IConnectionScoped) {
			updateScope(pos, true);
		} else {
			updateScope(pos, false);
		}
	}
	
	
	private void addEnclosingChain(HandlerChain enclosingChain) {
		if (enclosingChains == null) {
			enclosingChains = new ArrayList<HandlerChain>();
		}
		enclosingChains.add(enclosingChain);
	}
	

	private void updateScope(IHandler handler, boolean isConnectionScoped)  {
		for (int i = 0; i < handlers.length; i++) {
			if (handlers[i] == handler) {
				updateScope(i, isConnectionScoped);
				return;
			}
		}
	}
	
	private void updateScope(int pos, boolean isConnectionScoped)  {
		if (isConnectionScoped) {
			connectionScopedIndex = incArray(connectionScopedIndex, pos);
			
			if (enclosingChains != null) {
				for (HandlerChain chain : enclosingChains) {
					chain.updateScope(this, true);
				}
			}
		} 		
	}

	
	/**
	 * {@inheritDoc}
	 */
	public void onInit() {
		
		for (IHandler handler : handlers) {
			Field[] fields = handler.getClass().getDeclaredFields();
			for (Field field : fields) {
				if (field.getAnnotation(Resource.class) != null) {
					field.setAccessible(true);
					try {
						field.set(handler, ctx);
					} catch (IllegalAccessException iae) {
						LOG.warning("couldn't set HandlerContext for attribute " + field.getName() + ". Reason " + iae.toString());
					}
					
				}
			}			
		}
		
		
		for (Integer pos : lifeCycleChain) {		
			((org.xsocket.ILifeCycle) handlers[pos]).onInit();
		}
	}


	/**
	 * {@inheritDoc}
	 */
	public void onDestroy() {
		for (Integer pos : lifeCycleChain) {
			((org.xsocket.ILifeCycle) handlers[pos]).onDestroy();
		}
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public boolean onConnect(INonBlockingConnection connection) throws IOException {
		for (Integer pos : connectHandlerChain) {
			boolean result = ((IConnectHandler) handlers[pos]).onConnect(connection);
			if (result == true) {
				return true;
			}
		}
		
		return false;
	}
	

	/**
	 * {@inheritDoc}
	 */
	public boolean onDisconnect(INonBlockingConnection connection) throws IOException {
		for (Integer pos : disconnectHandlerChain) {
			boolean result = ((IDisconnectHandler) handlers[pos]).onDisconnect(connection);
			if (result == true) {
				return true;
			}
		}
		
		return false;
	}

	/**
	 * {@inheritDoc}
	 */
	public boolean onData(INonBlockingConnection connection) throws IOException {
		for (Integer pos : dataHandlerChain) {
			boolean result = ((IDataHandler) handlers[pos]).onData(connection);
			if (result == true) {
				return true;
			}
		}
		
		return false;
	}
	

	/**
	 * {@inheritDoc}
	 */
	public boolean onConnectionTimeout(INonBlockingConnection connection) throws IOException {
		for (Integer pos : timeoutHandlerChain) {
			boolean result = ((ITimeoutHandler) handlers[pos]).onConnectionTimeout(connection);
			if (result == true) {
				return true;
			}
		}
		
		return false;
	}

	
	/**
	 * {@inheritDoc}
	 */
	public boolean onIdleTimeout(INonBlockingConnection connection) throws IOException {
		for (Integer pos : timeoutHandlerChain) {
			boolean result = ((ITimeoutHandler) handlers[pos]).onIdleTimeout(connection);
			if (result == true) {
				return true;
			}
		}
		
		return false;

	}

	
	/**
	 * only for test purposes
	 * 
	 * @param pos the postion of the handler
	 * @return handler 
	 */
	IHandler getHandler(int pos) {
		return handlers[pos];
	};
	
	
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public Object clone() throws CloneNotSupportedException {
		
		if (connectionScopedIndex.length > 0) {
			HandlerChain clone = (HandlerChain) super.clone();
			clone.handlers = handlers.clone();
			
			for (int i =0; i < connectionScopedIndex.length; i++) {
				int position = connectionScopedIndex[i];
				clone.handlers[position] = (IHandler) ((IConnectionScoped) handlers[position]).clone();
			}
			
			return clone;
		
		} else {
			if (LOG.isLoggable(Level.FINEST)) {
				LOG.finest(this.getClass().getSimpleName() + " doesn't contain connection-specific handlers. return current instance as clone");
			}
			return this;
		}		
	}

	@SuppressWarnings("unchecked")
	private static <T> T[] incArray(T[] original, T newElement) {
		T[] newArray =  (T[]) copyOf(original, original.length + 1, original.getClass());
		newArray[original.length] = newElement;
		
		return newArray;
	}
	
	/**
	 * @see Arrays (Java 1.6)
	 */
	@SuppressWarnings("unchecked")
	private static <T,U> T[] copyOf(U[] original, int newLength, Class<? extends T[]> newType) {
		 T[] copy = ((Object)newType == (Object)Object[].class)
		 				? (T[]) new Object[newLength]
		 				: (T[]) Array.newInstance(newType.getComponentType(), newLength);
         System.arraycopy(original, 0, copy, 0, Math.min(original.length, newLength));
     return copy;
    }
}
