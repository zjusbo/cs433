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
package org.xsocket.bayeux.http;




import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.BufferUnderflowException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xsocket.Execution;
import org.xsocket.IDataSink;
import org.xsocket.ILifeCycle;
import org.xsocket.Resource;
import org.xsocket.connection.IConnectionScoped;
import org.xsocket.connection.IServer;
import org.xsocket.connection.http.server.HttpProtocolAdapter;




/** 
 *
 * @author grro@xsocket.org
 */
final class BayeuxUtils {
	
	private static final Logger LOG = Logger.getLogger(BayeuxUtils.class.getName());
	
	
	private static String versionInfo = null;
	
	private BayeuxUtils() { }
		
	
	
	/**
	 * get the version info
	 * 
	 * @return the version info
	 */
	public static String getVersionInfo() {
		
		if (versionInfo == null) {
			
			versionInfo = "<unknown>";
			
			try {
				InputStreamReader isr = new InputStreamReader(BayeuxUtils.class.getResourceAsStream("/org/xsocket/bayeux/http/version.txt"));
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
