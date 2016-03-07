// $Id: ByteBufferQueue.java 41 2006-06-22 06:30:23Z grro $
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



package org.xsocket;

import java.nio.ByteBuffer;
import java.util.Iterator;

import org.xsocket.util.TextUtils;


/**
 * Queue to storethe incoming and outgoing data   
 * 
 * @author grro@xsocket.org
 */
final class ByteBufferQueue implements Iterable<ByteBuffer> {

	private Node<ByteBuffer> head = null;
	private Node<ByteBuffer> tail = null;	
	private int numberOfNodes = 0;
	

	public synchronized void offerHead(ByteBuffer element) {
		if (head == null) {
			offer(element);
		} else {
			Node<ByteBuffer> node = new Node<ByteBuffer>(element);
			node.setSuccessor(head);
			head = node;
		
			numberOfNodes++;
		}
	}

	
	public synchronized ByteBuffer poll() {
		if (head == null) {
			return null;
		} else {
			Node<ByteBuffer> node = head;
			head = node.getSuccessor();
		
			numberOfNodes--;
			
			if (numberOfNodes == 0) {
				clear();
			}
			
			return node.getElement();
		}
	}
	
	
	synchronized boolean offer(ByteBuffer element) {
		Node<ByteBuffer> node = new Node<ByteBuffer>(element);
		if (tail == null) {
			tail = node;
			if (head == null) {
				head = tail;
			}
		} else {
			tail.setSuccessor(node);
			tail = node;
		}
	
		numberOfNodes++;
		
		return true;
	}
	
	
	public boolean isEmpty() {
		return (head == null);
	}
	
	public synchronized ByteBuffer[] drain() {
		ByteBuffer[] result = new ByteBuffer[numberOfNodes];
		for (int i = 0; i < result.length; i++) {
			result[i] = poll();
		}		
		return result;
	}
 
	public int size() {
		int size = 0;
		Node<ByteBuffer> node = head;
		while (node != null) {
			ByteBuffer buf = node.getElement();
			size += (buf.limit() - buf.position());
			node = node.getSuccessor();
		}
		
		return size;
	}
	
	private void clear() {
		head = null;
		tail = null;	
		numberOfNodes = 0;
	}
	
	public Iterator<ByteBuffer> iterator() {
		return new Iterator<ByteBuffer>() {
			private Node<ByteBuffer> next = head;
			
			public boolean hasNext() {				
				return (next != null);
			}
			
			public ByteBuffer next() {
				ByteBuffer element = next.getElement();
				next = next.getSuccessor();
				
				return element;
			}
			
			public void remove() {
				throw new UnsupportedOperationException("operation remove is not supported");	
			}
		};
	}
	
	
	private static class Node<T> {

		private T element = null;
		private Node<T> successor = null;
		  
		Node(T element) {
			this.element = element;
		}
		
		T getElement() {
			return element;
		}
		
		void setElement(T element) {
			this.element = element;
		}
		
		Node<T> getSuccessor() {
			return successor;
		}


		void setSuccessor(Node<T> successor) {
			this.successor = successor;
		}
	}
	
	@Override
	public String toString() {
		if (head != null) {
			Node<ByteBuffer> node = head;
			StringBuilder sb = new StringBuilder();
			do {
				sb.append(TextUtils.toString(node.getElement().duplicate(), "UTF-8"));
				node = node.getSuccessor();
			} while (node != null);
			
			return sb.toString();
		} else {
			return "";
		}

	}
}
