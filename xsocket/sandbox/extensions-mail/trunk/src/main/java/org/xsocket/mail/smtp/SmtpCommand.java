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
package org.xsocket.mail.smtp;



import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xsocket.stream.INonBlockingConnection;



/**
 * 
 * 
 * @author grro@xsocket.org
 */
abstract class SmtpCommand {
	
	private static final Logger LOG = Logger.getLogger(SmtpCommand.class.getName());


	public static final String LINE_DELIMITER = "" + '\r' + '\n';	
	public static final String DATA_DELIMITER = "" + '\r' + '\n' + '.'  + '\r' + '\n';

	
	public abstract void execute(INonBlockingConnection connection, ISmtpSession session, String argument) throws IOException, SmtpProtocolException;
		

	protected static void sendResponse(INonBlockingConnection connection, String msg) throws IOException {
		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("send " + msg + LINE_DELIMITER);
		}
		connection.write(msg + LINE_DELIMITER, "US-ASCII");			
	}
	
	
	protected final void checkTrue(boolean condition, int smtpCode, String message) throws SmtpProtocolException {
		if (!condition) {
			throw new SmtpProtocolException(smtpCode, message);
		}
	}
	
	
	protected final void sendMultilineResponse(INonBlockingConnection connection, int code, String[] lines) throws IOException {
		if (lines.length > 1) {
			for (int i = 0; i < (lines.length - 1); i++) {
				sendResponse(connection, code + "-" + lines[i]);
			}
		}
		
		sendResponse(connection, code + " " + lines[lines.length - 1]);
	}

}

