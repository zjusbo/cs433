// $Id: Index.java 41 2006-06-22 06:30:23Z grro $
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



/**
 * A index to determine the position of a record   
 * 
 * @author grro@xsocket.org
 */
final class Index {
	
	public static final int NULL = -1; 
	
	private int delimiterStartsBufferNum = NULL;
	private int delimiterStartsBufferPos = NULL;

	private int delimiterEndsBufferNum = NULL;
	private int delimiterEndsBufferPos = NULL;
	
	public boolean delimiterFound() {
		return (delimiterEndsBufferPos != NULL);
	}

	public int getDelimiterEndsBufferNum() {
		return delimiterEndsBufferNum;
	}

	public void setDelimiterEndsBufferNum(int delimiterEndsBufferNum) {
		this.delimiterEndsBufferNum = delimiterEndsBufferNum;
	}

	public int getDelimiterEndsBufferPos() {
		return delimiterEndsBufferPos;
	}

	public void setDelimiterEndsBufferPos(int delimiterEndsBufferPos) {
		this.delimiterEndsBufferPos = delimiterEndsBufferPos;
	}

	public int getDelimiterStartsBufferNum() {
		return delimiterStartsBufferNum;
	}

	public void setDelimiterStartsBufferNum(int delimiterStartsBufferNum) {
		this.delimiterStartsBufferNum = delimiterStartsBufferNum;
	}

	public int getDelimiterStartsBufferPos() {
		return delimiterStartsBufferPos;
	}

	public void setDelimiterStartsBufferPos(int delimiterStartsBufferPos) {
		this.delimiterStartsBufferPos = delimiterStartsBufferPos;
	}
}
