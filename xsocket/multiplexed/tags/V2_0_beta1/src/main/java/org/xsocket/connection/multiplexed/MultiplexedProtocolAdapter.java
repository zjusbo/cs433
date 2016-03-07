/*
 *  Copyright (c) xsocket.org, 2006 - 2008. All rights reserved.
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
package org.xsocket.connection.multiplexed;


import java.io.IOException;


import org.xsocket.Execution;
import org.xsocket.ILifeCycle;
import org.xsocket.Resource;
import org.xsocket.connection.IConnectHandler;
import org.xsocket.connection.IHandler;
import org.xsocket.connection.INonBlockingConnection;
import org.xsocket.connection.IServer;
import org.xsocket.connection.Server;
import org.xsocket.connection.multiplexed.multiplexer.DefaultMultiplexer;
import org.xsocket.connection.multiplexed.multiplexer.IMultiplexer;



/**
 * A helper class to register {@link IPipelineHandler} on the {@link Server}
 * 
 * <pre>
 *   ...
 *   IServer server = new Server(new MultiplexedProtocolAdapter(new MyHandler()));
 *   ConnectionUtils.start(server);
 *   ...
 *
 *   
 *   class MyHandler implements IPipelineDataHandler {
 *
 *      public boolean onData(INonBlockingPipeline pipeline) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
 *          byte[] data = pipeline.readBytesByDelimiter(DELIMITER);
 *          pipeline.write(data);
 *          pipeline.write(DELIMITER);
 *          pipeline.flush();
 *          
 *          return true;
 *      }
 *   }
 *
 * </pre>
 * 
 * 
 * 
 * @author grro
 */
@Execution(Execution.NONTHREADED)
public final class MultiplexedProtocolAdapter implements IConnectHandler, ILifeCycle {
	
	
	
	@Resource
	private IServer server = null;

	private IHandler handler = null;
	private PipelineHandlerAdapter handlerAdapter = null;
	private IMultiplexer multiplexer = null;
	
	
	public MultiplexedProtocolAdapter(IHandler handler) {
		this(handler, new DefaultMultiplexer());
	}
	
	  
	
	public MultiplexedProtocolAdapter(IHandler handler, IMultiplexer multiplexer) {
		this.handler = handler;
		this.multiplexer = multiplexer;
	}
		

	public void onInit() {
		server.setStartUpLogMessage(server.getStartUpLogMessage() + "; multiplexed " + MultiplexedUtils.getVersionInfo());
		
		handlerAdapter = PipelineHandlerAdapter.newInstance(handler);
		
		MultiplexedUtils.injectServerField(server, handlerAdapter.getHandler());
		handlerAdapter.onInit();
	}
	
	public void onDestroy() {
		handlerAdapter.onDestroy();
	}
	

	public boolean onConnect(INonBlockingConnection connection) throws IOException {
		// the multiplexed connection replaces the current handler by a own one
		new MultiplexedConnection(connection, handlerAdapter, multiplexer);		
		return true;
	}	
}
