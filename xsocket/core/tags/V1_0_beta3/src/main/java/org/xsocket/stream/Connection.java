// $Id: Connection.java 776 2007-01-15 17:15:41Z grro $
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

import java.io.IOException;
import java.net.InetAddress;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.util.LinkedList;


import org.xsocket.ClosedConnectionException;
import org.xsocket.DataConverter;
import org.xsocket.stream.ByteBufferParser.Index;



/**
 * Implementation base of the <code>IConnection</code> interface.
 * 
 *
 * @author grro@xsocket.org
 */
abstract class Connection implements IConnection {
	
	
	// parser
	private static final ByteBufferParser PARSER = new ByteBufferParser();
	
	// read & write queue
	private final ByteBufferQueue writeQueue = new ByteBufferQueue();
	private final ByteBufferQueue readQueue = new ByteBufferQueue();

	// io handler
	private IoHandler ioHandler = null;
	
	// encoding
	private String defaultEncoding = INITIAL_DEFAULT_ENCODING;
	
	// autoflush
	private boolean autoflush = INITIAL_AUTOFLUSH;
	
	
	// index for extract method
	private Index cachedIndex = null;

    

	/**
	 * constructor 
	 * 
	 * @param autoflush  true, if autoflush should be activated
	 */
    Connection(boolean autoflush) {
    	this.autoflush = autoflush;
	}
         	
	/**
	 * open the connection
	 * 
	 * @throws IOException If some other I/O error occurs
	 */
	void open() throws IOException {
		ioHandler.open();
	}
	
	
	/**
	 * return the read queue 
	 * 
	 * @return the read queue
	 */
	final ByteBufferQueue getReadQueue() {
		return readQueue;
	}
	
	
	/**
	 * return the underlying io handler
	 * 
	 * @return the underlying io handler
	 */
	final IoHandler getIOHandler() {
		return ioHandler;
	}

	/**
	 * set the underlying io handler 
	 * 
	 * @param ioHdl  the underlying io handler
	 */
	final void setIOHandler(final IoHandler ioHdl) {
		this.ioHandler = ioHdl;
	}
	
	
	
	/**
	 * {@inheritDoc}
	 */
	public final void close() throws IOException {
		flushStrong();
		ioHandler.close();		
	}

	
	/**
	 * {@inheritDoc}
	 */
	public final boolean isOpen() {
		return ioHandler.isOpen();
	}
		
	/**
	 * write incoming data into the read buffer 
	 * 
	 * @param data the data to add
	 */
	final void writeIncoming(ByteBuffer data) {
		readQueue.append(data);
	}
	

	/**
	 * write outgoing data into the write buffer 
	 * 
	 * @param data the data to add
	 */
	final void writeOutgoing(ByteBuffer data) {
		writeQueue.append(data);
	}
	
	/**
	 * write outgoing datas into the write buffer
	 *  
	 * @param datas the data to add
	 */
	void writeOutgoing(LinkedList<ByteBuffer> datas) {
		writeQueue.append(datas);	
	}
	
	
	
