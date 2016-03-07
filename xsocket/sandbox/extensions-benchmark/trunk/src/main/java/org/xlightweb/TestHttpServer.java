package org.xlightweb;




import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;


import org.xlightweb.BodyDataSink;
import org.xlightweb.HttpResponseHeader;
import org.xlightweb.HttpUtils;
import org.xlightweb.IHttpExchange;
import org.xlightweb.IHttpRequest;
import org.xlightweb.IHttpRequestHandler;
import org.xlightweb.server.HttpServer;
import org.xsocket.Execution;
import org.xsocket.connection.IServer;
import org.xsocket.connection.IConnection.FlushMode;

 


public final class TestHttpServer {

public static void main( String[] args ) throws Exception {
		
		if (args.length < 1) {
			System.out.println("usage org.xlightweb.TestHttpServer <port> <extended?> <workers>");
			System.exit(-1);
		}
		
		new TestHttpServer().launch(args);
	}
		
		
	public void launch(String... args) throws Exception {
	
		int port = Integer.parseInt(args[0]);
		boolean extended = Boolean.parseBoolean(args[1]);
	
		
		System.out.println("\r\n---------------------------------------");
		System.out.println(HttpUtils.getImplementationVersion() + "  (" + HttpUtils.getImplementationDate() + ")");
	
		
		IServer server = null; 

		if (args.length > 2) {
			int workers = Integer.parseInt(args[2]);
			server = new HttpServer(port, new HttpHandler(extended));
			server.setWorkerpool(Executors.newFixedThreadPool(workers));

			System.out.println("mode threaded (pool size " + ((ThreadPoolExecutor) server.getWorkerpool()).getMaximumPoolSize() + ")");
			
		} else {
			server = new HttpServer(port, new NonThreadedHttpHandler(extended));
			
			System.out.println("mode                      non threaded");
		}

		server.setFlushMode(FlushMode.ASYNC);
		server.setAutoflush(false);
		
		System.out.println("---------------------------------------\r\n");
		
		
	
		System.out.println("running http server");
		server.run();		
    }
	
	
	
	private static abstract class AbstractHttpHandler implements IHttpRequestHandler {
		
		private static final int OFFSET = 48;
		
		private static Timer timer = new Timer(true);
		private static int count = 0;
		private static long time = System.currentTimeMillis();
	
		private int cachedSize = 100; 
		private byte[] cached = generateByteArray(cachedSize);
		
		private boolean isExtended = false;
		
		private Map<String, String> map = null;
		

		static {
			TimerTask task = new TimerTask() {
				
				public void run() {
					try {
						String rate = printRate();
						System.out.println(rate);
					} catch (Exception ignore) { }
				}
			};
			timer.schedule(task, 5000, 5000);
		}
		
		
		public AbstractHttpHandler(boolean isExtended) {
			this.isExtended = isExtended;
			
			if (isExtended) {
				System.out.println("extended mode (pseudo hashmap)");
				map = new HashMap<String, String>();		
			}
		}
		
		
		public void onRequest(IHttpExchange exchange) throws IOException {

			IHttpRequest request = exchange.getRequest();

			int size = request.getIntParameter("dataLength");
			
			
			if (isExtended) {
				// pseudo hashmap operation
				String value = map.get(size);
				map.put(Integer.toString(size), Integer.toString(size * 2));
			}
			
			if (size > 0) {
				if (size != cachedSize) {
					cachedSize = size;
					cached = generateByteArray(size);
				} 
			}
			
			
	

			try {
				BodyDataSink bodyDataSink = exchange.send(new HttpResponseHeader(200, "text/plain"), size);
				bodyDataSink.setFlushmode(FlushMode.ASYNC);
				bodyDataSink.write(cached);
				bodyDataSink.close();
				
				count++;
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		


		public static ByteBuffer generateByteBuffer(int length) {
			ByteBuffer buffer = ByteBuffer.wrap(generateByteArray(length));
			return buffer;
		}
		
		

		public static byte[] generateByteArray(int length) {
			
			byte[] bytes = new byte[length];
			
			int item = OFFSET;
			
			for (int i = 0; i < length; i++) {
				bytes[i] = (byte) item;
				
				item++;
				if (item > (OFFSET + 9)) {
					item = OFFSET;
				}
			}
			
			return bytes;
		}
		

		private static String printRate() {
			long current = System.currentTimeMillis();
			
			long rate = 0;
			
			if (count == 0) {
				rate = 0;
			} else {
				rate = (count * 1000) / (current - time);
			}

			time = System.currentTimeMillis(); 
			count = 0;
			
			return Long.toString(rate);
		}
	}
	
	


	
	@Execution(Execution.MULTITHREADED)
	private static final class HttpHandler extends AbstractHttpHandler {
		
		public HttpHandler(boolean isExtended) {
			super(isExtended);
		}
		
	}

	@Execution(Execution.NONTHREADED)
	private static final class NonThreadedHttpHandler extends AbstractHttpHandler {
		
		public NonThreadedHttpHandler(boolean isExtended) {
			super(isExtended);
		}
	}
}
