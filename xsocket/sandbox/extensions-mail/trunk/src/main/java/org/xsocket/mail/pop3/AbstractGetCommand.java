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
import java.nio.ByteBuffer;


import org.xsocket.ClosedConnectionException;
import org.xsocket.mail.CommandName;
import org.xsocket.mail.pop3.spi.IPOP3MessageSource;
import org.xsocket.stream.INonBlockingConnection;


/**
 * 
 * 
 * @author grro@xsocket.org
 */
@CommandName("RETR")
abstract class AbstractGetCommand extends AbstractPOP3Command {

	public AbstractGetCommand() {
		
	}
	

	protected final void printMessage(ICommandContext ctx, INonBlockingConnection connection, String uid, IPOP3MessageSource source) throws IOException, ClosedConnectionException {
		if (source != null) {
			sendPositiveResponse(connection, ctx.getMaildrop().getMessageSize(uid) + " octets");
			ByteBuffer buffer = null;
			do {
				buffer = source.read();
				if (buffer != null) {
					connection.write(buffer);
				}
			} while (buffer != null);
			
			sendMultilineResponse(connection, "\r\n");
			sendMultilineResponse(connection, ".");
		} else {
			sendNegativeResponse(connection, "Error no such message");
		}
	}
}
