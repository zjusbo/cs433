// $Id: IHandler.java 1223 2007-05-08 06:18:30Z grro $
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

import org.xsocket.Synchronized;



/**
 * A marker interface for a handler <br><br>
 * 
 * Specific handlers defines <code>on&#060event&#062</code> callback methods. <br>
 * By default the callback methods are <i>connection-scoped</i> synchronized. For the <i>same</i>
 * connection instance the callback method will be called in a serialized manner.  
 * The synchronization behavior of  handler can be controlled by the {@link Synchronized}
 * annotation. 
 * 
 * @author grro@xsocket.org
 */
public interface IHandler {

}
