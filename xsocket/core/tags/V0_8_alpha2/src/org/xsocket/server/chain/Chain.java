// $Id$
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

package org.xsocket.server.chain;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;


import org.xsocket.server.IConnectionScoped;
import org.xsocket.server.IDataHandler;
import org.xsocket.server.IHandler;
import org.xsocket.server.INonBlockingConnection;
import org.xsocket.server.IConnectHandler;



/**
 * a chain is a handler which contains other handler, that will be executed in 
 * a chain order
 * 
 * @author grro@xsocket.org
 */
public final class Chain implements IDataHandler, IConnectHandler, IConnectionScoped {

	private static final Logger LOG = Logger.getLogger(Chain.class.getName());
	
	public static final int TAIL = Integer.MAX_VALUE;


	private int length = 0;

	private boolean isConnectionScoped = false;
	private boolean containsDataHandler = false;
	private boolean containsLifeCycleHandler = false;
	
	
	private final NodeRegistry lifeCycleHandlerNodeRegistry = new NodeRegistry();
	private final NodeRegistry dataHandlerNodeRegistry = new NodeRegistry();
	
	// prototypes incl. indexes
	private int[] connectionScopedIndex = new int[0];
	private IHandler[] handlers = new IHandler[0];

	
	private List<Chain> enclosingChains = null;
	
	
	/**
	 * constructor
	 */
	public Chain() {
		
	}
	
