// $Id: FlushOnCloseTest.java 1017 2007-03-15 08:03:05Z grro $
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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.junit.Assert;
import org.junit.Test;




/**
*
* @author grro@xsocket.org
*/
public final class NonBlockingClientTest {
	

	@Test 
	public void testSimple() throws Exception {
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		PrintStream consoleOut = System.out;
		System.setOut(new PrintStream(os));
		
		NonBlockingClient.main(new String[] { "www.web.de", "80", "/" });
		
		System.setOut(consoleOut);
		String out = os.toString("UTF-8");
		Assert.assertTrue(out.contains("OK"));
		
	}
}
