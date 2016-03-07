// $Id: SSLTestDirect.java 1023 2007-03-16 16:27:41Z grro $
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


import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;



/**
*
* @author grro@xsocket.org
*/
public interface IHttpConnection extends INonBlockingConnection {

	public HttpHeader readHeader() throws IOException, BufferUnderflowException;
	
	public void writeHeader(HttpHeader header) throws IOException;

	

	public static final class HttpHeader {
	
		private String firstLine = null; 
		private List<String> headerLines = null;
	
		
		HttpHeader(int statusCode) {
			switch (statusCode) {
			case 200:
				firstLine = "HTTP/1.1 200 OK";
				break;

			case 404:
				firstLine = "HTTP/1.1 404 Not found";
				break;

				
			default:
				firstLine = "HTTP/1.1 400 Bad Request";
				break;
			}
			
			 headerLines = new ArrayList<String>();
		}
	
		HttpHeader(String rawHeader) {
			int posFirstCRLF = rawHeader.indexOf("\r\n");
			firstLine = rawHeader.substring(0, posFirstCRLF);
			headerLines = Arrays.asList(rawHeader.substring(posFirstCRLF + 1, rawHeader.length()).split("\r\n"));
		}
	
		public String getMethod() {
			return firstLine.split(" ")[0];
		}
		
		public final String getRequestURI() {
			return firstLine.split(" ")[1]; 
		}
		
		public String[] getHeaderLines() {
			return headerLines.toArray(new String[headerLines.size()]);
		}
		
		public void addHeaderLine(String headerLine) {
			headerLines.add(headerLine);
		}
		
		String getFirstLine() {
			return firstLine;
		}
	}	
}
