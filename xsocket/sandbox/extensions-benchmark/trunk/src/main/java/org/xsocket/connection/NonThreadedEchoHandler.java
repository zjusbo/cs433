package org.xsocket.connection;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import org.xsocket.DataConverter;
import org.xsocket.Execution;
import org.xsocket.ILifeCycle;
import org.xsocket.connection.IConnection.FlushMode;


public final class NonThreadedEchoHandler implements IDataHandler, IConnectionTimeoutHandler, IIdleTimeoutHandler, ILifeCycle {

	private static final int PRINT_PERIOD = 5000;
	
	private int count = 0;
	private long time = System.currentTimeMillis(); 

	private Timer timer = new Timer(true);
	
	private int delay = 0;
	private boolean syncFlush = true;
	private FileChannel fc = null;
	
	public NonThreadedEchoHandler(int delay, boolean syncFlush, boolean writeTofile) throws IOException {
		this.delay = delay;
		this.syncFlush = syncFlush;
		
		if (writeTofile) {
			File tempFile = File.createTempFile("test", "test");
			tempFile.deleteOnExit();
			fc = new RandomAccessFile(File.createTempFile("test", "test"), "rw").getChannel();
			System.out.println("write received data into " + tempFile.getAbsolutePath());
		}
		
		TimerTask task = new TimerTask() {
			@Override
			public void run() {
				try {
					String rate = printRate();
					if (fc != null) {
						System.out.println(rate + "(file size=" + fc.position() + ")");
					} else {
						System.out.println(rate);
					}
				} catch (Exception ignore) { }
			}
		};
		timer.schedule(task, PRINT_PERIOD, PRINT_PERIOD);
		
		System.out.println("Non threaded Echo handler initialized. Printing each rate (req/sec) each " + DataConverter.toFormatedDuration(PRINT_PERIOD));
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

	
	
	public void onInit() {
		System.out.println(this.getClass().getSimpleName() + " initialized");
	}
	
	
	public void onDestroy() throws IOException {
	}

	
    
    
    @Execution(Execution.NONTHREADED)
    public boolean onConnectionTimeout(INonBlockingConnection connection) throws IOException {
    	return false;
    }

    @Execution(Execution.NONTHREADED)
    public boolean onIdleTimeout(INonBlockingConnection connection) throws IOException {
    	return false;
    }
    
    @Execution(Execution.NONTHREADED)
    public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
    	ByteBuffer[] data = connection.readByteBufferByLength(connection.available());
    	
    	
    	ByteBuffer[] response = new ByteBuffer[data.length];
    	
    	for (int i = 0; i < data.length; i++) {
			response[i] = ByteBuffer.allocate( data[i].remaining() );
			response[i].put( data[i] );
			response[i].flip();
	    }
	
	    for (ByteBuffer buffer : data) {
			buffer.flip();
		}
	    
		if (fc != null) {
    		fc.write(data);
    		
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