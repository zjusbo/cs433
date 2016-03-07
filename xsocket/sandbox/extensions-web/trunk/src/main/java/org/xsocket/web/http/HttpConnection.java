package org.xsocket.web.http;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xsocket.ClosedConnectionException;
import org.xsocket.DataConverter;
import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.stream.IConnection;
import org.xsocket.stream.IReadWriteableConnection;
import org.xsocket.stream.IHandler;
import org.xsocket.stream.NonBlockingConnection;
import org.xsocket.stream.io.spi.IHandlerIoProvider;
import org.xsocket.stream.io.spi.IIoHandler;
import org.xsocket.stream.io.spi.IIoHandlerContext;

 

/**
 *
 * 
 * This class is not thread-safe
 * 
 * 
 * @author grro
 */
final class HttpConnection extends NonBlockingConnection implements IConnection, IHttpConnection {

	private static final Logger LOG = Logger.getLogger(HttpConnection.class.getName());
	
	
	private final Object dataReceiveGuard = new Object();
	private final Object handlerGuard = new Object();

	
	private String host = null;
	private IMessageHandler msgHandler = null;


	// write support
	private boolean isOpenWriteTransaction = false;
	private Integer bufferedSendBufferSize = null;
	
	
	// read support
	private Header prefetchedHeader = null;
	private AbstractReadableBodyChannel prefetchedBody = null;
	private IBodyParser bodyParser = null;
	
	
	HttpConnection(IIoHandlerContext ctx, IIoHandler ioHandler, IHandler appHandler, IHandlerIoProvider ioProvider, IMessageHandler msgHandler) throws IOException {
		super(ctx, ioHandler, appHandler, ioProvider); 

		assert (msgHandler != null) : "msgHandler have to be set";
		this.msgHandler = msgHandler;
		
		setFlushmode(FlushMode.ASYNC);		
		setAutoflush(false);
	}
	
	
	public HttpConnection(String host, int port) throws IOException {
		this(host, port, null);
	}

	
	public HttpConnection(String host, int port, IMessageHandler msgHandler) throws IOException {
		super(InetAddress.getByName(host), port, new HttpProtocolHandler(msgHandler));
		this.host = host;
		this.msgHandler = msgHandler;
		
		// no msg handler? -> create a dummy one
		if (this.msgHandler == null) {
			this.msgHandler = new IMessageHandler() {
				@Override
				public void onMessageHeader(IHttpConnection httpConnection) throws BufferUnderflowException, IOException {
					// do nothing
				}
			};
		}
 		
		setAutoflush(false);
	}
	
	
	@Override
	public IHeader receiveMessageHeader() throws IOException, BufferUnderflowException {
		
		synchronized (dataReceiveGuard) {
			if (prefetchedHeader != null) {
				Header header = prefetchedHeader;
				prefetchedHeader = null;
				return header;
	
			} else {
				Header header = readMessage();
				return header;
			}
		}
	}
	
	
	@Override
	public IBlockingReadableChannel receiveMessageBody() throws IOException, BodyNotExistException {
		
		synchronized (dataReceiveGuard) {
			if (prefetchedBody != null) {
				IBlockingReadableChannel channel = new BlockingReadableBodyChannelAdapter(prefetchedBody);
				prefetchedBody = null;
				return channel; 
			} else {
				throw new BodyNotExistException();
			}
		}	
	}
	
	
	
	@Override
	public void receiveMessageBody(INonBlockingReadableChannelHandler messageBodyHandler) throws IOException, BodyNotExistException {
		synchronized (dataReceiveGuard) {
			if (prefetchedBody != null) {
				prefetchedBody.setHandler(messageBodyHandler);
				INonBlockingReadableChannelHandler handler = prefetchedBody.bodyhandler;
				prefetchedBody = null;
				
				if (handler != null) {
					synchronized (handlerGuard) {
						handler.onData(null);
					}
				}
			} else {
				throw new BodyNotExistException();
			}
		}	
	}
	
	
	public IResponseHeader createResponseHeader(int statusCode) {
		return new ResponseHeader(statusCode);
	}
	
	public IRequestHeader createRequestHeader(String method, String requestedURI) {
		return new RequestHeader(method, requestedURI);
	}
	

	
	
	
	void onIncomingData() throws BufferUnderflowException, IOException {
		
		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("incoming data (prefetchedHeader=" + prefetchedHeader + ", prefetchedBody=" + prefetchedBody + ", bodyParser=" + bodyParser + ")");
		}
		
		
		boolean callMessageHandler = false;
		boolean callBodyHandler = false;
		INonBlockingReadableChannelHandler bodyHandler = null;
		
		synchronized (dataReceiveGuard) {
			
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("check");
			}

