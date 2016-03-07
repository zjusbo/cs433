package org.xsocket.server;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.logging.Logger;

import javax.management.ObjectName;
import javax.management.StandardMBean;

import org.xsocket.INonBlockingConnection;




final class EchoHandler implements IDataHandler, ILifeCycle {
	
	private static final Logger LOG = Logger.getLogger(EchoHandler.class.getName());
	
	static final String DELIMITER = "\r";
	
	private static boolean TIME_TRACE = false;
	
	@Resource
	private IHandlerContext ctx;


	private EchoHandlerManagement management = null;  
	private ObjectName mbeanName = null;
	

	
	public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
		timeTrace("entering onData (pre read by delimiter)");			
		
		ByteBuffer[] buffer = connection.readByteBufferByDelimiter(DELIMITER);
		timeTrace("read by delimiter end (pre write buffer)");		

		connection.write(buffer);
		timeTrace("write buffer end (pre write delimiter)");
		
		connection.write(DELIMITER);
		timeTrace("write delimiter end");
		
		System.out.print(".");
		//management.registerCall();
		return true;
	}
	

	private void timeTrace(String msg) {
		if (TIME_TRACE) {
			System.out.println((System.nanoTime() / 1000) + " microsec [" + Thread.currentThread().getName() + "] " + msg);
		}
	}

	
	public void onInit() {
		
        try {
        	management = new EchoHandlerManagement();
        	StandardMBean mbean = new StandardMBean(management , EchoHandlerMBean.class);
        	mbeanName = new ObjectName(ctx.getDomainname() + ":type=EchoHandler,name=EchoHandler");
			ManagementFactory.getPlatformMBeanServer().registerMBean(mbean, mbeanName);
        } catch (Exception mbe) {
        	LOG.warning("error " + mbe.toString() + " occured while registering mbean");
        }
	}
	
	
	public void onDestroy() {
        try {
        	ManagementFactory.getPlatformMBeanServer().unregisterMBean(mbeanName);
        } catch (Exception mbe) {
        	LOG.warning("error " + mbe.toString() + " occured while deregistering mbean");
        }
	}

		
	private static final class EchoHandlerManagement implements EchoHandlerMBean {
		
		private long calls = 0; 
		private long time = System.currentTimeMillis();
		
		synchronized void registerCall() {
			calls++;
		}

		public long getNumberOfCallsTotalSinceReset() {
			return calls;
		}
		
		public long getNumberOfCallsPerSecSinceReset() {
			return (long) ((((double) calls) * 1000) / getElapsedTimeSinceReset());
		}

		synchronized public void reset() {
			calls = 0;
			time = System.currentTimeMillis();
		}
		
		public int getElapsedTimeSinceReset() {
			return (int) (System.currentTimeMillis() - time);
		}
	}
	
	
}
