/*
 *  Copyright (c) xsocket.org, 2006-2008. All rights reserved.
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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.junit.Assert;
import org.junit.Test;




/**
*
* @author grro@xsocket.org
*/
public final class NonBlockingClientTest {
	
	
	
	public static void main(String... args) throws Exception {
		for (int i = 0; i < 1000; i++) {
			new NonBlockingClientTest().testLive();
			System.out.print(i);
		}
	}

	@Test 
	public void testLive() throws Exception {
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		PrintStream consoleOut = System.out;
		System.setOut(new PrintStream(os));
		
		NonBlockingClient.main(new String[] { "www.web.de", "80", "/" });
		
		System.setOut(consoleOut);
		String[] out = os.toString("UTF-8").split("\r\n");
		
		if (out[0].contains("200")) {
			System.out.println("passed");
		} else {
			throw new Exception("failed");
		}
	}
}
