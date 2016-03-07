// $Id: AbstractGetCommand.java 335 2006-10-16 06:10:05Z grro $

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
package org.xsocket.mail.pop3;



import java.io.IOException;

import org.xsocket.mail.CommandName;
import org.xsocket.stream.INonBlockingConnection;


/**
 * 
 * 
 * @author grro@xsocket.org
 */
@CommandName("UIDL") 
final class UIDLCommand extends AbstractPOP3Command {

	public UIDLCommand() {
		
	}
	
	@Override
	public void execute(ICommandContext ctx, INonBlockingConnection connection, String[] arguments) throws IOException, POP3ProtocolException {
		checkState(ctx, ICommandContext.State.TRANSACTION, "have to be in TRANSACTION state");
			
		if (arguments.length == 1) {
			Integer msgNumber = Integer.parseInt(arguments[0]);
				
			if (ctx.isMessageNumberMarkedAsDeleted(msgNumber)) {
				sendNegativeResponse(connection, "message " + msgNumber + " is deleted");
			} else {
					
				String uid = ctx.getMessageUid(msgNumber); 
				if (uid != null) {
					sendPositiveResponse(connection, msgNumber + " " + uid);
				} else {
					sendNegativeResponse(connection, "Error no such message");
				}
			}
			
		} else {
			String[] uids = ctx.getMaildrop().getAllMessagesUid();
			Integer[] msgNumbers = ctx.mapUidToMessageNumber(uids);
			
			if (msgNumbers.length > 0) {
				sendPositiveResponse(connection, "");
				for (Integer msgNumber : msgNumbers) {
					if (!ctx.isMessageNumberMarkedAsDeleted(msgNumber)) {
						sendMultilineResponse(connection, msgNumber + " " + ctx.getMessageUid(msgNumber));
					}
				}
				sendMultilineResponse(connection, ".");
			} else {
				sendNegativeResponse(connection, "Error no messages");
			}
		}
	}
}

