// $Id: Chain.java 41 2006-06-22 06:30:23Z grro $
/**
 * Copyright 2006 xsocket.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.xsocket.server.handler.chain;

import java.io.IOException;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
 
import org.xsocket.server.IConnectionScoped;
import org.xsocket.server.IDataHandler;
import org.xsocket.server.IHandler;
import org.xsocket.server.INonBlockingConnection;
import org.xsocket.server.IConnectHandler;
import org.xsocket.server.ITimeoutHandler;



/**
 * implements the chain interface
 * 
 * 
 * @author grro@xsocket.org
 */
public final class Chain implements IConnectHandler, IDataHandler, ITimeoutHandler, IConnectionScoped {

	private static final Logger LOG = Logger.getLogger(Chain.class.getName());
	
	private IHandler[] handlers = new IHandler[0];
	private Integer[] connectionScopedIndex = new Integer[0];

	private Integer[] connectHandlerChain = new Integer[0];
	private Integer[] dataHandlerChain = new Integer[0];
	private Integer[] timeoutHandlerChain = new Integer[0];

	private List<Chain> enclosingChains = new ArrayList<Chain>();

	/**
	 * constructor
	 *
	 */
	public Chain() {
		
	}
	
	
	
	/**
	 * constructor 
	 * 
	 * @param hdls the initial handlers 
	 */
	public Chain(List<IHandler> hdls) {
		for (IHandler hdl : hdls) {
			addLast(hdl);
		}
	}
	
	/**
	 * @see IChain
	 */
	public void addLast(IHandler handler) {
		int pos = handlers.length;
		handlers = incArray(handlers, handler);
		
		if (handler instanceof IConnectHandler) {
			connectHandlerChain = incArray(connectHandlerChain, pos);
		} 
		
		if (handler instanceof IDataHandler) {
			dataHandlerChain = incArray(dataHandlerChain, pos);
		} 

		if (handler instanceof ITimeoutHandler) {
			timeoutHandlerChain = incArray(timeoutHandlerChain, pos);
		} 
		
		
		// if handler chain
		if (handler instanceof Chain) {
			((Chain) handler).addEnclosingChain(this);
		}
				
		
		// handle scope
		if (handler instanceof IConnectionScoped) {
			updateScope(pos, true);
		} else {
			updateScope(pos, false);
		}
	}
	
	
	private void addEnclosingChain(Chain enclosingChain) {
		if (enclosingChains == null) {
			enclosingChains = new ArrayList<Chain>();
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
				for (Chain chain : enclosingChains) {
					chain.updateScope(this, true);
				}
			}
		} 		
	}


	/**
	 * @see IConnectHandler
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
	 * @see IDataHandler
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
	 * @see ITimeoutHandler
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
	 * @see ITimeoutHandler
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
	 * for test purposes
	 * 
	 * @param pos the postion of the handler
	 * @return handler 
	 */
	IHandler getHandler(int pos) {
		return handlers[pos];
	};
	
	
	
	/**
	 * @see Object
	 */
	@Override
	public Object clone() throws CloneNotSupportedException {
		
		if (connectionScopedIndex.length > 0) {
			Chain clone = (Chain) super.clone();
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
