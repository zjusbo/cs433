// $Id: LifeCycleTest.java 1379 2007-06-25 08:43:44Z grro $
/*
 *  Copyright (c) xsocket.org, 2006. All rights reserved.
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



import org.junit.Test;






/**
*
* @author grro@xsocket.org
*/
public final class RepeatedCloseTest  {



	@Test 
	public void testRepeatedClose() throws Exception {
		IBlockingConnection connection = new BlockingConnection("www.web.de", 80);
		connection.setAutoflush(false);
		
		connection.write("GET /index.html \r\n");
		connection.write("User-Agent: xSocket\r\n");
		connection.write("Accept: text/xml,application/xml,application/xhtml+xml,text/html;q=0.9,text/plain;q=0.8,image/png,*/*;q=0.5\r\n");
		connection.write("Accept-Language: de-de,de;q=0.8,en-us;q=0.5,en;q=0.3\r\n");
		connection.write("Accept-Encoding: gzip,deflate\r\n");
		connection.write("Accept-Charset: ISO-8859-1,utf-8;q=0.7,*;q=0.7\r\n");
		connection.write("Keep-Alive: 300\r\n");
		connection.write("Connection: close\r\n");
		connection.write("\r\n\r\n");
		connection.flush();
		
		

		String responseHeader = connection.readStringByLength(50);
		connection.close();
		
	}
}
