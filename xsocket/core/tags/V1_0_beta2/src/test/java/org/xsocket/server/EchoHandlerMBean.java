package org.xsocket.server;

public interface EchoHandlerMBean {

	public void reset();
		
	public int getElapsedTimeSinceReset();

	public long getNumberOfCallsTotalSinceReset();
	
	public long getNumberOfCallsPerSecSinceReset();
}
