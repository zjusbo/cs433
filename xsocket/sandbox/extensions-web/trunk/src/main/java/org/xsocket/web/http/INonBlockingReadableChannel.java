package org.xsocket.web.http;


import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

import org.xsocket.ClosedConnectionException;
import org.xsocket.IDataSource;
import org.xsocket.MaxReadSizeExceededException;



public interface INonBlockingReadableChannel extends IDataSource, ReadableByteChannel {
	
	public static final int INITIAL_RECEIVE_TIMEOUT = Integer.MAX_VALUE;



	
	/**
	 * get the number of available bytes to read
	 *
	 * @return the number of available bytes
	 */
	public int getNumberOfAvailableBytes();

	
	public ByteBuffer[] readByteBuffer() throws IOException, ClosedConnectionException, SocketTimeoutException, MaxReadSizeExceededException;
	
	public byte[] readBytes() throws IOException, ClosedConnectionException, SocketTimeoutException, MaxReadSizeExceededException;
	
	public long transferTo(WritableByteChannel outputChannel) throws ClosedConnectionException, IOException, SocketTimeoutException;
	
	public boolean isRead();
}
