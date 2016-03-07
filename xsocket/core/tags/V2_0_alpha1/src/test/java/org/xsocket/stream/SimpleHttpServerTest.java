// $Id: FlushOnCloseTest.java 1379 2007-06-25 08:43:44Z grro $
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
import java.util.logging.Level;

import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.methods.GetMethod;
import org.junit.Assert;
import org.junit.Test;
import org.xsocket.QAUtil;
import org.xsocket.stream.IHttpConnection.HttpHeader;


/**
*
* @author grro@xsocket.org
*/
public final class SimpleHttpServerTest {
	
	private static final String HTML_PAGE = "<?xml version=\"1.0\" encoding=\"iso-8859-1\"?>" 
										  + "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\""
										  + "\"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">"
										  + " <html xmlns=\"http://www.w3.org/1999/xhtml\">"
										  + "<head>"
										  + "<title>New.html</title>"
										  + "</head>"
										  + "<body>"
										  + "<h1>Example Page</h1>"
										  + "<p>Hello World</p>"
										  + "</body>"
										  + "</html>"; 

	
	@Test 
	public void testSimple() throws Exception {
		SimpleHttpServer server = new SimpleHttpServer(9644, new HttpMessageHandler());
		
		HttpClient httpClient = new HttpClient();
		HttpMethod method = new GetMethod("http://127.0.0.1:9644/index.html");
		int status = httpClient.executeMethod(method);
		Assert.assertEquals(200, status);

		Assert.assertEquals(HTML_PAGE, method.getResponseBodyAsString());
		
		method.releaseConnection();
		server.close();
	}
	
	
	
	private static final class HttpMessageHandler implements IHttpMessageHandler {
		
		public void onHttpMessage(IHttpConnection httpConnection) throws BufferUnderflowException, IOException {
			HttpHeader header = httpConnection.readHeader();
			if (header.getMethod().equalsIgnoreCase("GET")) {
				String requestedRessource = header.getRequestURI();
				
				if (requestedRessource.equalsIgnoreCase("/INDEX.HTML")) {
					IHttpConnection.HttpHeader responseHeader = new IHttpConnection.HttpHeader(200);
					responseHeader.addHeaderLine("Server: SimpleTestServer");
					
					byte[] bytes = HTML_PAGE.getBytes();
					responseHeader.addHeaderLine("Content-Length: " + bytes.length);
					
					httpConnection.writeHeader(responseHeader);
					httpConnection.write(bytes);
					httpConnection.write("\r\n");

				} else {
					IHttpConnection.HttpHeader responseHeader = new IHttpConnection.HttpHeader(404);
					responseHeader.addHeaderLine("Server: SimpleTestServer");
					responseHeader.addHeaderLine("Connection: close");

					httpConnection.writeHeader(responseHeader);
					httpConnection.write("\r\n");
					
					httpConnection.close();
				}
			}
		}
	}
}
