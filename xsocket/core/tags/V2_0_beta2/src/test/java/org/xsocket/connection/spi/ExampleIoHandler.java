/*
 *  Copyright (c) xsocket.org, 2006-2008. All rights reserved.
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
package org.xsocket.connection.spi;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.util.Map;


import org.xsocket.DataConverter;
import org.xsocket.connection.spi.IIoHandler;
import org.xsocket.connection.spi.IIoHandlerCallback;



/**
*
* @author grro@xsocket.org
*/
final class ExampleIoHandler implements IIoHandler {

	private volatile boolean isRunning = true;

	private Socket socket = null;
	private Thread readerThread = null;

	private long conectionTimeoutMillis = 60 * 1000;
	private long idleTimeoutMillis = 60 * 1000;

	private IIoHandlerCallback callback = null;
	private String id = null;



	public ExampleIoHandler(String id, Socket socket) {
		this.id = id;
		this.socket = socket;
	}


	public void init(final IIoHandlerCallback callbackHandler) throws IOException, SocketTimeoutException {
		this.callback = callbackHandler;


		readerThread = new Thread() {
			@Override
			public void run() {
				setName("readerThread");

				try {
					InputStream is = socket.getInputStream();
					byte[] buffer = new byte[1024];
					int length = 0;

					while ((length = is.read(buffer)) > 0) {
						if (!isRunning) {
							break;
						}

						ByteBuffer bb = ByteBuffer.wrap(buffer, 0, length);
						callbackHandler.onData(new ByteBuffer[] { bb });

						buffer = new byte[socket.getReceiveBufferSize()];

					}

				} catch (IOException ioe) {
					ioe.printStackTrace();
				}
			}
		};
		readerThread.start();


		callbackHandler.onConnect();
	}


	public boolean isOpen() {
		return !socket.isClosed();
	}

	public void resumeRead() {

	}

	public void suspendRead() {

	}

	public boolean reset() {
		return true;
	}

	public void close(boolean immediate) throws IOException {
		shutdown();

		callback.onDisconnect();
	}

	public int getPendingWriteDataSize() {
		return 0;
	}

	public boolean hasDataToSend() {
		return false;
	}

	public int getPendingReadDataSize() {
		return 0;
	}



	public void write(ByteBuffer[] buffers) throws ClosedChannelException, IOException {
		for (ByteBuffer buffer : buffers) {
			try {
				byte[] bytes = DataConverter.toBytes(buffer);
				socket.getOutputStream().write(bytes);
				socket.getOutputStream().flush();

				callback.onWritten(buffer);
			} catch (final IOException ioe) {
				callback.onWriteException(ioe, buffer);
			}
		}
	}




	public InetAddress getLocalAddress() {
		return socket.getLocalAddress();
	}

	public int getLocalPort() {
		return socket.getLocalPort();
	}

	public InetAddress getRemoteAddress() {
		return socket.getInetAddress();
	}

	public int getRemotePort() {
		return socket.getPort();
	}


	public long getConnectionTimeoutMillis() {
		return conectionTimeoutMillis;
	}

	public String getId() {
		return id;
	}

	public long getIdleTimeoutMillis() {
		return idleTimeoutMillis;
	}

	public void setConnectionTimeoutMillis(long timeout) {
		this.conectionTimeoutMillis = timeout;

	}

	public void setIdleTimeoutMillis(long timeout) {
		this.idleTimeoutMillis = timeout;
	}

	public long getRemainingMillisToConnectionTimeout() {
		return 0;
	}


	public long getRemainingMillisToIdleTimeout() {
		return 0;
	}

	public void setOption(String name, Object value) throws IOException {

	}

	public Map<String, Class> getOptions() {
		return null;
	}

	public Object getOption(String name) throws IOException {
		return null;
	}

	void shutdown() {
		isRunning = false;
		readerThread.interrupt();
		try {
			socket.close();
		} catch (Exception ignore) { }
	}
}
