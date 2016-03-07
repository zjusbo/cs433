// $Id: SmtpTestClient.java 41 2006-06-22 06:30:23Z grro $
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
package org.xsocket.server;

import java.io.IOException;


public final class BulkSslTestMailSender {


	/**
	 * @param args
	 */
	public static void main(final String[] args) throws IOException {
		System.out.println("start test client for server " +  args[0] + ":" + args[1] + " with " + args[2] + " threads");
		int size = Integer.parseInt(args[2]); 
		for (int i = 0; i < size; i++) {
			Thread t = new Thread() {
				public void run() {
					try {
						SendSslTestMail sender = new SendSslTestMail();
						while (true) {
							boolean success = sender.send(args[0], Integer.parseInt(args[1]));
							if (success) {
								System.out.print(".");
							}
						}
					} catch (Exception e) {
						throw new RuntimeException(e);
					}
				};
			};
			t.start();
		}
	}	
}