	public Chain(List<IHandler> handlers) {
		for (IHandler handler : handlers) {
			addHandler(handler);
		}
	}
	
	
	/**
	 * add a handler to the chain 
	 * 
	 * @param handler the handler to add
	 * @return the handler position
	 */
	public int addHandler(IHandler handler) {		

		// add to the  handler matrix 
		int position = registerHandler(handler);

		
		// if handler chain
		if (handler instanceof Chain) {
			((Chain) handler).addEnclosingChain(this);
		}
		
		
		// add to connect handler node chain
		boolean isConnectHandler = false;
		if (handler instanceof IConnectHandler) {
			registerLifeCycleHandler(position);
			containsLifeCycleHandler = true;
			isConnectHandler = true;
		}

		// add to data handler node chain
		boolean isDataHandler = false;
		if (handler instanceof IDataHandler) {
			registerDataHandler(position);
			containsDataHandler = true;
			isDataHandler = true;
		}

		// handle scope
		if (handler instanceof IConnectionScoped) {
			updateScope(handler, true);
		} else {
			updateScope(handler, false);
		}

		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("handler " + handler.getClass().getSimpleName() 
					 + " (isConnectionScoped="  + isConnectionScoped + ", isConnectHandler=" 
					 + isConnectHandler + ", isDataHandler=" + isDataHandler
					 + ") has been registered");
		}

		
		length++;
		return position;
	}
	

	

	private void registerLifeCycleHandler(int position) {		
		boolean isEmpty = lifeCycleHandlerNodeRegistry.isEmpty(); 
		Node node = lifeCycleHandlerNodeRegistry.retrieveNode(position);
			
		if (isEmpty) {
			lifeCycleHandlerNodeRegistry.setRoorNode(node);
		}
		node.setFalseSuccessorNode(lifeCycleHandlerNodeRegistry.retrieveNode(position + 1));
	}

	private void registerDataHandler(int position) {
		boolean isEmpty = dataHandlerNodeRegistry.isEmpty(); 
		Node node = dataHandlerNodeRegistry.retrieveNode(position);

		if (isEmpty) {
			dataHandlerNodeRegistry.setRoorNode(node);
		}
		node.setFalseSuccessorNode(dataHandlerNodeRegistry.retrieveNode(position + 1));

	}


	private synchronized int registerHandler(IHandler handler)  {
		int position = handlers.length;
				
		// add to handler array
		IHandler[] newHandlers = new IHandler[handlers.length + 1];
		System.arraycopy(handlers, 0, newHandlers, 0, handlers.length);
		newHandlers[handlers.length] = handler; 
		handlers = newHandlers;		
		
		return position;
	}


	
	private synchronized void updateScope(IHandler handler, boolean isConnectionScoped)  {
		int position = getPosition(handler);
		
		// if connection scoped -> add to index
		if (isConnectionScoped) {
			this.isConnectionScoped = true; 
				
			int[] newConnectionScopedIndex = new int[connectionScopedIndex.length + 1];
			System.arraycopy(connectionScopedIndex, 0, newConnectionScopedIndex, 0, connectionScopedIndex.length);
			newConnectionScopedIndex[connectionScopedIndex.length] = position; 
			connectionScopedIndex = newConnectionScopedIndex;	
		
			if (enclosingChains != null) {
				for (Chain chain : enclosingChains) {
					chain.updateScope(this, this.isConnectionScoped);
				}
			}
		} 		
	}

	
	private synchronized IHandler getHandler(int position) {
		if (position < handlers.length) {
			return (IHandler) handlers[position]; 
		} else {
			return null;
		}		
	}

	private int getPosition(IHandler handler) {
		for (int i = 0; i < handlers.length; i++) {
			if (handlers[i] == handler) {
				return i;
			}
		}
		
		return -1;
	}

	/**
	 * @see IConnectHandler
	 */
	public boolean onConnectionOpening(INonBlockingConnection connection) throws IOException {

		boolean result = false;

		if (containsLifeCycleHandler) {
			Node node = lifeCycleHandlerNodeRegistry.getRootNode();
			while (node != null) {
				IConnectHandler handler = (IConnectHandler) getHandler(node.getId());
				if (handler != null) {
					if (LOG.isLoggable(Level.FINEST)) {
						LOG.finest("calling onConnectionOpening on " + handler.getClass().getSimpleName() + "#" + handler.hashCode());
					}
		
					result = handler.onConnectionOpening(connection);
					if (LOG.isLoggable(Level.FINEST)) {
						LOG.finest("result of readIncommingData on " + handler.getClass().getSimpleName() + "#" + handler.hashCode() + " " + result);
					}

					if (result) {
						node = node.getTrueSuccessorNode();
					} else {
						node = node.getFalseSuccessorNode();
					}
				} else {
					node = null;
				}
			}
		}

		return result;
	}
	


	/**
	 * @see IDataHandler
	 */
	public boolean onData(INonBlockingConnection connection) throws IOException {
		boolean result = false;

		if (containsDataHandler) {
			Node node = dataHandlerNodeRegistry.getRootNode();
			while (node != null) {
				IDataHandler handler = (IDataHandler) getHandler(node.getId());
				if (handler != null) {
					if (LOG.isLoggable(Level.FINEST)) {
						LOG.finest("calling  processIncommingData on " + handler.getClass().getSimpleName() + "#" + handler.hashCode());
					}
		
					result = handler.onData(connection);
					if (LOG.isLoggable(Level.FINEST)) {
						LOG.finest("result of readIncommingData on " + handler.getClass().getSimpleName() + "#" + handler.hashCode() + " " + result);
					}

					if (result) {
						node = node.getTrueSuccessorNode();
					} else {
						node = node.getFalseSuccessorNode();
					}
				} else {
					node = null;
				}
			} 
		}

		return result;
	}
	
	
	private synchronized void addEnclosingChain(Chain enclosingChain) {
		if (enclosingChains == null) {
			enclosingChains = new ArrayList<Chain>();
		}
		enclosingChains.add(enclosingChain);
	}
	
	
	/**
	 * @see Object
	 */
	@Override
	public Object clone() throws CloneNotSupportedException {
		
		if (isConnectionScoped) {
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


	/**
	 * @see Object
	 */
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder(this.getClass().getSimpleName() + "#" + this.hashCode() + "\n\n");
		
		sb.append("registered prototypes:\n");
		for(int i = 0; i < handlers.length; i++) {
			sb.append(handlers[i].getClass().getSimpleName() + "#" + handlers[i].hashCode());
			for (int j = 0; j < connectionScopedIndex.length; j++) {
				if (connectionScopedIndex[j] == i) {
					sb.append(" (Scope=connection)");
				}
			}
			sb.append("\n");
		}
		sb.append("\n");
 
		sb.append("LifeCycleHandler Nodes:\n" + printNodeRegistry(lifeCycleHandlerNodeRegistry) + "\n");
		sb.append("\n");
		
		sb.append("DataHandler Nodes:\n" + printNodeRegistry(dataHandlerNodeRegistry) + "\n");		
		sb.append("\n");
		
		return sb.toString();
	}
	
	
	private String printNodeRegistry(NodeRegistry nodeRegistry) {
		StringBuilder sb = new StringBuilder("nodeId, node, trueSucId, falseSucId, isRoot\n");

		
		Node root = nodeRegistry.getRootNode();
		List<Node> nodes = nodeRegistry.getAllNodes();
		Collections.sort(nodes, new Comparator<Node>() {
			public int compare(Node node1, Node node2) {
				return new Integer(node1.getId()).compareTo(new Integer(node2.getId())); 
			}});
		
		for (Node node : nodes) {
			
			boolean isRoot = (root == node);
			
			IHandler trueSuccessor = null;
			if (node.getTrueSuccessorNode() != null) {
				trueSuccessor = getHandler(node.getTrueSuccessorNode().getId());
			}

			IHandler falseSuccessor = null;
			if (node.getFalseSuccessorNode() != null) {
				falseSuccessor = getHandler(node.getFalseSuccessorNode().getId());
			}

			sb.append(node.getId() + ", " + printHandlerClass(getHandler(node.getId())) + ", " + printHandlerClass(trueSuccessor) + ", " + printHandlerClass(falseSuccessor) + ", " + isRoot + "\n");
		}
		
		return sb.toString();
	}
	
	
	private String printHandlerClass(IHandler handler) {
		if (handler != null) {
			return handler.getClass().getSimpleName() + "#" + handler.hashCode();
		} else {
			return "null";
		}
	}
		

	private static final class NodeRegistry {
		private final Map<Integer, Node> nodes = new HashMap<Integer, Node>();
		private Node rootNode = null;
		
		
		public void setRoorNode(Node rootNode) {
			this.rootNode = rootNode;
		}
		
		public Node getRootNode() {
			return rootNode;
		}
		
		public boolean isEmpty() {
			return nodes.isEmpty();
		}
		
		public Node retrieveNode(int id) {
			if (nodes.containsKey(id)) {
				return nodes.get(id);
			} else {
				Node node = new Node(id);
				registerNode(node);
				return node;
			}
		}
		
		private void registerNode(Node node) {
			nodes.put(node.getId(), node);
		}
		
		public List<Node> getAllNodes() {
			List<Node> result = new ArrayList<Node>();
			for (int id : nodes.keySet()) {
				result.add(nodes.get(id));
			}
			return result;
		}
		
	}
}
