package org.xsocket.stream;

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.mina.common.IdleStatus;
import org.apache.mina.common.IoHandlerAdapter;
import org.apache.mina.common.IoSession;
import org.apache.mina.common.TransportType;
import org.apache.mina.transport.socket.nio.SocketSessionConfig;
import org.xsocket.DataConverter;


public final class EchoHandler extends IoHandlerAdapter implements IConnectHandler, IDataHandler, ITimeoutHandler {

	private static final int PRINT_PERIOD = 5000;
	
	private int count = 0;
	private long time = System.currentTimeMillis(); 

	private Timer timer = new Timer(true);
	
	private int delay = 0;
	
	public EchoHandler(int delay) {
		this.delay = delay;
		
		TimerTask task = new TimerTask() {
			@Override
			public void run() {
				try {
					String rate = printRate();
					System.out.println(rate);
				} catch (Exception ignore) { }
			}
		};
		timer.schedule(task, PRINT_PERIOD, PRINT_PERIOD);
		
		System.out.println("Echo handler initialized. Printing each rate (req/sec) each " + DataConverter.toFormatedDuration(PRINT_PERIOD));
	}
	
	
	private void reset() {
		time = System.currentTimeMillis(); 
		count = 0;
	}

	
	public String printRate() {
		long current = System.currentTimeMillis();
		
		long rate = 0;
		
		if (count == 0) {
			rate = 0;
		} else {
			rate = (count * 1000) / (current - time);
		}

		reset();
		
		return Long.toString(rate);
	}

	
	
	/////////////////////////////////////
	// MINA callbacks 
    public void sessionCreated( IoSession session ) {
        if( session.getTransportType() == TransportType.SOCKET )
        {
            ( ( SocketSessionConfig ) session.getConfig() ).setReceiveBufferSize( 2048 );
        }
        
        session.setIdleTime( IdleStatus.BOTH_IDLE, 60 );
    }
    
    
    public void sessionIdle( IoSession session, IdleStatus status ) {
    
    }

    public void exceptionCaught( IoSession session, Throwable cause ) {
        cause.printStackTrace();
        session.close();
    }

    
    public void messageReceived( IoSession session, Object message ) throws Exception {

        org.apache.mina.common.ByteBuffer rb = ( org.apache.mina.common.ByteBuffer ) message;
        // Write the received data back to remote peer
        org.apache.mina.common.ByteBuffer wb = org.apache.mina.common.ByteBuffer.allocate( rb.remaining() );
        wb.put( rb );
        wb.flip();
        
        count++;
      
        pause();
      
        session.write( wb );
    }
    
    
    
	/////////////////////////////////////
	// xSocket callbacks 
    public boolean onConnect(INonBlockingConnection connection) throws IOException {
    	connection.setIdleTimeoutSec(60);
    	return true;
    }

  
    public boolean onConnectionTimeout(INonBlockingConnection connection) throws IOException {
    	return false;
    }
    
    public boolean onIdleTimeout(INonBlockingConnection connection) throws IOException {
    	return false;
    }
    
    public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
    	ByteBuffer[] data = connection.readAvailable();
    	
    	ByteBuffer[] response = new ByteBuffer[data.length];
    	for (int i = 0; i < data.length; i++) {
	    	 ByteBuffer wb = ByteBuffer.allocate( data[i].remaining() );
	         wb.put( data[i] );
	         wb.flip();
	         response[i] = wb;
    	}

    	connection.write(response);
        count++;
        pause();
    	return true;
    }
    
    
    private void pause() {
    	if (delay > 0) {
    		try {
    			Thread.sleep(delay);
    		} catch (InterruptedException ignore) { }
    	}
    }
}