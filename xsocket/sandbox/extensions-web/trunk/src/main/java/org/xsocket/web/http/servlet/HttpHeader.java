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
import java.io.Serializable;
import java.net.URLDecoder;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;



/**
 * 
 * 
 * @author grro@xsocket.org
 */
final class HttpHeader {

	private static final Logger LOG = Logger.getLogger(HttpHeader.class.getName());	

	private static final SimpleDateFormat FORMATS[] = { new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US),
														new SimpleDateFormat("EEEEEE, dd-MMM-yy HH:mm:ss zzz", Locale.US),
														new SimpleDateFormat("EEE MMMM d HH:mm:ss yyyy", Locale.US)
		    										  };
	
	private String requestMethod = null;
	private String requestedRessource = null;
	private String protocol = null;

	private String queryString = null;
	private Map<String, String[]> parameters = null;

	private final Map<String, List<String>> headers = new HashMap<String, List<String>>();


	HttpHeader() {
		
	}
	
	HttpHeader(String rawheader) throws IOException {
		String[] lines = rawheader.split("\r\n");
		
		// parse method request line
		if (lines.length > 0) {
			String[] tokens = lines[0].split(" ");
			
			if (tokens.length > 0) {
				
				// retrieve method
				requestMethod = tokens[0].toUpperCase();
				
				
				if (tokens.length > 1) {
					
					// get requested resource string
					requestedRessource = tokens[1];
					
					// extract protocol if exists
					protocol = null;
					if (tokens.length > 2) {
						protocol = tokens[2];
					} else {
						LOG.fine("no protocol version is given: " + rawheader);
					}				
 
					// specific parsing for the method types 
					if (requestMethod.equalsIgnoreCase("GET") || requestMethod.equalsIgnoreCase("POST")) {
						int idxQ = requestedRessource.indexOf("?");
						if (idxQ != -1) {
							queryString = requestedRessource.substring(idxQ + 1, requestedRessource.length());
							requestedRessource = requestedRessource.substring(0, idxQ);			
						}
					}
						
					
					// parse header
					parseHeader(lines);
				}
			}
		}
	}
	
	public boolean hasBody() {
		return (getMethod().equalsIgnoreCase("PUT") || getMethod().equalsIgnoreCase("POST"));
	}

	
	private void parseHeader(String[] lines) {
		String[] headerLines = null;
		if (lines.length > 1) {
			headerLines = new String[lines.length - 1];
			System.arraycopy(lines, 1, headerLines, 0, headerLines.length);
		} else {
			headerLines = new String[0];
		}
		
		for (int i = 0; i < headerLines.length; i++) {
			int idx = headerLines[i].indexOf(":");
			if (idx != -1) {
				addHeader(headerLines[i].substring(0, idx), headerLines[i].substring(idx + 1, headerLines[i].length()).trim());
			} else {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.info("invalid header entry: " + headerLines[i]);
				}
			}
		}
	}

	
	
	
	public final String getMethod() {
		return requestMethod;
	}
	
	public final String getProtocol() {
		return protocol;
	}
	
	public String getScheme() {
		return "http";
	}



	public final int getContentLength() {
		String s = getHeader("Content-Length");
		if (s == null) {
			return -1;
		} else {
			return Integer.parseInt(s);
		}
	}
	

	public String getContentType() {
		return getHeader("Content-Type");
	}

	public void setContentType(String contentType) {
		addHeader("Content-Type", contentType);
	}
	
	public final String getTransferEncoding() {
		return getHeader("Transfer-Encoding");
	}

	
	public final String getQueryString() {
		return queryString;
	}
	
	public final String getRequestURI() {
		String uri = requestedRessource;
		if (requestMethod.equalsIgnoreCase("GET")) {
			int i = uri.indexOf("?");
			if (i != -1) {
				uri = requestedRessource.substring(0, i);
			}
		}
		return uri; 
	}
	

	public final String getParameter(String name) {
		String[] params = getParameterValues(name);
		if (params != null) {
			return params[0];
		} else {
			return null;
		}
	}
	
	public final Map getParameterMap() {
		return Collections.unmodifiableMap(getParameters());
	}
	
	public final Enumeration getParameterNames() {
		return Collections.enumeration(getParameters().keySet());
	}
	
	public final String[] getParameterValues(String name) {
		return getParameters().get(name);
	}
	
	protected final void addParameter(String name, String value) {
		Map<String, String[]> params = getParameters();
		if (params.containsKey(name)) {
			String[] values = params.get(name);
			String[] newValues = new String[values.length + 1];
			System.arraycopy(values, 0, newValues, 0, values.length);
			newValues[newValues.length - 1] = value;
		} else {
			params.put(name, new String[] {value});
		}
	}
	
	
	private Map<String, String[]> getParameters() {
		if (parameters == null) {
			parameters = new HashMap<String, String[]>();
			if (queryString != null) {
				if (queryString.length() > 0) {
					String[] params = queryString.split("&");
					for (String param : params) {
						try {
							String[] pair = param.split("=");
							if (!parameters.containsKey(pair[0])) {
								parameters.put(pair[0], new String[] { URLDecoder.decode(pair[1], "UTF-8") });
							} else {
								String[] sa = parameters.get(pair[0]);
								String[] newSa = new String[sa.length + 1];
								System.arraycopy(sa, 0, newSa, 0, sa.length);
								newSa[sa.length] = pair[1];
								parameters.put(pair[0], newSa); 
							}
							
						} catch (Exception ignore) { }
					}
				}
			}
		}
		
		return parameters;
	}
	
	
	
	
	public final long getDateHeader(String headername) {
		String value = getHeader(headername);
        if (value == null)
            return  (-1L);

        long result = parseDate(value);
        if (result != (-1L)) {
            return result;
        }
        throw new IllegalArgumentException(value);
	}
 
	public final void addDateHeader(String headername, long date) {
		addHeader(headername, printDate(date));
	}

	public final void setDateHeader(String headername, long date) {
		setHeader(headername, printDate(date));
	}

	
	public final void addIntHeader(String headername, int value) {
		addHeader(headername, Integer.toString(value));
	}

	public final void setIntHeader(String headername, int value) {
		setHeader(headername, Integer.toString(value));
	}
	
	public final String getHeader(String headername) {
		List<String> values = headers.get(headername);
		if (values != null) {
			return values.get(0);
		} else {
			return null;
		}
	}

	
	public final void addHeader(String headername, String headervalue) {
		List<String> values = headers.get(headername);
		
		if (values == null) {
			values = new ArrayList<String>();
			headers.put(headername, values);
		} 
		
		values.add(headervalue);
	}

	public final void setHeader(String headername, String headervalue) {
		List<String> values = new ArrayList<String>();
		values.add(headervalue);
		
		headers.put(headername, values);
	}
	
	
	public boolean containsHeader(String headername) {
		return headers.containsKey(headername);
	}
	
	public final Enumeration getHeaderNames() {
		return Collections.enumeration(headers.keySet());
	}

	public final Enumeration getHeaders(String headername) {
		List<String> values = headers.get(headername);
		if (values == null) {
			return Collections.enumeration(new ArrayList<String>());
		} else {
			return Collections.enumeration(values);
		}
	}
	
	 
	protected final List<String> getHeaderValues(String headername) {
		return headers.get(headername);
	}

	public final int getIntHeader(String headername) {
		String value = getHeader(headername);
		if (value != null) {
			return Integer.parseInt(value);
		} else {
			return -1;
		}
	}
	
	
	public final Locale getLocale() {
		Enumeration en = getLocales();
		if (en.hasMoreElements()) {
			return (Locale) en.nextElement();
		} else {
			return Locale.getDefault();
		}
	}
	
	
	public final Enumeration getLocales() {
		List<Locale> result = new ArrayList<Locale>();
	
		String val = getHeader("Accept-Language");
		if (val != null) {
			String[] elements = val.split(",");
			List<WeightedValue> values = new ArrayList<WeightedValue>();

			for (String el : elements) {
				WeightedValue wv = new WeightedValue(el);
				values.add(wv);
			}
			Collections.sort(values, new WeightComparator());
		
			for (WeightedValue wv : values) {
				String language = wv.getValue(); 
	            String country = "";
	            int dash = language.indexOf('-');
	            if (dash > -1) {
	            	country = language.substring(dash + 1).trim();
	            	language = language.substring(0,dash).trim();
	            }
	            result.add(new Locale(language, country));
			}
		}
		
		if (result.isEmpty()) {
			result.add(Locale.getDefault());
		}
		
		return Collections.enumeration(result);
	}

	
	private static final long parseDate(String value) {
		Date date = null;
		for (int i = 0; i < FORMATS.length; i++) {
			try {
				date = FORMATS[i].parse(value);
				break;
			} catch (ParseException ignore) { }
        }

		if (date == null) {
			return (-1L);
		} else {
	        return date.getTime();
	    }
	}
	
	private static String printDate(long value) {
		return FORMATS[0].format(new Date(value));
	}


	
	
	private static class WeightedValue {
		private String value = null;
		private double weight = 1;
		
		WeightedValue(String s) {
			String[] splitted = s.split(";q=");
			value = splitted[0];
			if (splitted.length > 1) {
				weight = Double.parseDouble(splitted[1]);
			}
		}

		public final String getValue() {
			return value;
		}

		public final double getWeight() {
			return weight;
		}
		
	
	}
	
	
	private static class WeightComparator implements Comparator<WeightedValue>, Serializable {
		private static final long serialVersionUID = -6974139703976883845L;

		public int compare(WeightedValue o1, WeightedValue o2) {
			return (int) (o1.weight - o2.weight);
		}
	}
		
}
