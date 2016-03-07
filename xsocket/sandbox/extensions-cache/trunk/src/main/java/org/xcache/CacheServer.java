// $Id: DataConverter.java 1546 2007-07-23 06:07:56Z grro $

/*
 *  Copyright (c) xcache.org, 2007. All rights reserved.
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
 * The latest copy of this software may be found on http://www.xcache.org/
 */
package org.xcache;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.net.InetAddress;
import java.nio.BufferUnderflowException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.jsr107cache.Cache;

import org.xsocket.ClosedConnectionException;
import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.stream.IBlockingConnection;
import org.xsocket.stream.IConnectHandler;
import org.xsocket.stream.IConnection;
import org.xsocket.stream.IDataHandler;
import org.xsocket.stream.INonBlockingConnection;
import org.xsocket.stream.IServer;
import org.xsocket.stream.IServerListener;
import org.xsocket.stream.Server;
import org.xsocket.stream.StreamUtils;
import org.xsocket.stream.IConnection.FlushMode;



/**
 * 
 * 
 * @author grro@xcache.org
 */
public final class CacheServer implements IServer {
	
	private static final Logger LOG = Logger.getLogger(CacheServer.class.getName());
	
	static final int IDLE_TIMEOUT_SEC = 6 * 60 * 60;  // 6 hours
    static final int CONNECTION_TIMEOUT_SEC = Integer.MAX_VALUE;  // no timeout  

	static final byte CMD_PUT = 88;
	static final byte CMD_GET = 89;
	
	static final byte CMD_ADD_SEGMENT = 92;
	static final byte CMD_TRANSFER_SEGMENT = 112;
	static final byte CMD_RECEIVE_SEGMENT = 113;

	static final byte RESULT_OK_WITHOUT_RETURNVALUE = 55;
	static final byte RESULT_OK_WITH_RETURNVALUE = 77;
	
	private static String implementationVersion = null;

	
	private final Map<Integer, Segment> segmentMap = new HashMap<Integer, Segment>();
	
	private ICacheFactory cacheFactory = null;
	private Server server = null;

	
	
	CacheServer(ICacheFactory cacheFactory) throws IOException {
		this(cacheFactory, null);
	}

	
	CacheServer(ICacheFactory cacheFactory, Executor workerpool) throws IOException {
		this.cacheFactory = cacheFactory;
		
		if (workerpool != null) {
			server = new Server(new Handler(), workerpool);
		} else {
			server = new Server(new Handler());
		}

		server.setConnectionTimeoutSec(CONNECTION_TIMEOUT_SEC);
		server.setIdleTimeoutSec(IDLE_TIMEOUT_SEC);
		
		server.setServerName("CacheServer " + getImplementationVersion());	
	}

	
	Server getUnderlyingServer() {
		return server;
	}
	
	
	int getCacheSize() {
		int size = 0;
		for (Segment segment : segmentMap.values()) {
			if (!segment.isForwardSegment()) {
				size += segment.getCache().size();
			}
		}
		return size;
	}
	
	
	public void run() {
		server.run();
	}
	
	
	public boolean isOpen() {
		return server.isOpen();
	}
	
	
	public void close() throws IOException {
		server.close();
	}
	
	
	public int getLocalPort() {
		return server.getLocalPort();
	}
	
	public InetAddress getLocalAddress() {
		return server.getLocalAddress();
	}
	
	public Executor getWorkerpool() {
		return server.getWorkerpool();
	}
	
	public Object getOption(String name) throws IOException {
		return server.getOption(name);
	}
	
	public Map<String, Class> getOptions() {
		return server.getOptions();
	}
	
	public void setConnectionTimeoutSec(int timeoutSec) {
		server.setConnectionTimeoutSec(timeoutSec);
	}
	
	public int getConnectionTimeoutSec() {
		return server.getConnectionTimeoutSec();
	}
	
	public void setIdleTimeoutSec(int timeoutInSec) {
		server.setIdleTimeoutSec(timeoutInSec);
	}
	
	public int getIdleTimeoutSec() {
		return server.getIdleTimeoutSec();
	}
	
	public void addListener(IServerListener listener) {
		server.addListener(listener);
	}
	
	public boolean removeListener(IServerListener listener) {
		return server.removeListener(listener);
	}
	

	
	String[] getSegments() {
		List<String> info = new ArrayList<String>();

		for (Integer segmentId : segmentMap.keySet()) {
			Segment segment = segmentMap.get(segmentId);
			if (segment.isForwardSegment()) {
				info.add("(" + segment + " forwarding to " + segment.getForwardAddress() + ")");
			} else {
				info.add(segment + " (size=" + segment.getCache().size() + ")");
			}
		}
		
		return info.toArray(new String[info.size()]); 
	}

	
	int getHits() {
		int hits = 0;
		
		for (Segment segment : segmentMap.values()) {
			if (!segment.isForwardSegment()) {
				hits += segment.getCache().getCacheStatistics().getCacheHits();
			}
		}
		return hits;
	}
	
	String getImplementationVersion() {
		if (implementationVersion == null) { 
			try {
				LineNumberReader lnr = new LineNumberReader(new InputStreamReader(this.getClass().getResourceAsStream("/org/xcache/version.txt")));
				String line = null;
				do {
					line = lnr.readLine();
					if (line != null) {
						if (line.startsWith("Implementation-Version=")) {
							implementationVersion = line.substring("Implementation-Version=".length(), line.length()).trim();
							break;
						}
					}
				} while (line != null);
				
				lnr.close();
			} catch (Exception ignore) { }
		}
		
		return implementationVersion;
	}


	
	
