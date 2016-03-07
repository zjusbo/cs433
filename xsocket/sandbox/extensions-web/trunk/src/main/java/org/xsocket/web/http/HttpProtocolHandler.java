package org.xsocket.web.http;

import static org.xsocket.Synchronized.Mode.OFF;

import java.io.IOException;
import java.nio.BufferUnderflowException;

import javax.annotation.Resource;

import org.xsocket.ILifeCycle;
import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.Synchronized;
import org.xsocket.stream.IDataHandler;
import org.xsocket.stream.IHandler;
import org.xsocket.stream.INonBlockingConnection;
import org.xsocket.stream.INonBlockingConnectionFactory;
import org.xsocket.stream.IServer;
import org.xsocket.stream.io.spi.IHandlerIoProvider;
import org.xsocket.stream.io.spi.IIoHandler;
import org.xsocket.stream.io.spi.IIoHandlerContext;



/**
 *
 * 
 * @author grro
 */
@Synchronized(OFF)
public final class HttpProtocolHandler implements IDataHandler, ILifeCycle {

	
	@Resource
	private IServer srv = null;
	private IMessageHandler msgHandler = null;
	
		
	public HttpProtocolHandler(IMessageHandler msgHandler) {
		this.msgHandler = msgHandler;
	}
	
	
	
	@Override
	public void onInit() {
		srv.setConnectionFactory(new HttpConnectionFactory());
	}
	
	@Override
	public void onDestroy() {
		
	}
	
	public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
		((HttpConnection) connection).onIncomingData();
		return true;
	}	
	
	
	

	private final class HttpConnectionFactory implements INonBlockingConnectionFactory {

		@Override
		public INonBlockingConnection createNonBlockingConnection(IIoHandlerContext ctx, IIoHandler ioHandler, IHandler appHandler, IHandlerIoProvider ioProvider) throws IOException {
			HttpConnection con =  new HttpConnection(ctx, ioHandler, appHandler, ioProvider, msgHandler);
			return con;
		}
	}
}