	/**
	 * {@inheritDoc}
	 */ 
	public final void flush() throws ClosedConnectionException, IOException {
		if (!writeQueue.isEmpty()) {
			LinkedList<ByteBuffer> buffer = writeQueue.drain();
			ioHandler.writeOutgoing(buffer);
		}
	}
	
	
	private void flushStrong() throws ClosedConnectionException, IOException {
		flush();
		getIOHandler().flushOutgoing();
	}
	

	

	
	/**
	 * {@inheritDoc}
	 */
	public final String getDefaultEncoding() {
		return defaultEncoding;
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public final void setDefaultEncoding(String defaultEncoding) {
		this.defaultEncoding = defaultEncoding;
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public void setAutoflush(boolean autoflush) {
		this.autoflush = autoflush;
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public boolean getAutoflush() {
		return autoflush;
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public final String getId() {
		return ioHandler.getId();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public InetAddress getLocalAddress() {
		return ioHandler.getLocalAddress();
	}
	
	
	
	/**
	 * {@inheritDoc}
	 */
	public int getLocalPort() {
		return ioHandler.getLocalPort();
	}
	
	
	
	/**
	 * {@inheritDoc}
	 */
	public InetAddress getRemoteAddress() {
		return ioHandler.getRemoteAddress();
	}
	
	
	
	/**
	 * {@inheritDoc}
	 */
	public int getRemotePort() {
		return ioHandler.getRemotePort();
	}
	
		
	/**
	 * call back method for the idle timeout
	 *
	 */
	void onIdleTimeout() {
		try {
			close();
		} catch (IOException ignore) { }
	}
	

	/**
	 * call back method for connection timeout
	 *
	 */
	void onConnectionTimeout() {
		try {
			close();
		} catch (IOException ignore) { }
	}

	
	/**
	 * receive data 
	 *
	 */
	final void receive() {
		LinkedList<ByteBuffer> buffer = getIOHandler().drainIncoming();
		getReadQueue().append(buffer);
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public void startSSL() throws IOException {
		IoHandler ioHandler = getIOHandler();
		
		flushStrong();
		
		do {
			if (ioHandler instanceof IoSSLHandler) {
				((IoSSLHandler) ioHandler).startSSL();
				return;
			}
			ioHandler = ioHandler.getSuccessor();
		} while (ioHandler != null);
		
		throw new IOException("couldn't startSSL, because no SSLHandler (SSLContext) is set");
	}
	
	
	
	/**
	 * {@inheritDoc}
	 */
	public final int write(String s) throws ClosedConnectionException, IOException {
		return write(s, defaultEncoding);
	}


	/**
	 * {@inheritDoc}
	 */
	public final int write(String s, String encoding) throws ClosedConnectionException, IOException {
		ByteBuffer buffer = DataConverter.toByteBuffer(s, encoding);
		return write(buffer);
	}

	/**
	 * {@inheritDoc}
	 */
	public final int write(byte b) throws ClosedConnectionException, IOException {
		ByteBuffer buffer = ByteBuffer.allocate(1).put(b);
		buffer.flip();
		return write(buffer);
	}

	
	/**
	 * {@inheritDoc}
	 */
	public final int write(byte... bytes) throws ClosedConnectionException, IOException {
		return write(ByteBuffer.wrap(bytes));
	}

	
	/**
	 * {@inheritDoc}
	 */
	public final int write(byte[] bytes, int offset, int length) throws ClosedConnectionException, IOException {
		return write(ByteBuffer.wrap(bytes, offset, length));
	}
	

	/**
	 * {@inheritDoc}
	 */
	public final long write(ByteBuffer[] buffers) throws ClosedConnectionException, IOException {
		long written = 0;
		for (ByteBuffer buffer : buffers) {
			written += buffer.limit() - buffer.position();
		}

		for (ByteBuffer buffer : buffers) {
			writeQueue.append(buffer);
		}

		if (autoflush) {
			flush();
		}
		
		return written;
	}

	
	/**
	 * {@inheritDoc}
	 */
	public final int write(ByteBuffer buffer) throws ClosedConnectionException, IOException {
		int written = buffer.limit() - buffer.position();
		writeQueue.append(buffer);

		if (autoflush) {
			flush();
		}

		return written;
	}
	

	/**
	 * {@inheritDoc}
	 */
	public final int write(int i) throws ClosedConnectionException, IOException {
		ByteBuffer buffer = ByteBuffer.allocate(4).putInt(i);
		buffer.flip();
		return (int) write(buffer);
	}

	/**
	 * {@inheritDoc}
	 */
	public final int write(long l) throws ClosedConnectionException, IOException {
		ByteBuffer buffer = ByteBuffer.allocate(8).putLong(l);
		buffer.flip();
		return (int) write(buffer);
	}


	/**
	 * {@inheritDoc}
	 */
	public final int write(double d) throws ClosedConnectionException, IOException {
		ByteBuffer buffer = ByteBuffer.allocate(8).putDouble(d);
		buffer.flip();
		return (int) write(buffer);
	}


	/**
	 * {@inheritDoc}
	 */
	public final long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
		ByteBuffer[] bufs = new ByteBuffer[length];
		System.arraycopy(srcs, offset, bufs, 0, length);

		return write(bufs);
	}

	
	/**
	 * extract all bytes from the queue 
	 * 
	 * @return all bytes of the queue
	 */
	protected final LinkedList<ByteBuffer> extractAvailableFromReadQueue() {
		resetCachedIndex();
		return readQueue.drain();
	}
	
	/**
	 * extract bytes by using a delimiter
	 * 
	 * @param delimiter   the delimiter 
	 * @param outChannel  the channel to write in
	 * @return true, if the delimiter has been found
	 * @throws IOException If some other I/O error occurs
 	 * @throws BufferUnderflowException if the buffer's limit has been reached	

	 */
	protected final int extractBytesByDelimiterFromReadQueue(String delimiter, WritableByteChannel outChannel) throws IOException, BufferUnderflowException {
		
		if (!readQueue.isEmpty()) {
			LinkedList<ByteBuffer> buffers = readQueue.drain();
			assert (buffers != null);
				
			ByteBufferParser.Index index = scanByDelimiter(buffers, delimiter);
	
			// index found?
			if (index.hasDelimiterFound()) {
				// delimiter found 
				PARSER.extract(buffers, index, outChannel);
				readQueue.addFirst(buffers);
				resetCachedIndex();
				return index.getReadBytes();	
					
			// .. no -> return buffer
			} else {
				readQueue.addFirst(buffers);
				cachedIndex = index;
			}
		}
		
		throw new BufferUnderflowException();
	}

	/**
	 * extracts bytes by using 
	 * 
	 * @param length      the number of bytes to extract
	 * @param outChannel  the channel to write ib
	 * @throws IOException If some other I/O error occurs
 	 * @throws BufferUnderflowException if the buffer's limit has been reached	
	 */
	protected final void extractBytesByLength(int length, WritableByteChannel outChannel) throws IOException, BufferUnderflowException {
		
		// enough data?
		if (readQueue.getSize() >= length) {
			LinkedList<ByteBuffer> buffers = readQueue.drain();
			assert (buffers != null);
			
			PARSER.extract(buffers, length, outChannel);
			readQueue.addFirst(buffers);
			resetCachedIndex();
			
		// .. no
		} else {
			throw new BufferUnderflowException();
		}
	}
	
	
	
	/**
	 * extract available bytes from the queue by using a delimiter
	 * 
	 * @param delimiter    the delimiter
	 * @param outChannel   the channel to write in
	 * @return true if the delimiter has been found
	 * @throws IOException If some other I/O error occurs 
	 */
	@SuppressWarnings("unchecked")
	protected final boolean extractAvailableFromReadQueue(String delimiter, WritableByteChannel outChannel) throws IOException {
		
		if (!readQueue.isEmpty()) {
			LinkedList<ByteBuffer> buffers = readQueue.drain();
			assert (buffers != null);

			ByteBufferParser.Index index = scanByDelimiter(buffers, delimiter);
	
			// delimiter found?
			if (index.hasDelimiterFound()) {
				PARSER.extract(buffers, index, outChannel);
				readQueue.addFirst(buffers);
				resetCachedIndex();	
				return true;
					
			// delimiter not found 	
			} else {
				// read only if not part of delimiter has been detected 
				if (index.getDelimiterPos() == 0) {
					int readBytes = index.getReadBytes();
					if (readBytes > 0) {
						int availableBytes = readBytes - index.getDelimiterPos();
						if (availableBytes > 0) {
							PARSER.extract(buffers, availableBytes, outChannel);
							resetCachedIndex();	
						}
					}
				} 
				
				readQueue.addFirst(buffers);
				return false;
			}
			
		} else {
			return false;
		}
	}

	
	private ByteBufferParser.Index scanByDelimiter(LinkedList<ByteBuffer> buffers, String delimiter) {			

		// does index already exists (from former scan) 
		if (cachedIndex != null) {
			if (cachedIndex.getDelimiter().equals(delimiter)) {
				return PARSER.find(buffers, cachedIndex);
			} else {
				cachedIndex = null;
			}
		}

		return PARSER.find(buffers, delimiter);
	}
	
	

	/**
	 * extract bytes from the queue
	 * 
	 * @param length the number of bytes to extract
	 * @return the bytes
 	 * @throws BufferUnderflowException if the buffer's limit has been reached 
	 */
	protected final byte[] extractBytesFromReadQueue(int length) throws BufferUnderflowException {
		resetCachedIndex();
		
		return readQueue.read(length);
	}

	
	/**
	 * extract a int from the queue
	 * 
	 * @return the int value
 	 * @throws BufferUnderflowException if the buffer's limit has been reached
	 */
	protected final int extractIntFromReadQueue() throws BufferUnderflowException {
		resetCachedIndex();
		
		if (readQueue.getFirstBufferSize() >= 4) {
			ByteBuffer buf = readQueue.removeFirst();
			assert (buf != null);
			
			int i = buf.getInt();
			readQueue.addFirst(buf.slice());
			return i;
			
		} else {
			return ByteBuffer.wrap(readQueue.read(4)).getInt();
		}
	}
	
	
	/**
	 * extract a byte value from the queue
	 * 
	 * @return the byte value
 	 * @throws BufferUnderflowException if the buffer's limit has been reached
	 */
	protected final byte extractByteFromReadQueue() throws BufferUnderflowException {
		resetCachedIndex();
		
		if (readQueue.getFirstBufferSize() >= 1) {
			ByteBuffer buf = readQueue.removeFirst();
			assert (buf != null);
			
			byte b = buf.get();
			readQueue.addFirst(buf.slice());
			return b;
		}  else {
			throw new BufferUnderflowException();
		}
	}
	
	
	/**
	 * extract a double value from the queue 
	 * 
	 * @return the double value
 	 * @throws BufferUnderflowException if the buffer's limit has been reached
	 */
	protected final double extractDoubleFromReadQueue() throws BufferUnderflowException {
		resetCachedIndex();
		
		if (readQueue.getFirstBufferSize() >= 8) {
			ByteBuffer buf = readQueue.removeFirst();
			assert (buf != null);
			
			double d = buf.getDouble();
			readQueue.addFirst(buf.slice());
			return d;
		} else {
			return ByteBuffer.wrap(readQueue.read(8)).getDouble();
		}
	}
	
	

	/**
	 * extract a long value from the queue
	 * 
	 * @return the long value
 	 * @throws BufferUnderflowException if the buffer's limit has been reached 
	 */
	protected final long extractLongFromReadQueue() throws BufferUnderflowException {
		resetCachedIndex();
		
		if (readQueue.getFirstBufferSize() >= 8) {
			ByteBuffer buf = readQueue.removeFirst();
			assert (buf != null);
			
			long l = buf.getLong();
			readQueue.addFirst(buf.slice());
			return l;
		} else {
			return ByteBuffer.wrap(readQueue.read(8)).getLong();
		}
	}

	
	private void resetCachedIndex() {
		cachedIndex = null;
	}
	

	

	/**
	 * {@inheritDoc}
	 */
	public String toCompactString() {
		return "id=" + getId()
	       + ", caller=" + getRemoteAddress().getCanonicalHostName() + "(" + getRemoteAddress() + ":" + getRemotePort() + ")";
	}


	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder(toCompactString());
	/*	if (connectionEndTime != -1) {
			sb.append(", lifetime=" + TextUtils.printFormatedDuration(connectionEndTime - connectionOpenedTime));
		}
		sb.append(", lastTimeReceived=" + TextUtils.printFormatedDate(lastTimeRead)
				  + ", received=" + TextUtils.printFormatedBytesSize(bytesRead)
				  + ", send=" + TextUtils.printFormatedBytesSize(bytesWritten)
				  + ", readQueueSize=" + readQueue.getSize());*/
		return sb.toString();
	}
}
