// $Id: Node.java 41 2006-06-22 06:30:23Z grro $
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


/**
 * a node of a chain
 * 
 * @author grro@xsocket.org
 */
final class Node {

	private int id = 0;
	private Node trueSuccessor = null;
	private Node falseSuccessor = null;
	
	public Node(int id) {
		this.id = id;
	}
	
	public int getId() {
		return id;
	}
	
	public void setTrueSuccessorNode(Node successor) {
		this.trueSuccessor = successor;
	}
	
	public Node getTrueSuccessorNode() {
		return trueSuccessor;
	}

	public void setFalseSuccessorNode(Node successor) {
		this.falseSuccessor = successor;
	}
	
	public Node getFalseSuccessorNode() {
		return falseSuccessor;
	}
}
