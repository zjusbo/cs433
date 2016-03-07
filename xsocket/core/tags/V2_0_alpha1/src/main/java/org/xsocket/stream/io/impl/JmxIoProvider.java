// $Id$
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
package org.xsocket.stream.io.impl;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.management.JMException;
import javax.management.ObjectName;

import org.xsocket.Dispatcher;
import org.xsocket.IDispatcher;
import org.xsocket.IntrospectionBasedDynamicBean;
import org.xsocket.stream.IServerListener;
import org.xsocket.stream.Server;
import org.xsocket.stream.io.spi.IAcceptor;
import org.xsocket.stream.io.spi.IAcceptorMXBean;
import org.xsocket.stream.io.spi.IServerIoJmxProvider;



/**
 * A Mbean proxy factory, which creates and registers an appropriated mbean
 * for a given {@link MultithreadedServer} instance.
 *
 * <br><br><b>This class is for test purpose only, and will be modified or discarded in future versions</b>
 *
 * @author grro@xsocket.org
 */
public final class JmxIoProvider implements IServerIoJmxProvider {

	public IAcceptorMXBean createMBean(Server server, IAcceptor acceptor, String domain, String address) throws JMException {

		if (acceptor instanceof Acceptor) {

			IStdAcceptorMXBean acceptorMXBean = new AcceptorMXBeanAdapter((Acceptor) acceptor);

			DispatcherPoolListener dispatcherPoolListener = new DispatcherPoolListener(server, domain, address);
			((Acceptor) acceptor).getDispatcherPool().addListener(dispatcherPoolListener);

			for (IDispatcher dispatcher : ((Acceptor) acceptor).getDispatchers()) {
				try {
					dispatcherPoolListener.onDispatcherAdded((Dispatcher) dispatcher);
				} catch(Exception ignore) { }
			}

			server.addListener(new ServerListener());

			return acceptorMXBean;

		} else {
			throw new JMException("only accpetor of instance " + Acceptor.class.getName() + " is supported, not " + acceptor.getClass().getName());
		}
	}





	private static final class AcceptorMXBeanAdapter implements IStdAcceptorMXBean  {

		private Acceptor delegee = null;

		public AcceptorMXBeanAdapter(Acceptor delegee) {
			this.delegee = delegee;
		}

		public long getNumberOfConnectionTimeouts() {
			return delegee.getNumberOfConnectionTimeouts();
		}

		public long getNumberOfHandledConnections() {
			return delegee.getNumberOfHandledConnections();
		}

		public long getNumberOfIdleTimeouts() {
			return delegee.getNumberOfIdleTimeouts();
		}

		public List<String> getOpenConnections() {
			List<String> result = new ArrayList<String>();

			for (IoSocketHandler hdl : delegee.getOpenConnections()) {
				result.add(hdl.toString());
			}

			return result;
		}

		public int getNumberOfOpenConnections() {
			return delegee.getNumberOfOpenConnections();
		}

		public int getReceiveBufferPreallocationSize() {
			return delegee.getReceiveBufferPreallocationSize();
		}

		public void setReceiveBufferPreallocationSize(int size) {
			delegee.setReceiveBufferPreallocationSize(size);
		}

		public String getLocalHost() {
			return delegee.getLocalAddress().getCanonicalHostName();
		}

		public int getLocalPort() {
			return delegee.getLocalPort();
		}

		public int getDispatcherPoolSize() {
			return delegee.getDispatcherPoolSize();
		}

		public void setDispatcherPoolSize(int size) {
			delegee.setDispatcherPoolSize(size);
		}

		public void resetStatistics() {
			delegee.resetStatistics();
		}

		public long getStatisiticsStartTime() {
			return delegee.getStatisticsStartTime();
		}
	}


	private static final class IoSocketDispatcherMXBeanAdapter implements IIoSocketDispatcherMXBean {

		private IoSocketDispatcher delegee = null;

		public IoSocketDispatcherMXBeanAdapter(IoSocketDispatcher delegee) {
			this.delegee = delegee;
		}

		public int getPreallocatedReadMemorySize() {
			return delegee.getPreallocatedReadMemorySize();
		}

		public long getCountConnectionTimeout() {
			return delegee.getCountConnectionTimeout();
		}

		public long getCountIdleTimeout() {
			return delegee.getCountIdleTimeout();
		}

		public long getNumberOfHandledReads() {
			return delegee.getNumberOfHandledReads();
		}

		public long getNumberOfHandledRegistrations() {
			return delegee.getNumberOfHandledRegistrations();
		}

		public long getNumberOfHandledWrites() {
			return delegee.getNumberOfHandledWrites();
		}

		public void resetStatistics() {
			delegee.resetStatistics();
		}

		public long getStatisticsStartTime() {
			return delegee.getStatisticsStartTime();
		}
	}



	private static final class ServerListener implements IServerListener {

		public void onInit() {

		}

		public void onDestroy() {

		}
	}









	private  final class DispatcherPoolListener implements IIoSocketDispatcherPoolListener {


		private Server server = null;
		private String domain = null;
		private String address = null;

		DispatcherPoolListener(Server server, String domain, String address) {
			this.server = server;
			this.domain = domain;
			this.address = address;
		}



		public void onDispatcherAdded(IDispatcher dispatcher) {

			try {
				ObjectName objectName = new ObjectName(domain +":type=Dispatcher,name=" + address + "." + dispatcher.hashCode());
				ManagementFactory.getPlatformMBeanServer().registerMBean(new IoSocketDispatcherMXBeanAdapter((IoSocketDispatcher) dispatcher), objectName);
			} catch (Exception ignore) { }
		}


		public void onDispatcherRemoved(IDispatcher dispatcher) {
			try {
				ObjectName objectName = new ObjectName(domain +":type=Dispatcher,name=" + address + "." + dispatcher.hashCode());
				ManagementFactory.getPlatformMBeanServer().unregisterMBean(objectName);
			} catch (Exception ignore) { }
		}
	}
}