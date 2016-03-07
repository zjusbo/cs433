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
package org.xsocket.web.http;


import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.channels.ReadableByteChannel;
import java.util.Map;



/**
*
* @author grro@xsocket.org
*/
public interface IHttpConnection extends Closeable {
	
	
	public IResponseHeader createResponseHeader(int statusCode);
	
	public IRequestHeader createRequestHeader(String method, String requestedURI);
	
	/**
	 * sends a HTTP message header without a body
	 * 
	 * non-blocking call
	 */
	public int sendMessage(IRequestHeader header) throws IOException;

	
	/**
	 * sends a HTTP message header without a body
	 * 
	 * non-blocking call
	 */
	public int sendMessage(IResponseHeader header) throws IOException;

	
	
	/**
	 * sends a HTTP message header for a standard body
	 * 
	 * non-blocking call 
	 */
	public IWriteableChannel sendMessageHeader(IRequestHeader header, int contentLength) throws IOException;

	

	/**
	 * sends a HTTP message header for a standard body
	 * 
	 * non-blocking call 
	 */
	public IWriteableChannel sendMessageHeader(IResponseHeader header, int contentLength) throws IOException;

	
	/**
	 * writes a HTTP message header for a chunked body
	 * 
 	 * blocking call
	 */
	public int sendMessage(IRequestHeader header, int contentLength, ReadableByteChannel bodyChannel) throws IOException;

	/**
	 * writes a HTTP message header for a chunked body
	 * 
 	 * blocking call
	 */
	public int sendMessage(IResponseHeader header, int contentLength, ReadableByteChannel bodyChannel) throws IOException;

	
	
	/**
	 * writes a HTTP message header for a chunked body
	 * 
	 * non-blocking call
	 */
	public IWriteableChannel sendChunkedMessageHeader(IRequestHeader header) throws IOException;

	
	/**
	 * writes a HTTP message header for a chunked body
	 * 
	 * non-blocking call
	 */
	public IWriteableChannel sendChunkedMessageHeader(IRequestHeader header, int chunkSize) throws IOException;

	
	/**
	 * writes a HTTP message header for a chunked body
	 * 
	 * non-blocking call
	 */
	public IWriteableChannel sendChunkedMessageHeader(IResponseHeader header) throws IOException;

	/**
	 * writes a HTTP message header for a chunked body
	 * 
	 * non-blocking call
	 */
	public IWriteableChannel sendChunkedMessageHeader(IResponseHeader header, int chunkSize) throws IOException;

	
	
	/**
	 * writes a HTTP message header for a chunked body
	 * 
	 * blocking call 
	 */
	public int sendChunkedMessage(IRequestHeader header, ReadableByteChannel bodyChannel) throws IOException;

	
	/**
	 * writes a HTTP message header for a chunked body
	 * 
	 * blocking call 
	 */
	public int sendChunkedMessage(IResponseHeader header, ReadableByteChannel bodyChannel) throws IOException;

	
	

	/**
	 * blocking call to retrieve the message header.
	 * 
	 * @return
	 * @throws IOException
	 */
	public IHeader receiveMessageHeader() throws IOException;

	
	/**
	 * blocking call to retrieve the message body. It only returns when the complete body has been received 
	 * 
	 * @return a <b>blocking</b> body read channel 
	 * @throws IOException
	 */
	public IBlockingReadableChannel receiveMessageBody() throws IOException, BodyNotExistException;
	
	
	
	/**
	 * non-blocking call to retrieve the message body by a handler. This call returns immediately. By receiving  
	 * body data the call back handler will be called by a dedicated worker thread. By performing the
	 * call back method of the handler a <b>non-blocking</b> {@link INonBlockingReadableChannel} will be passed over
	 * 
	 * @throws IOException
	 */
	public void receiveMessageBody(INonBlockingReadableChannelHandler messageBodyHandler) throws IOException, BodyNotExistException;

	
	
	/**
	 * returns the id
	 *
	 * @return id
	 */
	public String getId();

	
	/**
	 * returns the local port
	 *
	 * @return the local port
	 */
	public int getLocalPort();



	/**
	 * returns the local address
	 *
	 * @return the local IP address or InetAddress.anyLocalAddress() if the socket is not bound yet.
	 */
	public InetAddress getLocalAddress();




	/**
	 * returns the remote address
	 *
	 * @return the remote address
	 */
	public InetAddress getRemoteAddress();


	/**
	 * returns the port of the remote endpoint
	 *
	 * @return the remote port
	 */
	public int getRemotePort();


	/**
	 * sets the default encoding for this connection (used by string related methods like readString...)
	 *
	 * @param encoding the default encoding
	 */
	public void setDefaultEncoding(String encoding);

	
	/**
	 * gets the default encoding for this connection (used by string related methods like readString...)
	 *
	 * @return the default encoding
	 */
	public String getDefaultEncoding();



	/**
	 * returns the idle timeout in sec.
	 *
	 * @return idle timeout in sec
	 */
	public int getIdleTimeoutSec();






	/**
	 * sets the idle timeout in sec
	 *
	 * @param timeoutInSec idle timeout in sec
	 */
	public void setIdleTimeoutSec(int timeoutInSec);



	/**
	 * gets the connection timeout
	 *
	 * @return connection timeout
	 */
	public int getConnectionTimeoutSec();


	/**
	 * sets the max time for a connections. By
	 * exceeding this time the connection will be
	 * terminated
	 *
	 * @param timeoutSec the connection timeout in sec
	 */
	public void setConnectionTimeoutSec(int timeoutSec);




	/**
	 * ad hoc activation of a secured mode (SSL). By perfoming of this
	 * method all remaining data to send will be flushed.
	 * After this all data will be sent and received in the secured mode
	 *
	 * @throws IOException If some other I/O error occurs
	 */
	public void activateSecuredMode() throws IOException;



	/**
	 * Attaches the given object to this connection
	 *
	 * @param obj The object to be attached; may be null
	 * @return The previously-attached object, if any, otherwise null
	 */
	public void setAttachment(Object obj);


	/**
	 * Retrieves the current attachment.
	 *
	 * @return The object currently attached to this key, or null if there is no attachment
	 */
	public Object getAttachment();



	/**
	 * sets the value of a option. <br><br>
	 *
	 * A good article for tuning can be found here {@link http://www.onlamp.com/lpt/a/6324}
	 *
	 * @param name   the name of the option
	 * @param value  the value of the option
	 * @throws IOException In an I/O error occurs
	 */
	public void setOption(String name, Object value) throws IOException;



	/**
	 * returns the value of a option
	 *
	 * @param name  the name of the option
	 * @return the value of the option
	 * @throws IOException In an I/O error occurs
	 */
	public Object getOption(String name) throws IOException;



	/**
	 * Returns an unmodifiable map of the options supported by this endpoint.
	 *
	 * The key in the returned map is the name of a option, and its value
	 * is the type of the option value. The returned map will never contain null keys or values.
	 *
	 * @return An unmodifiable map of the options supported by this channel
	 */
	@SuppressWarnings("unchecked")
	public Map<String, Class> getOptions();
	
	
	
}
