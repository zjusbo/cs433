//$Id: Resource.java 1049 2007-03-21 16:42:48Z grro $

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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;




/**
 * The synchronized annotation determines the synchronization scope of an entity. For
 * xSocket the synchronized annotation can only be used within a {@link IHandler} 
 * class of the stream package. <br>
 * 
 * By default the handler of the stream package is synchronized on the connection level.
 * That means, for the same connection the handler callback methods will be called in a 
 * serialized manner. Sometime it is desirable that the synchronization should be done 
 * by the handler itself. The synchronization can be deactivated by annotate the handler 
 * as non-synchronized (level OFF). By switching off the synchronization, the correct 
 * order of the call back method calls is not guaranteed. Because the call back method 
 * will be performed within unsynchronized worker threads, race condition could occur. <br>
 * 
 *  E.g.
 *  
 * <pre>
 *  ... 
 *  import static org.xsocket.Synchronized.Mode.*;
 *  import org.xsocket.Synchronized;
 *  import org.xsocket.stream.BlockingConnection;
 * 
 * 
 *  &#064Synchronized(OFF)   // deactivate the synchronization by setting scope OFF
 *  class MyHandler implements IDataHandler {
 *
 *     public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
 *         ...
 *         synchronized (connection) {
 *              ByteBuffer[] data = connection.readByteBufferByDelimiter(DELIMITER);
 *              ...
 *         }
 *         ...
 *         
 *         return true;
 *     }
 *  }
 * </pre> 
 * 
 * 
 * @author grro@xsocket.org
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Synchronized {
	
	public enum Mode { OFF, CONNECTION }
	
	Mode value() default Mode.CONNECTION;
}
