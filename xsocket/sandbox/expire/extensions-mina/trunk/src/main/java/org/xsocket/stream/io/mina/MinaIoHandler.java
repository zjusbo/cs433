// $Id: MemoryManager.java 1304 2007-06-02 13:26:34Z grro $
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
package org.xsocket.stream.io.mina;




import java.util.logging.Logger;

import org.apache.mina.common.IdleStatus;
import org.apache.mina.common.IoHandlerAdapter;
import org.apache.mina.common.IoSession;


/**
*
*  
* @author grro@xsocket.org
*/
final class MinaIoHandler extends IoHandlerAdapter {
	
	private static final Logger LOG = Logger.getLogger(MinaIoHandler.class.getName());

		
	private IMinaIoHandlerCallback callback = null;
		
	public MinaIoHandler(IMinaIoHandlerCallback callback) {
		this.callback = callback;
	}
		
		
	public void sessionClosed(IoSession session) throws Exception {
		// retrieve sesion-specific xSocket io handler and notify disconnect 
		getSessionHandler(session).onDisconnect();
	}

		
	public void sessionOpened(IoSession session) throws Exception {
		// notify connect		
		callback.onConnect(session);
	}

		
	public void messageReceived(IoSession session, Object message) throws Exception {
		
		// retrieve read buffer and slice read part
		// (should the remainig part be reused? will there be modifications for mina V2.0 final?)
		org.apache.mina.common.ByteBuffer rb = (org.apache.mina.common.ByteBuffer) message;
		org.apache.mina.common.ByteBuffer read = rb.slice();

		// retrieve sesion-specific xSocket io handler and notify received data 		
		getSessionHandler(session).onData(read.buf());			
	}
	
	
	@Override
	public void sessionIdle(IoSession session, IdleStatus status) throws Exception {
		
		// retrieve sesion-specific xSocket io handler and notify idle timeout 		
		getSessionHandler(session).onIdle();
	}
	
	
	@Override
	public void exceptionCaught(IoSession session, Throwable cause) throws Exception {
		LOG.warning("exception occured for " + session.toString() + " reason: ");
		cause.printStackTrace();
	}
		
	private IoHandler getSessionHandler(IoSession session) {
		return (IoHandler) session.getAttachment();
	}
}
