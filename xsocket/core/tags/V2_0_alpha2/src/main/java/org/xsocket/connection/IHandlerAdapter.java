/*
 *  Copyright (c) xsocket.org, 2006 - 2008. All rights reserved.
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
package org.xsocket.connection;





/**
 * Adapter interface, which is implemented by xSocket extension such as http or multiplexed.
 * By implementing this interface the adapted handler will be exported by JMX instead of the adapter. 
 * Implementing this interface has only effects on exposing JMX artifacts. It does not have any effect
 * on the business functionality.
 * 
 * @author grro
 */
public interface IHandlerAdapter  {
	
	/**
	 * returns the adapted handler
	 *  
	 * @return the adapted handler 
	 */
	public Object getAdaptee();	
}