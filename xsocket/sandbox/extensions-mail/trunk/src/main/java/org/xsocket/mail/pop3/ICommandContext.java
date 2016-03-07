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

import org.xsocket.mail.pop3.spi.IPOP3AuthenticatorService;
import org.xsocket.mail.pop3.spi.IPOP3Maildrop;
import org.xsocket.mail.pop3.spi.IPOP3MaildropService;




/**
 * command context  
 * 
 * @author grro@xsocket.org
 */
interface ICommandContext {
	 
	enum State {
		AUTHORIZATION,
		TRANSACTION;
	}

	
	public State getState();
	
	public void setState(State state);
	
	public IPOP3MaildropService getMaildropService();
	
	public void clear();

	public String getUser();

	public void setUser(String user);

	public IPOP3AuthenticatorService getAuthenticator();

	public void setMaildrop(IPOP3Maildrop maildrop);
	
	public IPOP3Maildrop getMaildrop();
	
	public boolean addMessageNumberToDelete(Integer msgNumber);
	
	public boolean isMessageNumberMarkedAsDeleted(Integer msgNumber);

	public void clearMessageNumbersToDelete();

	public Integer[] getMessageNumbersToDelete();
	
	public String getMessageUid(Integer msgNumber);
	
	public Integer[] mapUidToMessageNumber(String[] uids);
}