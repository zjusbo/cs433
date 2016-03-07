// $Id: Context.java 293 2006-10-09 16:15:39Z grro $

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
package org.xsocket.web.http.servlet;


import java.io.IOException;
import java.io.PrintWriter;
import java.util.Enumeration;

import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class InfoServlet extends HttpServlet {

	private static final long serialVersionUID = 5632497271094661368L;

		@Override
		protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
			process(req, res);
		}
		
		@Override
		protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
			process(req, res);
		}
		
		private void process(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
			response.setContentType("text/html");
			PrintWriter out = response.getWriter();
//			PrintWriter out = new PrintWriter(System.out); 

			  // Print the HTML header
			  out.println("<HTML><HEAD><TITLE>");
			  out.println("Request info");
			  out.println("</TITLE></HEAD>");

			  // Print the HTML body
			  out.println("<BODY><H1>Request info</H1><PRE>");
			  out.println("getCharacterEncoding: " + request.getCharacterEncoding());
			  out.println("getContentLength: " + request.getContentLength());
			  out.println("getContentType: " + request.getContentType());
			  out.println("getCharacterEncoding: " + request.getCharacterEncoding());
			  out.println("getProtocol: " + request.getProtocol());
			  out.println("getRemoteAddr: " + request.getRemoteAddr());
			  out.println("getRemoteHost: " + request.getRemoteHost());
			  out.println("getScheme: " + request.getScheme());
			  out.println("getServerName: " + request.getServerName());
			  out.println("getServerPort: " + request.getServerPort());
			  out.println("getAuthType: " + request.getAuthType());
			  out.println("getMethod: " + request.getMethod());
			  out.println("getPathInfo: " + request.getPathInfo());
			  out.println("getPathTranslated: " + request.getPathTranslated());
			  out.println("getQueryString: " + request.getQueryString());
			  out.println("getRemoteUser: " + request.getRemoteUser());
			  out.println("getContextpath: " + request.getContextPath());
			  out.println("getRequestURI: " + request.getRequestURI());
			  out.println("getServletPath: " + request.getServletPath());
			  out.println("getLocale: " + request.getLocale());
			  out.println("getLocales: ");
			  for (Enumeration en = request.getLocales(); en.hasMoreElements(); ) {
				  out.println("  " + en.nextElement() + " ");
			  }
			  
			  
			  out.println();
			  out.println("Parameters:");
			  Enumeration paramNames = request.getParameterNames();
			  while (paramNames.hasMoreElements()) {
			    String name = (String) paramNames.nextElement();
			    String[] values = request.getParameterValues(name);
			    out.println("    " + name + ":");
			    for (int i = 0; i < values.length; i++) {
			      out.println("      " + values[i]);
			    }
			  }

			  
			  
			  out.println();
			  out.println("Request headers:");
			  Enumeration headerNames = request.getHeaderNames();
			  while (headerNames.hasMoreElements()) {
			    String name = (String) headerNames.nextElement();
			    String value = request.getHeader(name);
			    out.println("  " + name + " : " + value);
			  }
/*
			  out.println();
			  out.println("Cookies:");
			  Cookie[] cookies = request.getCookies();
			  for (int i = 0; i < cookies.length; i++) {
			    String name = cookies[i].getName();
			    String value = cookies[i].getValue();
			    out.println("  " + name + " : " + value);
			  }
*/
			  // Print the HTML footer
			  out.println("</PRE></BODY></HTML>");
			  out.flush();
			  out.close();
		}
	}