	protected void onCmd(byte cmd, INonBlockingConnection connection) throws IOException {
		switch (cmd) {
		
		case CMD_PUT:
			onPutCmd(connection);
			break;

			
		case CMD_GET:
			onGetCmd(connection);
			break;
			
		
		// TODO remove me
		case CMD_ADD_SEGMENT:
			int segmentId = connection.readInt();
			segmentMap.put(segmentId, new Segment(segmentId, cacheFactory));
			writeResult(null, connection);
			break;
			
			
		case CMD_TRANSFER_SEGMENT:
			onTransferSegment(connection);
			break;


		case CMD_RECEIVE_SEGMENT:
			onReceiveSegment(connection);
			break;

			
		default:
			if (LOG.isLoggable(Level.FINE)) {
				LOG.info("got unknown command " + cmd);
			}
			break;
		}
	}



	private void onPutCmd(INonBlockingConnection connection) throws IOException, ClosedConnectionException {
		int segmentId = connection.readInt();
		Item keyItem = Item.readFrom(connection);
		Item valueItem = Item.readUnresolvedFrom(connection);
		Item oldValueItem = null;
		
		Segment segment = segmentMap.get(segmentId);
		synchronized (segment) {
			Cache cache = segment.getCache();
			oldValueItem = (Item) cache.put(keyItem.getValue(), valueItem);	
		}
		
		writeResult(oldValueItem, connection);
	}

	
	private void onGetCmd(INonBlockingConnection connection) throws IOException, ClosedConnectionException {
		int segmentId = connection.readInt();
		Item keyItem = Item.readFrom(connection);
		Item valueItem = null;

		Segment segment = segmentMap.get(segmentId);
		synchronized (segment) {
			Cache cache = segment.getCache();
			valueItem = (Item) cache.get(keyItem.getValue());
		}
		
		writeResult(valueItem, connection);
	}

	

	static final void callTransferSegment(Address sourceService, Address targetService, int segment) throws IOException {
		IBlockingConnection connection = null;
		try {
			connection = ConnectionPool.getInstance().getConnection(sourceService);

			connection.markWritePosition();
			connection.write((int) 0); // emtpy length field
			int length = 0;
			
			length += connection.write(CacheServer.CMD_TRANSFER_SEGMENT);
			length += connection.write(segment);
			length += targetService.writeTo(connection);
			
			connection.resetToWriteMark();
			connection.write(length);
			
			connection.flush();
			
			connection.readInt();  // first position is length
			byte response = connection.readByte();
			if (response != CacheServer.RESULT_OK_WITHOUT_RETURNVALUE) {
				throw new IOException("couldn't initiate transfer. reason");
			}
			
			connection.close();
		} catch (IOException e) {
			if (connection != null) {
				try {
					ConnectionPool.getInstance().destroyConnection(connection);
				} catch (Exception ignore) { }
			}
			
			throw e;
		}
	}
	

	private void onTransferSegment(INonBlockingConnection connection) throws IOException, ClosedConnectionException {
	
		int segmentId = connection.readInt();
		Address targetService = Address.readFrom(connection);
		
		if (targetService.equals(new Address(server.getLocalAddress(), server.getLocalPort()))) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("transfer to self. ignore transfer command");
			}
			return;
		}
		
		Segment segment = segmentMap.get(segmentId);
		synchronized (segment) {
			segment.transferTo(targetService);
		}

		writeResult(null, connection);
	}

	
	

	private void onReceiveSegment(INonBlockingConnection connection) throws IOException, ClosedConnectionException {
		int segmentId = connection.readInt();
		
		synchronized (segmentMap) {
			if (segmentMap.containsKey(segmentId)) {
				LOG.warning("segment " + segmentId + " already exist. overridig it");
			} else {
				Segment segment = new Segment(segmentId, cacheFactory);
				segmentMap.put(segmentId, segment);
			}
		}
		
		Segment segment = segmentMap.get(segmentId);
		synchronized (segment) {
			segment.readFrom(connection);
		}
		
		writeResult(null, connection);
	}
	
	
	
	private void writeResult(Item data, IConnection connection) throws IOException {
		
		connection.markWritePosition();
		connection.write((int) 0); 
		int length = 0;
		
		if (data == null) {
			length += connection.write(RESULT_OK_WITHOUT_RETURNVALUE);
			
		} else {
			length += connection.write(RESULT_OK_WITH_RETURNVALUE);
			length += data.writeTo(connection);
		}

		connection.resetToWriteMark();
		connection.write(length);
		
		connection.flush();
	}

	
	
	private final class Handler implements IConnectHandler, IDataHandler {
		
		public boolean onConnect(INonBlockingConnection connection) throws IOException {
			connection.setAutoflush(false);
			connection.setFlushmode(FlushMode.ASYNC);
			connection.setOption(IConnection.TCP_NODELAY, true);
			
			return true;
		}
		
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {

			StreamUtils.validateSufficientDatasizeByIntLengthField(connection);
			
			byte cmd = connection.readByte();
			onCmd(cmd, connection);
			
			return true;
		}
	}
}
