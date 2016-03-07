/*
 * Copyright (c) xlightweb.org, 2006 - 2010. All rights reserved.
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
package org.xsocket.connection;




import org.junit.Assert;
import org.junit.Test;
import org.xsocket.connection.BlockingConnection;
import org.xsocket.connection.IBlockingConnection;




/**
*
* @author grro@xsocket.org
*/
public final class FlushTest {


	
	@Test 
	public void testLiveFlush() throws Exception {
		
		IBlockingConnection con = new BlockingConnection("www.web.de", 80);
		con.setAutoflush(false);
		
		con.write("GET / HTTP/1.1\r\n");
		con.write("Host: www.web.de\r\n");
		con.write("\r\n");
		con.flush();
		
		
		String rawHeader = con.readStringByDelimiter("\r\n");
		Assert.assertTrue(rawHeader.indexOf("200") != -1);
		
		con.close();
		
	}
}
