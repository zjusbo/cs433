// $Id: IoHandler.java 1004 2007-03-08 06:05:15Z grro $
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
package org.xsocket.stream;

import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketOptions;
import java.util.HashMap;
import java.util.Map;

import static java.net.SocketOptions.*;


/**
 * socket configuration. 
 * 
 * @author grro@xsocket.org
 */
public final class StreamSocketConfiguration {
	
	private final Map<Integer, Object> options = new HashMap<Integer, Object>(); 	

	
	void setOptions(Socket socket) throws SocketException {
		for (java.util.Map.Entry<Integer, Object> entry : options.entrySet()) {
			setOption(socket, entry.getKey(), entry.getValue());
		}
	}

	void setOptions(ServerSocket socket) throws SocketException {
		for (java.util.Map.Entry<Integer, Object> entry : options.entrySet()) {
			setOption(socket, entry.getKey(), entry.getValue());
		}
	}

		   
	static void setOption(Socket socket, int optID, Object value) throws SocketException {
		switch (optID) {
		case TCP_NODELAY:
			socket.setTcpNoDelay((Boolean) value);
			break;

		case SO_TIMEOUT:
			socket.setSoTimeout((Integer) value);
			break;

		case SO_SNDBUF:
			socket.setSendBufferSize((Integer) value);
			break;

		case SO_REUSEADDR:
			socket.setReuseAddress((Boolean) value);
			break;
			
		case SO_RCVBUF:
			socket.setReceiveBufferSize((Integer) value);
			break;

		case SO_OOBINLINE:
			socket.setOOBInline((Boolean) value);
			break;

		case SO_LINGER:
			if (value instanceof Integer) {
				socket.setSoLinger(true, (Integer) value);
			} else if (value instanceof Boolean) {
				if (((Boolean) value).equals(Boolean.FALSE)) {
					socket.setSoLinger(Boolean.FALSE, 0);
				}
			}
			break;
				
		case SO_KEEPALIVE:
			socket.setKeepAlive((Boolean) value);
			break;

		case IP_TOS:
			socket.setTrafficClass((Integer) value);
			break;

		default:
			break;
		}
	}

	
	static void setOption(ServerSocket socket, int optID, Object value) throws SocketException {
		switch (optID) {
			
		case SO_RCVBUF:
			socket.setReceiveBufferSize((Integer) value);
			break;
			
		case SO_REUSEADDR:
			socket.setReuseAddress((Boolean) value);
			break;

		default:
			break;
		}
	}
	

	static Object getOption(Socket socket, int optID) throws SocketException {
		switch (optID) {
		case TCP_NODELAY:
			return socket.getTcpNoDelay();

		case SO_TIMEOUT:
			return socket.getSoTimeout();

		case SO_SNDBUF:
			return socket.getSendBufferSize();

		case SO_REUSEADDR:
			return socket.getReuseAddress();
			
		case SO_RCVBUF:
			return socket.getReceiveBufferSize();

		case SO_OOBINLINE:
			return socket.getOOBInline();

		case SO_LINGER:
			return socket.getSoLinger();
			
		case SO_KEEPALIVE:
			return socket.getKeepAlive();

		case IP_TOS:
			return socket.getTrafficClass();

		default:
			throw new RuntimeException("unsupported option id: " + optID);
		}
	}
	
	
	/**
	 * set SO_REUSEADDR
	 * 
	 * @param b SO_REUSEADDR or null to use default
	 */
	public void setSO_REUSEADDR(Boolean b) {
		options.put(SocketOptions.SO_REUSEADDR, b);
	}
		
	
	/**
	 * set TCP_NODELAY
	 * 
	 * @param b TCP_NODELAY or null to use default
	 */
	public void setTCP_NODELAY(Boolean b) {
		options.put(SocketOptions.TCP_NODELAY, b);
	}
		
	
	/**
	 * set SO_TIMEOUT 
	 * 
	 * @param i SO_TIMEOUT or null to use default
	 */
	public void setSO_TIMEOUT(Integer i) {
		options.put(SocketOptions.SO_TIMEOUT, i);
	}

	
	/**
	 * set SO_SNDBUF 
	 * 
	 * @param i SO_SNDBUF or null to use default
	 */
	public void setSO_SNDBUF(Integer i) {
		options.put(SocketOptions.SO_SNDBUF, i);
	}
		

	/**
	 * set SO_RCVBUF 
	 * 
	 * @param i SO_RCVBUF or null to use default
	 */
	public void setSO_RCVBUF(Integer i) {
		options.put(SocketOptions.SO_RCVBUF, i);
	}

	
	/**
	 * set SO_OOBINLINE 
	 * 
	 * @param b SO_OOBINLINE or null to use default
	 */
	public void setSO_OOBINLINE(Boolean b)  {
		options.put(SocketOptions.SO_OOBINLINE, b);
	}

	/**
	 * set SO_KEEPALIVE 
	 * 
	 * @param b SO_KEEPALIVE or null to use default
	 */
	public void setSO_KEEPALIVE(Boolean b) {
		options.put(SocketOptions.SO_KEEPALIVE, b);
	}
	

	
	/**
	 * set IP_TOS 
	 * 
	 * @param i IP_TOS or null to use default
	 */
	public void setIP_TOS(Integer i) {
		options.put(SocketOptions.IP_TOS, i);
	}
	
	
	/**
	 * set SO_LINGER 
	 * 
	 * @param i SO_LINGER or null to use default or negative value to disable
	 */
	public void setSO_LINGER(Integer i) {
		if (i < 0) {
			options.put(SocketOptions.SO_LINGER, Boolean.FALSE);
		} else {
			options.put(SocketOptions.SO_LINGER, i);
		}
	}
}
