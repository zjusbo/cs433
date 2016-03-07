/*
 *  Copyright (c) xsocket.org, 2006 - 2008. All rights reserved.
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
package org.xsocket.connection.multiplexed;

import java.io.InputStreamReader;
import java.io.LineNumberReader;





/**
 * utility class 
 * 
 * @author grro@xsocket.org
 */
final class MultiplexedUtils {
	


	private MultiplexedUtils() { }

	
	private static String versionInfo = null;
	
	

	
	/**
	 * get the version info
	 * 
	 * @return the version info
	 */
	public static String getVersionInfo() {
		if (versionInfo == null) {

			versionInfo = "<unknown>";
			
			try {
				InputStreamReader isr = new InputStreamReader(MultiplexedUtils.class.getResourceAsStream("/org/xsocket/connection/multiplexed/version.txt"));
				if (isr != null) {
					LineNumberReader lnr = new LineNumberReader(isr);
					String line = null;
					do {
						line = lnr.readLine();
						if (line != null) {
							if (line.startsWith("Implementation-Version=")) {
								versionInfo = line.substring("Implementation-Version=".length(), line.length()).trim();
							}
						}
					} while (line != null);
		
					lnr.close();
				}
			} catch (Exception ignore) { }
			
		} 
		
		return versionInfo;
	}
}
