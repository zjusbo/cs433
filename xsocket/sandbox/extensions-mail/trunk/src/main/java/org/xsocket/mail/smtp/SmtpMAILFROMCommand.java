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

import org.xsocket.mail.CommandName;
import org.xsocket.mail.smtp.spi.SmtpRejectException;
import org.xsocket.stream.INonBlockingConnection;



/**
 * 
 * 
 * @author grro@xsocket.org
 */
@CommandName("MAIL FROM:")
final class SmtpMAILFROMCommand extends SmtpCommand {

	public SmtpMAILFROMCommand() {
		
	}
	
	@Override
	public void execute(INonBlockingConnection connection, ISmtpSession session, String argument) throws SmtpProtocolException, IOException {
		checkTrue(!session.isAuthenticationRequired(), 501, "authentification required");
				
		// check reverse-path
		checkTrue(argument.length() > 0, 504, "error missing parameter");
		checkTrue(session.getSmtpReversePath() == null, 504, "error sender is already set with " + session.getSmtpReversePath());
		
		try {
			session.setSmtpReversePath(argument);
			session.getMessageSinkService().isReversePathAccepted(session);
			sendResponse(connection, "250 " + argument + " sender OK");
		} catch (SmtpRejectException re) {
			session.setSmtpReversePath(null);
			throw re;
		}
	}	
}
