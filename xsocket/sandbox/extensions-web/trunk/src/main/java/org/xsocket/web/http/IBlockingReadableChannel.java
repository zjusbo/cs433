package org.xsocket.web.http;


import java.io.Closeable;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

import org.xsocket.ClosedConnectionException;
import org.xsocket.IDataSink;
import org.xsocket.IDataSource;
import org.xsocket.MaxReadSizeExceededException;



public interface IBlockingReadableChannel extends IDataSource, ReadableByteChannel {
	
	public static final int INITIAL_RECEIVE_TIMEOUT = Integer.MAX_VALUE;


	/**
	 * set the timeout for calling read methods in millis
	 *
	 * @param timeout  the timeout in millis
	 * @throws IOException If some other I/O error occurs
	 */
	public void setReceiveTimeoutMillis(int timeout) throws IOException;
	
	
	/**
	 * get the timeout for calling read methods in millis
	 *
	 * @return the timeout in millis
	 * @throws IOException If some other I/O error occurs
	 */
	public int getReceiveTimeoutMillis() throws IOException;
	
	
	public ByteBuffer[] readByteBuffer() throws IOException, ClosedConnectionException, SocketTimeoutException;
	
	public byte[] readBytes() throws IOException, ClosedConnectionException, SocketTimeoutException;
	
	public String readString() throws IOException, ClosedConnectionException, SocketTimeoutException;
	
	public long transferTo(WritableByteChannel outputChannel) throws ClosedConnectionException, IOException, SocketTimeoutException, MaxReadSizeExceededException;
	
	public boolean isRead();
}