			// waiting for header?
			if (bodyParser == null) {
				// message retrieved (BufferUnderflow hasn't been thrown)
				prefetchedHeader = readMessage();
				callMessageHandler = true;
				
			// .. no, waiting for body data  
			} else  {
				bodyHandler = bodyParser.onData();
				if (bodyHandler != null) {
					callBodyHandler = true;
				}
			}
		}

		if (callMessageHandler) {
			synchronized (handlerGuard) {
				msgHandler.onMessageHeader(this);				
			}
			return;
		} 
		
		if (callBodyHandler) {
			synchronized (handlerGuard) {
				bodyHandler.onData(null);  // body handler is wrapped -> see AbstractReadableBodyChannel 			
			}
			return;
		}
	}
	
	
	private void removeBodyParser() throws IOException {
		
		synchronized (dataReceiveGuard) {
			if (LOG.isLoggable(Level.FINE)) {
				if (bodyParser != null) {
					LOG.fine("body parser removed");
				}
			}

			
			bodyParser = null;			
		}
	}
	
	
	private Header readMessage() throws IOException, BufferUnderflowException {
		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("try to read header");
		}
		
		Header header = null;
		header = Header.readFrom(this, Integer.MAX_VALUE);
		if (header.hasBody()) {
			if (header.getContentLength() > 0) {
				prefetchedBody = new ReadableBodyChannel(header.getContentLength());
				bodyParser = prefetchedBody;
				
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("set body parser");
				}

			} else {
				prefetchedBody = new ReadableChunkedBodyChannel();
				bodyParser = prefetchedBody;
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("set chunked body parser");
				}
			}
		}
		return header;
	}
	
	
	private void enhanceRequestHeader(IRequestHeader header) {
		if (!header.containsHeader("User-Agent")) {
			header.addHeader("User-Agent", "xSocket/Web");
		}
		
		if (!header.containsHeader("Accept")) {
			header.addHeader("Accept", "*/*");
		}

		if (!header.containsHeader("Accept-Language")) {
			header.addHeader("Accept-Language", "en-us");
		}

		if (!header.containsHeader("Accept-Encoding")) {
			header.addHeader("Accept-Encoding", "deflate");
		}
		
		if (!header.containsHeader("Connection")) {
			header.addHeader("Connection", "Keep-Alive");
		}
		
		if (!header.containsHeader("host")) {
			header.addHeader("host", host);
		}
	}
	


	@Override
	public int sendMessage(IRequestHeader header) throws IOException {
		enhanceRequestHeader(header);
		return sendMessage((IHeader) header);
	}
	
	@Override
	public int sendMessage(IResponseHeader header) throws IOException {
		return sendMessage((IHeader) header);
	}

	
	private int sendMessage(IHeader header) throws IOException {
		if (isOpenWriteTransaction) {
			LOG.warning("previous message send operation is not completed");
		}
				
		if (!header.containsHeader("Content-Length")) {
			header.addHeader("Content-Length", "0");
		}
		
		
		int written = write(header.toString());
		flush();
		
		return written;
	}
	
	
	@Override
	public IWriteableChannel sendMessageHeader(IRequestHeader header, int contentLength) throws IOException {
		enhanceRequestHeader(header);
		return sendMessageHeader((IHeader) header, contentLength);
	}

	@Override
	public IWriteableChannel sendMessageHeader(IResponseHeader header, int contentLength) throws IOException {
		return sendMessageHeader((IHeader) header, contentLength);
	}

	private IWriteableChannel sendMessageHeader(IHeader header, int contentLength) throws IOException {
		if (isOpenWriteTransaction) {
			LOG.warning("previous message send operation is not completed");
		}
		
		if (!header.containsHeader("host")) {
			if (host != null) {
				header.addHeader("host", host);
			}
		}
		
		if (!header.containsHeader("Content-Length")) {
			header.addHeader("Content-Length", Integer.toString(contentLength));
		}
		
		write(header.toString());
		isOpenWriteTransaction = true;
		
		return new WriteableBodyChannel(contentLength);
	}
	
	
	@Override
	public int sendMessage(IRequestHeader header, int contentLength, ReadableByteChannel bodyChannel) throws IOException {
		enhanceRequestHeader(header);
		return sendMessage((IHeader) header, contentLength, bodyChannel);
	}
	
	@Override
	public int sendMessage(IResponseHeader header, int contentLength, ReadableByteChannel bodyChannel) throws IOException {
		return sendMessage((IHeader) header, contentLength, bodyChannel);
	}
	
	private int sendMessage(IHeader header, int contentLength, ReadableByteChannel bodyChannel) throws IOException {
		int written = 0;
		IWriteableChannel bodyHandle = sendMessageHeader(header, contentLength);
		written += header.toString().getBytes(getDefaultEncoding()).length;
		
		written += bodyHandle.transferFrom(bodyChannel);
		bodyHandle.close();
		
		return written;
	}

	
	@Override
	public IWriteableChannel sendChunkedMessageHeader(IRequestHeader header) throws IOException {
		enhanceRequestHeader(header);
		
		int chunkSize = getSendBufferSize() - (int) (getSendBufferSize() * 0.1);
		return sendChunkedMessageHeader((IHeader) header, chunkSize);
	}
	
	
	@Override
	public IWriteableChannel sendChunkedMessageHeader(IRequestHeader header,int chunkSize) throws IOException {
		enhanceRequestHeader(header);
		return sendChunkedMessageHeader((IHeader) header, chunkSize);
	}
	
	
	@Override
	public IWriteableChannel sendChunkedMessageHeader(IResponseHeader header) throws IOException {
		int chunkSize = getSendBufferSize() - (int) (getSendBufferSize() * 0.1);
		return sendChunkedMessageHeader((IHeader) header, chunkSize);
	}

	@Override
	public IWriteableChannel sendChunkedMessageHeader(IResponseHeader header, int chunkSize) throws IOException {
		return sendChunkedMessageHeader((IHeader) header, chunkSize);
	}
	

	private IWriteableChannel sendChunkedMessageHeader(IHeader header, int chunkSize) throws IOException {
		if (isOpenWriteTransaction) {
			LOG.warning("previous message send operation is not completed");
		}

		if (!header.containsHeader("host")) {
			if (host != null) {
				header.addHeader("host", host);
			}
		}
		if (!header.containsHeader("Transfer-Encoding")) {
			header.addHeader("Transfer-Encoding", "chunked");
		}
		 
		write(header.toString());
		isOpenWriteTransaction = true;
		
		return new WriteableChunkedBodyChannel(chunkSize);
	}

	
	@Override
	public int sendChunkedMessage(IRequestHeader header, ReadableByteChannel bodyChannel) throws IOException {
		enhanceRequestHeader(header);
		return sendChunkedMessage((IHeader) header, bodyChannel);
	}
	
	@Override
	public int sendChunkedMessage(IResponseHeader header, ReadableByteChannel bodyChannel) throws IOException {
		return sendChunkedMessage((IHeader) header, bodyChannel);
	}
	
	private int sendChunkedMessage(IHeader header, ReadableByteChannel bodyChannel) throws IOException {
		int written = 0;
		
		int chunkSize = getSendBufferSize() - (int) (getSendBufferSize() * 0.1);
		IWriteableChannel bodyHandle = sendChunkedMessageHeader(header, chunkSize);
		written += header.toString().getBytes(getDefaultEncoding()).length;
		
		written += bodyHandle.transferFrom(bodyChannel);
		bodyHandle.close();
		
		return written;
	}
	
	
	
	
	private int getSendBufferSize() throws IOException {
		if (bufferedSendBufferSize == null) {
			bufferedSendBufferSize = (Integer) getOption(IReadWriteableConnection.SO_SNDBUF);
		}
		return bufferedSendBufferSize;
	}
	
	
	
	private interface IBodyParser {
		public INonBlockingReadableChannelHandler onData() throws IOException;
	}
	
	
	private abstract class AbstractReadableBodyChannel implements INonBlockingReadableChannel, IBodyParser {
	
		private INonBlockingReadableChannelHandler bodyhandler = null;
		
		final void setHandler(final INonBlockingReadableChannelHandler handler) {
			this.bodyhandler = new INonBlockingReadableChannelHandler() {
				@Override
				public void onData(INonBlockingReadableChannel bodyReadChannel) throws BufferUnderflowException, IOException {
					if (getNumberOfAvailableBytes() > 0) {
						handler.onData(AbstractReadableBodyChannel.this);
					}
				}
			};
		}
		
		
		@Override
		public INonBlockingReadableChannelHandler onData() throws IOException {
			return bodyhandler;
		}
		
		final String getDefaultEncoding() {
			return HttpConnection.this.getDefaultEncoding();
		}
			
		abstract public boolean isRead();
		
		abstract public int getNumberOfAvailableBytes();
		

		
		

		public final byte[] readAvailableBytesByDelimiter(String delimiter, String encoding, int maxLength) throws IOException, MaxReadSizeExceededException {
			return DataConverter.toBytes(readAvailableByteBufferByDelimiter(delimiter, encoding, maxLength));
		}

		@Override
		public final byte[] readBytes() throws IOException, ClosedConnectionException, SocketTimeoutException, MaxReadSizeExceededException {
			return DataConverter.toBytes(readByteBuffer());
		}
	
		@Override
		public final String readStringByLength(int length) throws IOException, UnsupportedEncodingException {
			return readStringByLength(length, getDefaultEncoding());
		}

		@Override
		public final String readStringByDelimiter(String delimiter, int maxLength) throws IOException, UnsupportedEncodingException, MaxReadSizeExceededException {
			return readStringByDelimiter(delimiter, getDefaultEncoding(), maxLength);
		}	
	
		@Override
		public final ByteBuffer[] readByteBufferByDelimiter(String delimiter, int maxLength) throws IOException, MaxReadSizeExceededException {
			return readByteBufferByDelimiter(delimiter, getDefaultEncoding(), maxLength); 
		}	
	}
	
	
	private static final class BlockingReadableBodyChannelAdapter implements IBlockingReadableChannel, IBodyParser {
		
		private final Object readGuard = new Object();
		private Integer receiveTimeoutMillis = Integer.MAX_VALUE;


		private AbstractReadableBodyChannel delegee = null;
		
		
		BlockingReadableBodyChannelAdapter(AbstractReadableBodyChannel delegee) {
			this.delegee = delegee;
		}
		
		
	
		public final void setReceiveTimeoutMillis(int timeout) throws IOException {
			this.receiveTimeoutMillis = timeout;
		}
		
		public final int getReceiveTimeoutMillis() {
			return receiveTimeoutMillis;
		}
		
		@Override
		public boolean isRead() {
			return delegee.isRead();
		}

		
		@Override
		public boolean isOpen() {
			return delegee.isOpen();
		}
		
		@Override
		public void close() throws IOException {
			delegee.close();
		}
		
		@Override
		public INonBlockingReadableChannelHandler onData() throws IOException {
			delegee.onData();
			
			synchronized (readGuard) {
				readGuard.notify();
			}
			
			return null;
		}
	
		
		private boolean hasEnoughData(int requiredLength) throws MaxReadSizeExceededException {
			if (requiredLength <= delegee.getNumberOfAvailableBytes()) {
				return true;
			} else {
				if (delegee.isRead()) {
					throw new MaxReadSizeExceededException();
				}
				
				return false;
			}
		}
		
		
		@Override
		public int read(ByteBuffer buffer) throws IOException {
			long start = System.currentTimeMillis();
			long remainingTime = getReceiveTimeoutMillis();

			synchronized (readGuard) {
				do {
					if (delegee.isRead() && (delegee.getNumberOfAvailableBytes() == 0)) {
						return -1;
					}
				
					if (delegee.getNumberOfAvailableBytes() > 0) {
						return delegee.read(buffer);
					} else {
						try {
							readGuard.wait(remainingTime);
						} catch (InterruptedException ignore) { }
					} 
					remainingTime = (start + getReceiveTimeoutMillis()) - System.currentTimeMillis();
				} while (remainingTime> 0);
			}

			throw new SocketTimeoutException("timeout " + DataConverter.toFormatedDuration(getReceiveTimeoutMillis()) + " reached");
		}
		
		
		@Override
		public byte readByte() throws IOException {
			long start = System.currentTimeMillis();
			long remainingTime = getReceiveTimeoutMillis();

			synchronized (readGuard) {
				do {
					if (hasEnoughData(1)) {
						return delegee.readByte();
					} else {
						try {
							readGuard.wait(remainingTime);
						} catch (InterruptedException ignore) { }
					} 
					remainingTime = (start + getReceiveTimeoutMillis()) - System.currentTimeMillis();
				} while (remainingTime> 0);
			}

			throw new SocketTimeoutException("timeout " + DataConverter.toFormatedDuration(getReceiveTimeoutMillis()) + " reached");
		}

		
		@Override
		public long transferTo(WritableByteChannel outputChannel) throws ClosedConnectionException, IOException, SocketTimeoutException, MaxReadSizeExceededException {
			long written = 0;
			ByteBuffer[] buffers = readByteBuffer();
			for (ByteBuffer buffer : buffers) {
				written += outputChannel.write(buffer);
			}
			return written;
		}

		
		@Override
		public String readString() throws IOException, ClosedConnectionException, SocketTimeoutException {
			return DataConverter.toString(readByteBuffer());
		}
		
		@Override
		public byte[] readBytes() throws IOException, ClosedConnectionException, SocketTimeoutException {
			return DataConverter.toBytes(readByteBuffer());
		}
		
		
		@Override
		public ByteBuffer[] readByteBuffer() throws IOException, ClosedConnectionException, SocketTimeoutException {
			long start = System.currentTimeMillis();
			long remainingTime = getReceiveTimeoutMillis();

			synchronized (readGuard) {
				do {
					if (delegee.isRead()) {
						if (delegee.getNumberOfAvailableBytes() == 0) {
							throw new MaxReadSizeExceededException();
						} else {
							return readByteBufferByLength(delegee.getNumberOfAvailableBytes());
						}
					} else {
						try {
							readGuard.wait(remainingTime);
						} catch (InterruptedException ignore) { }
					} 
					remainingTime = (start + getReceiveTimeoutMillis()) - System.currentTimeMillis();
				} while (remainingTime> 0);
			}

			throw new SocketTimeoutException("timeout " + DataConverter.toFormatedDuration(getReceiveTimeoutMillis()) + " reached");

		}
		
		@Override
		public ByteBuffer[] readByteBufferByDelimiter(String delimiter) throws IOException {
			synchronized (readGuard) {
				return readByteBufferByDelimiter(delimiter, delegee.getNumberOfAvailableBytes());
			}
		}
		
		@Override
		public ByteBuffer[] readByteBufferByDelimiter(String delimiter, int maxLength) throws IOException, MaxReadSizeExceededException {
			return readByteBufferByDelimiter(delimiter, delegee.getDefaultEncoding(), maxLength);
		}
		
		@Override
		public ByteBuffer[] readByteBufferByDelimiter(String delimiter, String encoding, int maxLength) throws IOException, MaxReadSizeExceededException {
			long start = System.currentTimeMillis();
			long remainingTime = getReceiveTimeoutMillis();
			
			synchronized (readGuard) {
				do {
					if (hasEnoughData(1)) {
						int available = delegee.getNumberOfAvailableBytes();
						boolean reducedSize = false;
						if (available < maxLength) {
							reducedSize = true;							
							maxLength = available;
						}
						
						try {
							return delegee.readByteBufferByDelimiter(delimiter, maxLength);
						} catch (MaxReadSizeExceededException mre) {
							if (!reducedSize) {
								throw mre;
							}
						}
					} else {
						try {
							readGuard.wait(remainingTime);
						} catch (InterruptedException ignore) { }
					}
					remainingTime = (start + getReceiveTimeoutMillis()) - System.currentTimeMillis();
				} while (remainingTime> 0);
			}

			throw new SocketTimeoutException("timeout " + DataConverter.toFormatedDuration(getReceiveTimeoutMillis()) + " reached");
		}
		
		
		@Override
		public ByteBuffer[] readByteBufferByLength(int length) throws IOException {
			long start = System.currentTimeMillis();
			long remainingTime = getReceiveTimeoutMillis();

			synchronized (readGuard) {
				do {
					if (hasEnoughData(length)) {
						return delegee.readByteBufferByLength(length);
					} else {
						try {
							readGuard.wait(remainingTime);
						} catch (InterruptedException ignore) { }
					} 
					remainingTime = (start + getReceiveTimeoutMillis()) - System.currentTimeMillis();
				} while (remainingTime> 0);
			}

			throw new SocketTimeoutException("timeout " + DataConverter.toFormatedDuration(getReceiveTimeoutMillis()) + " reached");
		}
		
		@Override
		public byte[] readBytesByDelimiter(String delimiter, int maxLength) throws IOException, MaxReadSizeExceededException {
			ByteBuffer[] buffers = readByteBufferByDelimiter(delimiter, maxLength); 
			return DataConverter.toBytes(buffers);
		}
		
		@Override
		public byte[] readBytesByLength(int length) throws IOException {
			long start = System.currentTimeMillis();
			long remainingTime = getReceiveTimeoutMillis();

			synchronized (readGuard) {
				do {
					if (hasEnoughData(length)) {
						return delegee.readBytesByLength(length);
						
					} else {
						try {
							readGuard.wait(remainingTime);
						} catch (InterruptedException ignore) { }
					} 
					remainingTime = (start + getReceiveTimeoutMillis()) - System.currentTimeMillis();
				} while (remainingTime> 0);
			}

			throw new SocketTimeoutException("timeout " + DataConverter.toFormatedDuration(getReceiveTimeoutMillis()) + " reached");
		}
		
		@Override
		public double readDouble() throws IOException {
			long start = System.currentTimeMillis();
			long remainingTime = getReceiveTimeoutMillis();

			synchronized (readGuard) {
				do {
					if (hasEnoughData(8)) {
						return delegee.readDouble();
						
					} else {
						try {
							readGuard.wait(remainingTime);
						} catch (InterruptedException ignore) { }
					} 
					remainingTime = (start + getReceiveTimeoutMillis()) - System.currentTimeMillis();
				} while (remainingTime> 0);
			}

			throw new SocketTimeoutException("timeout " + DataConverter.toFormatedDuration(getReceiveTimeoutMillis()) + " reached");
		}
		
		@Override
		public int readInt() throws IOException {
			long start = System.currentTimeMillis();
			long remainingTime = getReceiveTimeoutMillis();

			synchronized (readGuard) {
				do {
					if (hasEnoughData(4)) {
						return delegee.readInt();
						
					} else {
						try {
							readGuard.wait(remainingTime);
						} catch (InterruptedException ignore) { }
					} 
					remainingTime = (start + getReceiveTimeoutMillis()) - System.currentTimeMillis();
				} while (remainingTime> 0);
			}

			throw new SocketTimeoutException("timeout " + DataConverter.toFormatedDuration(getReceiveTimeoutMillis()) + " reached");
		}
		
		@Override
		public long readLong() throws IOException {
			long start = System.currentTimeMillis();
			long remainingTime = getReceiveTimeoutMillis();

			synchronized (readGuard) {
				do {
					if (hasEnoughData(8)) {
						return delegee.readLong();
						
					} else {
						try {
							readGuard.wait(remainingTime);
						} catch (InterruptedException ignore) { }
					} 
					remainingTime = (start + getReceiveTimeoutMillis()) - System.currentTimeMillis();
				} while (remainingTime> 0);
			}

			throw new SocketTimeoutException("timeout " + DataConverter.toFormatedDuration(getReceiveTimeoutMillis()) + " reached");
		}
		
		@Override
		public short readShort() throws IOException {
			long start = System.currentTimeMillis();
			long remainingTime = getReceiveTimeoutMillis();

			synchronized (readGuard) {
				do {
					if (hasEnoughData(2)) {
						return delegee.readShort();
						
					} else {
						try {
							readGuard.wait(remainingTime);
						} catch (InterruptedException ignore) { }
					} 
					remainingTime = (start + getReceiveTimeoutMillis()) - System.currentTimeMillis();
				} while (remainingTime> 0);
			}

			throw new SocketTimeoutException("timeout " + DataConverter.toFormatedDuration(getReceiveTimeoutMillis()) + " reached");
		}
		
		@Override
		public String readStringByDelimiter(String delimiter) throws IOException, UnsupportedEncodingException {
			ByteBuffer[] buffers = readByteBufferByDelimiter(delimiter);
			return DataConverter.toString(buffers);
		}
		
		@Override
		public String readStringByDelimiter(String delimiter, int maxLength) throws IOException, UnsupportedEncodingException, MaxReadSizeExceededException {
			ByteBuffer[] buffers = readByteBufferByDelimiter(delimiter, maxLength);
			return DataConverter.toString(buffers);
		}
		
		@Override
		public String readStringByDelimiter(String delimiter, String encoding, int maxLength) throws IOException, UnsupportedEncodingException, MaxReadSizeExceededException {
			ByteBuffer[] buffers = readByteBufferByDelimiter(delimiter, encoding, maxLength);
			return DataConverter.toString(buffers);
		}
		
		@Override
		public String readStringByLength(int length) throws IOException, UnsupportedEncodingException {
			return readStringByLength(length, delegee.getDefaultEncoding());
		}
		
		
		@Override
		public String readStringByLength(int length, String encoding) throws IOException, UnsupportedEncodingException {
			long start = System.currentTimeMillis();
			long remainingTime = getReceiveTimeoutMillis();

			synchronized (readGuard) {
				do {
					if (hasEnoughData(length)) {
						return delegee.readStringByLength(length, encoding);
						
					} else {
						try {
							readGuard.wait(remainingTime);
						} catch (InterruptedException ignore) { }
					} 
					remainingTime = (start + getReceiveTimeoutMillis()) - System.currentTimeMillis();
				} while (remainingTime> 0);
			}

			throw new SocketTimeoutException("timeout " + DataConverter.toFormatedDuration(getReceiveTimeoutMillis()) + " reached");
		}
	}
	
	
	
	private final class ReadableBodyChannel extends AbstractReadableBodyChannel {
		
		private boolean isOpen = true;
		private int remainingDataLength = 0;
		
		ReadableBodyChannel(int contentLength) {
			remainingDataLength = contentLength;
		}
		
		@Override
		public boolean isRead() {
			return (HttpConnection.this.getNumberOfAvailableBytes() >= remainingDataLength);
		}
		
		@Override
		public int getNumberOfAvailableBytes() {
			int available = HttpConnection.this.getNumberOfAvailableBytes();
			if (remainingDataLength < available) {
				available = remainingDataLength;
			}
			return available;
		}
		
		

		
		@Override
		public void close() throws IOException {
			if (isOpen) {
				isOpen = false;
				 removeBodyParser();
			}
		}
		
		@Override
		public boolean isOpen() {
			return isOpen;
		}
		
		@Override
		public int read(ByteBuffer buffer) throws IOException {
			if (remainingDataLength == 0) {
				return -1;
			}
			
			int read = HttpConnection.this.read(buffer);
			decRemainingDataSize(read);

			return read;
		}
		
		
		@Override
		public byte readByte() throws IOException {
			if (remainingDataLength >= 1) {
				byte b = HttpConnection.this.readByte();
				decRemainingDataSize(1);
				
				return b;
			} else {
				throw new MaxReadSizeExceededException();
			}
		}

	
		@Override
		public ByteBuffer[] readByteBufferByDelimiter(String delimiter, String encoding, int maxLength) throws IOException, MaxReadSizeExceededException {
			if (maxLength > remainingDataLength) {
				maxLength = remainingDataLength;
			}
			ByteBuffer[] buffers = HttpConnection.this.readByteBufferByDelimiter(delimiter, encoding, maxLength);
			int read = 0;
			for (ByteBuffer buffer : buffers) {
				read += buffer.remaining(); 
			}
			decRemainingDataSize(read);

			return buffers; 
		}
		
		@Override
		public ByteBuffer[] readByteBufferByLength(int length) throws IOException {
			if (remainingDataLength >= length) {
				ByteBuffer[] buffers = HttpConnection.this.readByteBufferByLength(length);
				int read = 0;
				for (ByteBuffer buffer : buffers) {
					read += buffer.remaining(); 
				}
				decRemainingDataSize(read);
				
				return buffers; 

			} else {
				throw new MaxReadSizeExceededException();
			}
		}
		
		@Override
		public String readStringByDelimiter(String delimiter) throws IOException, UnsupportedEncodingException {
			return readStringByDelimiter(delimiter, remainingDataLength);
		}
		
		
		@Override
		public ByteBuffer[] readByteBufferByDelimiter(String delimiter) throws IOException {
			return readByteBufferByDelimiter(delimiter, remainingDataLength);
		}
		
	
		
		
		@Override
		public byte[] readBytesByDelimiter(String delimiter, int maxLength) throws IOException, MaxReadSizeExceededException {
			if (maxLength > remainingDataLength) {
				maxLength = remainingDataLength;
			}
			byte[] bytes = HttpConnection.this.readBytesByDelimiter(delimiter, maxLength);
			decRemainingDataSize(bytes.length);

			return bytes;
		}
		
		@Override
		public byte[] readBytesByLength(int length) throws IOException {
			if (remainingDataLength >= length) {
				byte[] bytes = HttpConnection.this.readBytesByLength(length);
				decRemainingDataSize(bytes.length);

				return bytes;

			} else {
				throw new MaxReadSizeExceededException();
			}
		}
		
		@Override
		public double readDouble() throws IOException {
			if (remainingDataLength >= 8) {
				double d = HttpConnection.this.readDouble();
				decRemainingDataSize(8);

				return d;
			} else {
				throw new MaxReadSizeExceededException();
			}
		}
		
		@Override
		public int readInt() throws IOException {
			if (remainingDataLength >= 4) {
				int i = HttpConnection.this.readInt();
				decRemainingDataSize(4);

				return i;
			} else {
				throw new MaxReadSizeExceededException();
			}
		}
		
		@Override
		public long readLong() throws IOException {
			if (remainingDataLength >= 8) {
				long l = HttpConnection.this.readLong();
				decRemainingDataSize(8);

				return l;
			} else {
				throw new MaxReadSizeExceededException();
			}
		}
		
		@Override
		public short readShort() throws IOException {
			if (remainingDataLength >= 2) {
				short s = HttpConnection.this.readShort();
				decRemainingDataSize(2);

				return s;
			} else {
				throw new MaxReadSizeExceededException();
			}
		}
		
	
		
		@Override
		public String readStringByDelimiter(String delimiter, String encoding, int maxLength) throws IOException, UnsupportedEncodingException, MaxReadSizeExceededException {
			if (maxLength > remainingDataLength) {
				maxLength = remainingDataLength;
			}
			ByteBuffer[] buffers = readByteBufferByDelimiter(delimiter, encoding, maxLength);
			int read = 0;
			for (ByteBuffer buffer : buffers) {
				read += buffer.remaining(); 
			}
			decRemainingDataSize(read);

			return DataConverter.toString(buffers, encoding);
		}
		
	
		
		@Override
		public String readStringByLength(int length, String encoding) throws IOException, UnsupportedEncodingException {
			if (remainingDataLength >= length) {
				return DataConverter.toString(readByteBufferByLength(length), encoding);
				
			} else {
				throw new MaxReadSizeExceededException();
			}
		}
	


		@Override
		public ByteBuffer[] readByteBuffer() throws IOException, ClosedConnectionException, SocketTimeoutException, MaxReadSizeExceededException {
			if ((remainingDataLength == 0) && (getNumberOfAvailableBytes() == 0)) {
				throw new MaxReadSizeExceededException();
			}
			
			return HttpConnection.this.readByteBuffer();
		}
		
		
		@Override
		public long transferTo(WritableByteChannel outputChannel) throws ClosedConnectionException, IOException, SocketTimeoutException, MaxReadSizeExceededException {
			if ((remainingDataLength == 0) && (getNumberOfAvailableBytes() == 0)) {
				return -1;
			}

			int read = 0; 
			for (ByteBuffer buffer : HttpConnection.this.readByteBuffer()) {
				read += outputChannel.write(buffer);
			}
			
			decRemainingDataSize(read);
			
			return read;
		}
		
		
		private void decRemainingDataSize(int read) throws IOException {
			remainingDataLength -= read;
			
			if (isRead()) {
				 removeBodyParser();
			}
		}

	}

	
	
	
	private final class ReadableChunkedBodyChannel extends AbstractReadableBodyChannel {
		
		private boolean isOpen = true;
		
		private boolean isTerminatorReceived = false;
		
		ReadableChunkedBodyChannel() {
		}
		
		@Override
		public boolean isRead() {
			return isTerminatorReceived;
		}

		@Override
		public int getNumberOfAvailableBytes() {
			return 0;
		}

		
		@Override
		public void close() throws IOException {
			if (isOpen) {
				isOpen = false;
				 removeBodyParser();
			}
		}
		
		@Override
		public boolean isOpen() {
			return isOpen;
		}
		
		@Override
		public int read(ByteBuffer buffer) throws IOException {
			// TODO Auto-generated method stub
			return 0;
		}
		
		@Override
		public byte readByte() throws IOException {
			// TODO Auto-generated method stub
			return 0;
		}
		
		@Override
		public ByteBuffer[] readByteBufferByDelimiter(String delimiter)
				throws IOException {
			// TODO Auto-generated method stub
			return null;
		}
		
	
		
		@Override
		public ByteBuffer[] readByteBufferByDelimiter(String delimiter,
				String encoding, int maxLength) throws IOException,
				MaxReadSizeExceededException {
			// TODO Auto-generated method stub
			return null;
		}
		
		@Override
		public ByteBuffer[] readByteBufferByLength(int length)
				throws IOException {
			// TODO Auto-generated method stub
			return null;
		}
		
		@Override
		public String readStringByDelimiter(String delimiter)
				throws IOException, UnsupportedEncodingException {
			// TODO Auto-generated method stub
			return null;
		}
		
		@Override
		public byte[] readBytesByDelimiter(String delimiter, int maxLength)
				throws IOException, MaxReadSizeExceededException {
			// TODO Auto-generated method stub
			return null;
		}
		
		@Override
		public byte[] readBytesByLength(int length) throws IOException {
			// TODO Auto-generated method stub
			return null;
		}
		
		@Override
		public double readDouble() throws IOException {
			// TODO Auto-generated method stub
			return 0;
		}
		
		@Override
		public int readInt() throws IOException {
			// TODO Auto-generated method stub
			return 0;
		}
		
		@Override
		public long readLong() throws IOException {
			// TODO Auto-generated method stub
			return 0;
		}
		
		@Override
		public short readShort() throws IOException {
			// TODO Auto-generated method stub
			return 0;
		}
	
		
		@Override
		public String readStringByDelimiter(String delimiter, String encoding,
				int maxLength) throws IOException,
				UnsupportedEncodingException, MaxReadSizeExceededException {
			// TODO Auto-generated method stub
			return null;
		}
	
		
		@Override
		public String readStringByLength(int length, String encoding)
				throws IOException, UnsupportedEncodingException {
			// TODO Auto-generated method stub
			return null;
		}
		
		@Override
		public long transferTo(WritableByteChannel outputChannel)
				throws ClosedConnectionException, IOException,
				SocketTimeoutException {
			// TODO Auto-generated method stub
			return 0;
		}
		

		@Override
		public ByteBuffer[] readByteBuffer() throws IOException,
				ClosedConnectionException, SocketTimeoutException,
				MaxReadSizeExceededException {
			// TODO Auto-generated method stub
			return null;
		}
	}
	
	
	
	
	
	
	private abstract class AbstractWriteableBodyChannel implements IWriteableChannel {
	
		private LinkedList<ByteBuffer> queue = null;
		
		private boolean isOpen = true;
		private int freeSpace = 0;
		
		
		public AbstractWriteableBodyChannel(int freeSpace) {
			setFreeSpace(freeSpace);
		}
		
		
		protected final void setFreeSpace(int freeSpace) {
			this.freeSpace = freeSpace;
		}
		
		protected final int getFreeSpace() {
			return freeSpace;
		}
		
		
		protected final boolean isBufferEmpty() {
			if (queue == null) {
				return true;
			}
		
			return queue.isEmpty();
		}
		
		
		

		public final int transferFrom(ReadableByteChannel contentSource) throws IOException, ClosedConnectionException {
			int transferChunkSize = getSendBufferSize() - ((int) (getSendBufferSize() * 0.1)); 
			return transferFrom(contentSource, transferChunkSize);
		}

		private final int transferFrom(ReadableByteChannel contentSource, int transferChunkSize) throws IOException, ClosedConnectionException {
			
			int transfered = 0;


			int read = 0;
			do {
				ByteBuffer transferBuffer = ByteBuffer.allocate(transferChunkSize);
				read = contentSource.read(transferBuffer);
				
				if (read > 0) { 
					if (transferBuffer.remaining() == 0) {
						transferBuffer.flip();
						write(transferBuffer);
						
					} else {
						transferBuffer.flip();
						write(transferBuffer.slice());
					}
					
					transfered += read;
				}
			} while (read > 0);
			
			
			return transfered;
		}
		
		
		@Override
		public final int write(byte b) throws IOException {
			return write(DataConverter.toByteBuffer(b));
		}
		
		@Override
		public final int write(double d) throws IOException {
			return write(DataConverter.toByteBuffer(d));
		}
		
		@Override
		public final int write(int i) throws IOException {
			return write(DataConverter.toByteBuffer(i));
		}
		
		@Override
		public final int write(long l) throws IOException {
			return write(DataConverter.toByteBuffer(l));
		}
		
		@Override
		public final int write(short s) throws IOException {
			return write(DataConverter.toByteBuffer(s));
		}
		
		@Override
		public final int write(String message) throws IOException {
			return write(message, HttpConnection.this.getDefaultEncoding());
		}
		
		@Override
		public final int write(String message, String encoding) throws IOException {
			return write(DataConverter.toByteBuffer(message, encoding));
		}
		
		@Override
		public final int write(byte[] bytes, int offset, int length) throws IOException {
			return write(ByteBuffer.wrap(bytes, offset, length));
		}
		
		@Override
		public final int write(byte... bytes) throws IOException {
			return write(ByteBuffer.wrap(bytes));
		}

		
		@Override
		public int write(ByteBuffer buffer) throws IOException {
			int written = buffer.remaining();
			
			if (queue == null) {
				queue = new LinkedList<ByteBuffer>();
			}
			queue.add(buffer);
			
			freeSpace -= written;
			return written;
		}

		
		@Override
		public final long write(ByteBuffer[] buffers) throws IOException {
			long written = 0;
			for (ByteBuffer buffer : buffers) {
				written += write(buffer);
			}
			
			if (queue == null) {
				queue = new LinkedList<ByteBuffer>(Arrays.asList(buffers));
			} else {
				queue.addAll(Arrays.asList(buffers));
			}
			
			freeSpace -= written;
			return written;
		}

	
		@Override
		public long write(LinkedList<ByteBuffer> buffers) throws IOException {
			long written = 0;
			for (ByteBuffer buffer : buffers) {
				written += write(buffer);
			}
			
			if (queue == null) {
				queue = buffers;
			} else {
				queue.addAll(buffers);
			}
			
			freeSpace -= written;
			return written;
		}

		
		@Override
		public void close() throws IOException {
			isOpen = false;
			isOpenWriteTransaction = false;
		}

		
		@Override
		public final boolean isOpen() {
			return isOpen;
		}
		
		
		protected final LinkedList<ByteBuffer> drainBuffer() {
			LinkedList<ByteBuffer> buffers = queue;
			queue = null;
			return buffers;
		}
	}

	
	
	private final class WriteableBodyChannel extends AbstractWriteableBodyChannel {
				
		public WriteableBodyChannel(int freeSpace) {
			super(freeSpace);
		}


		@Override
		public int write(ByteBuffer buffer) throws IOException {
			if (isOpen()) {
				int remaining = buffer.remaining();
				if (remaining < getFreeSpace()) {
					int written = super.write(buffer);
					return written;
					
				} else if (remaining == getFreeSpace()) {
					int written =  super.write(buffer);
					flush();
					return written;
					
				} else {
					buffer.limit(buffer.position() + getFreeSpace());
					int written = write(buffer.slice());

					LOG.warning("message body size exceeded. Ignoring additional data to write");
					return written;
				}
				
			} else {
				// TODO handle closed case
				return 0;
			}
		}

		
		@Override
		public final void flush() throws IOException {
			if (isOpen()) {
				HttpConnection.this.write(drainBuffer());
				HttpConnection.this.flush();
			} else {
				// TODO handle 
			}
		}
		
		@Override
		public void close() throws IOException {
			if (isOpen()) {
				flush();
				super.close();
			}
		}
	}

	
	
	private final class WriteableChunkedBodyChannel extends AbstractWriteableBodyChannel {

		private int maxChunkSize = 0;
		
		WriteableChunkedBodyChannel(int maxChunkSize) {
			super(maxChunkSize);
			this.maxChunkSize = maxChunkSize;
		}

		@Override
		public int write(ByteBuffer buffer) throws IOException {

			if (isOpen()) {
				
				// enough space i chunk 
				int remaining = buffer.remaining();
				if (remaining < getFreeSpace()) {
					int written =  super.write(buffer);
					return written;
					
				// chunk size reached 
				} else if (remaining == getFreeSpace()) {
					int written =  super.write(buffer);
					flush();
					return written;
					
				// chunk size exceeded
				} else {
					int pos = buffer.position();
					int limit = buffer.limit();
					
					// split chunk
					int lengthToWrite = getFreeSpace();
					buffer.limit(pos + lengthToWrite);
					int written = write(buffer.slice());
					
					// write remaining
					buffer.position(pos + lengthToWrite);
					buffer.limit(limit);
					written += write(buffer.slice());
					
					return written;
				}
				
			} else {
				// TODO handle closed case
				return 0;
			}
		}

		
		@Override
		public void flush() throws IOException {
			if (isOpen()) {
				writeChunk();
				HttpConnection.this.flush();
			} else {
				// TODO handle 
			}
		}
		
		
		private void writeChunk() throws IOException {

			if (!isBufferEmpty()) {

				LinkedList<ByteBuffer> buffers = drainBuffer();

				int size = 0;
				for (ByteBuffer buffer : buffers) {
					size += buffer.remaining(); 
				}
				
				HttpConnection.this.write(Integer.toString(size, 16).toUpperCase() + "\r\n");
				HttpConnection.this.write(buffers);
				HttpConnection.this.write("\r\n");
				
				setFreeSpace(maxChunkSize);
			}
		}
		
		
		@Override
		public void close() throws IOException {
			if (isOpen()) {
				writeChunk();
				HttpConnection.this.write("0\r\n\r\n");
				HttpConnection.this.flush();
				super.close();
			}
		}
	}
}