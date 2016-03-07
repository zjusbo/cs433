// $Id: TimeoutException.java 910 2007-02-12 16:56:19Z grro $
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
package org.xsocket;

import java.io.IOException;


/**
 * Checked exception thrown when the imeout is reached 
 * by invoking or completing an I/O operation 
 * 
 * @author grro@xsocket.org 
 */
public class TimeoutException extends IOException {


	private static final long serialVersionUID = -8528028632078265758L;

	/**
	 * @see IOException
	 */
	public TimeoutException() {
		super();
	}

	/**
	 * @see IOException
	 */
	public TimeoutException(String msg) {
		super(msg);
	}
}
