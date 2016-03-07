// $Id: NonBlockingConnection.java 1739 2007-09-13 07:03:38Z grro $
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

import javax.management.ObjectName;


/**
 * The server mxbean
 *
 * @author grro@xsocket.org
 */
public interface IServerMXBean {


	/**
	 * signals, if service is running
	 *
	 * @return true, if the server is running
	 */
	public boolean isOpen();


	/**
	 * returns the idle timeout in sec.
	 *
	 * @return idle timeout in sec
	 */
	public int getIdleTimeoutSec();



	/**
	 * sets the idle timeout in sec
	 *
	 * @param timeoutInSec idle timeout in sec
	 */
	public void setIdleTimeoutSec(int timeoutInSec);


	/**
	 * gets the connection timeout
	 *
	 * @return connection timeout
	 */
	public int getConnectionTimeoutSec();


	/**
	 * sets the max time for a connections. By
	 * exceeding this time the connection will be
	 * terminated
	 *
	 * @param timeoutSec the connection timeout in sec
	 */
	public void setConnectionTimeoutSec(int timeoutSec);

}
